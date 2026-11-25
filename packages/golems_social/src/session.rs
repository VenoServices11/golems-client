use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::time::{Duration, Instant};

use rand::Rng;
use uuid::Uuid;

use golems_auth::MinecraftAccount;
use golems_events::{ChatEvent, EventBus};

use crate::{
    PlusClient, PlusError, REQUEST_TIMEOUT, api, base_url, golems, plus_blocked, rest,
    social_allowed,
};

const MOJANG_JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";

const POLL_INTERVAL: Duration = Duration::from_secs(20);
const PRESENCE_INTERVAL: Duration = Duration::from_secs(120);

const RECONNECT_DELAY: Duration = Duration::from_secs(15);
const MAX_RECONNECT_DELAY: Duration = Duration::from_secs(300);

#[derive(serde::Deserialize)]
struct LoginResponse {
    token: String,
}

enum Outcome {
    Connected,
    NoAccount,
    Failed,
    Restart,
}

enum Ended {
    Closed,
    Restart,
}

pub(crate) fn spawn(client: Arc<PlusClient>) {
    tokio::spawn(async move {
        let socket = match build_socket_client() {
            Ok(socket) => socket,
            Err(err) => {
                tracing::error!("[plus] could not build the websocket client: {err}");
                return;
            }
        };

        let mut delay = RECONNECT_DELAY;

        loop {
            if plus_blocked() {
                tracing::info!("[plus] ending the session: terms and privacy policy declined");
                return;
            }

            let mut restart = client.restart_signal();

            let outcome = session(&client, &socket, &mut restart).await;
            let restarted = matches!(outcome, Outcome::Restart);

            delay = next_delay(&outcome, delay);

            if restarted {
                continue;
            }

            tokio::select! {
                () = tokio::time::sleep(delay) => {}
                _ = restart.changed() => {}
            }
        }
    });
}

pub(crate) fn build_socket_client() -> Result<reqwest::Client, reqwest::Error> {
    let builder = reqwest::Client::builder()
        .tcp_keepalive(Some(Duration::from_secs(15)))
        .connect_timeout(Duration::from_secs(10))
        .http1_only()
        .tls_backend_rustls()
        .user_agent(format!(
            "Golem's Client {} ({})",
            env!("CARGO_PKG_VERSION"),
            env!("CARGO_PKG_HOMEPAGE")
        ));

    builder.no_hickory_dns().build()
}

async fn session(
    client: &PlusClient,
    socket: &reqwest::Client,
    restart: &mut tokio::sync::watch::Receiver<u64>,
) -> Outcome {
    let account = match current_account(&client.auth).await {
        Ok(Some(account)) => account,
        Ok(None) => {
            client.set_session_account(None);
            return Outcome::NoAccount;
        }
        Err(err) => {
            client.set_session_account(None);
            tracing::warn!("[plus] no account to open a session for: {err}");
            return Outcome::Failed;
        }
    };
    client.set_session_account(Some(account.id));

    let id = account.id;
    let mut connected = false;
    let mut restarted = false;

    match run_session(client, socket, &account, &mut connected, restart).await {
        Ok(Ended::Closed) => tracing::info!("[plus] session for {id} ended"),
        Ok(Ended::Restart) => {
            restarted = true;
            tracing::info!("[plus] restarting the session for {id}");
        }
        Err(err) => {
            if err.is_unauthorized() {
                api::forget_token(id).await;
            }
            tracing::warn!("[plus] session for {id} failed: {err}");
        }
    }

    if connected {
        client
            .events
            .chat(ChatEvent::ConnectionChanged { connected: false });
    }

    match (restarted, connected) {
        (true, _) => Outcome::Restart,
        (_, true) => Outcome::Connected,
        _ => Outcome::Failed,
    }
}

/// Golem's Client: no websocket. The friends list is fetched every few seconds from Golem's Client's server, and the
/// changes are passed on as the same events the panel already listens to. Every two minutes (unless "Invisible") the
/// request also says this launcher is open, so friends see "In the launcher".
async fn run_session(
    client: &PlusClient,
    _socket: &reqwest::Client,
    account: &MinecraftAccount,
    connected: &mut bool,
    restart: &mut tokio::sync::watch::Receiver<u64>,
) -> Result<Ended, PlusError> {
    let account_id = account.id;
    let mut previous: Option<golems::State> = None;
    let mut presence_at: Option<Instant> = None;

    loop {
        if plus_blocked() || !social_allowed() || account_changed(&client.auth, account_id).await {
            golems::leave(account).await;
            tracing::info!("[friends] ending the session for {account_id}");
            return Ok(Ended::Closed);
        }

        let send_presence = !rest::invisible()
            && presence_at.is_none_or(|at| at.elapsed() >= PRESENCE_INTERVAL);
        let state = golems::fetch_state(account, send_presence).await?;
        if send_presence {
            presence_at = Some(Instant::now());
        }

        if !*connected {
            *connected = true;
            client
                .events
                .chat(ChatEvent::ConnectionChanged { connected: true });
            tracing::info!("[friends] connected for {account_id}");
        }
        changes(&client.events, account_id, previous.as_ref(), &state);
        previous = Some(state);

        tokio::select! {
            () = tokio::time::sleep(POLL_INTERVAL) => {}
            _ = restart.changed() => return Ok(Ended::Restart),
        }
    }
}

/// What changed since the last fetch, as the panel's events.
fn changes(events: &EventBus, account: Uuid, before: Option<&golems::State>, now: &golems::State) {
    let Some(before) = before else {
        events.chat(ChatEvent::RosterChanged);
        return;
    };
    let mut roster_changed = false;

    let was: HashMap<&str, &golems::FriendRow> = before.friends.iter().map(|f| (f.uuid.as_str(), f)).collect();
    for friend in &now.friends {
        match was.get(friend.uuid.as_str()) {
            None => roster_changed = true,
            Some(old) => {
                if old.status.online() != friend.status.online() {
                    events.chat(ChatEvent::PresenceChanged {
                        player: golems::parse_uuid(&friend.uuid),
                        online: friend.status.online(),
                    });
                }
                if old.status.text() != friend.status.text() || old.name != friend.name {
                    roster_changed = true;
                }
            }
        }
    }
    if before.friends.len() != now.friends.len() {
        roster_changed = true;
    }

    let old_incoming: HashSet<i32> = before.incoming.iter().map(|r| r.id).collect();
    for request in &now.incoming {
        if !old_incoming.contains(&request.id) {
            events.chat(ChatEvent::FriendRequestReceived {
                account,
                sender: golems::parse_uuid(&request.uuid),
            });
            roster_changed = true;
        }
    }
    let ids = |rows: &[golems::RequestRow]| rows.iter().map(|r| r.id).collect::<Vec<_>>();
    let blocked = |state: &golems::State| state.blocked.iter().map(|b| b.uuid.clone()).collect::<Vec<_>>();
    if ids(before.incoming.as_slice()) != ids(now.incoming.as_slice())
        || ids(before.outgoing.as_slice()) != ids(now.outgoing.as_slice())
        || blocked(before) != blocked(now)
    {
        roster_changed = true;
    }

    if roster_changed {
        events.chat(ChatEvent::RosterChanged);
    }
}

pub(crate) async fn current_account(
    auth: &golems_auth::AuthService,
) -> Result<Option<MinecraftAccount>, PlusError> {
    if !social_allowed() {
        return Ok(None);
    }

    let Some(account) = auth.default_account_for_launch().await? else {
        return Ok(None);
    };

    Ok(usable(&account, social_allowed()).then_some(account))
}

fn next_delay(outcome: &Outcome, delay: Duration) -> Duration {
    match outcome {
        Outcome::Restart | Outcome::Connected | Outcome::NoAccount => RECONNECT_DELAY,
        Outcome::Failed => (delay * 2).min(MAX_RECONNECT_DELAY),
    }
}

pub(crate) fn usable(account: &MinecraftAccount, allowed: bool) -> bool {
    allowed
        && account.is_microsoft()
        && !account.needs_sign_in()
        && !account.access_token.is_empty()
}

async fn account_changed(auth: &golems_auth::AuthService, id: Uuid) -> bool {
    match auth.default_account().await {
        Ok(Some(account)) => account.id != id || account.needs_sign_in(),
        Ok(None) => true,
        Err(_) => false,
    }
}

pub(crate) async fn login(
    client: &reqwest::Client,
    account: &MinecraftAccount,
) -> Result<String, PlusError> {
    if plus_blocked() {
        return Err(PlusError::ConsentRequired);
    }

    let server_id = generate_server_id();

    let join = client
        .post(MOJANG_JOIN_URL)
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .json(&serde_json::json!({
            "accessToken": account.access_token,
            "selectedProfile": account.id.simple().to_string(),
            "serverId": server_id,
        }))
        .send()
        .await?;

    if !join.status().is_success() {
        return Err(PlusError::JoinRejected {
            status: join.status().as_u16(),
        });
    }

    let mut url = reqwest::Url::parse(&format!("{}/account/login", base_url()))?;
    url.query_pairs_mut()
        .append_pair("username", &account.username)
        .append_pair("server_id", &server_id);

    let login = client
        .post(url)
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .send()
        .await?;

    if !login.status().is_success() {
        return Err(PlusError::LoginRejected {
            username: account.username.clone(),
            status: login.status().as_u16(),
        });
    }

    Ok(login.json::<LoginResponse>().await?.token)
}

fn generate_server_id() -> String {
    let mut bytes = [0u8; 20];
    rand::rng().fill_bytes(&mut bytes);
    golems_io::to_hex(&bytes)
}
