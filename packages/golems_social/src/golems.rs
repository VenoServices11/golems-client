//! Golem's Client friends, from Golem's Client's own server (`server/src/friends.js`).
//!
//! The friends panel used to talk to Polyfrost's servers; it now talks to Golem's Client's server instead. Mojang
//! won't answer that server, so accounts aren't checked with Mojang: the first time an account signs in to friends,
//! this computer gets a secret key (kept in `~/.golemsclient/friends`, shared with the game), and later sign-ins send
//! it. Nothing from the Microsoft or Minecraft login is sent.
//!
//! Copyright (C) 2026 veno15 - GNU General Public License v3.0.

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{LazyLock, Mutex, RwLock};
use std::time::{Duration, Instant};

use chrono::{DateTime, FixedOffset, TimeZone, Utc};
use serde::Deserialize;
use serde_json::{Value, json};
use uuid::Uuid;

use golems_auth::MinecraftAccount;

use crate::models::{BlockedPlayer, Friend, FriendRequest, RelationshipKind, ResolvedPlayer};
use crate::PlusError;

pub const SERVER: &str = "https://c418-presence.c418client.workers.dev";

/// How long a fetched friends list is reused (the panel asks for friends, requests and blocks at once).
const FRESH_FOR: Duration = Duration::from_secs(3);

#[derive(Deserialize, Clone, Debug, Default)]
pub struct State {
    #[serde(default)]
    pub me: Me,
    #[serde(default)]
    pub friends: Vec<FriendRow>,
    #[serde(default)]
    pub incoming: Vec<RequestRow>,
    #[serde(default)]
    pub outgoing: Vec<RequestRow>,
    #[serde(default)]
    pub blocked: Vec<BlockedRow>,
    #[serde(default)]
    pub inbox: Inbox,
    /// Messages newer than the `since` that was sent.
    #[serde(default)]
    pub messages: Vec<NewMessage>,
}

#[derive(Deserialize, Clone, Debug, Default)]
pub struct Inbox {
    #[serde(default)]
    pub latest: i64,
    #[serde(default)]
    pub unread: i64,
}

#[derive(Deserialize, Clone, Debug)]
pub struct NewMessage {
    pub id: i64,
    pub group_id: i32,
    pub sender: Uuid,
    pub content: String,
}

#[derive(Deserialize, Clone, Debug, Default)]
pub struct Me {
    #[serde(default, rename = "shareServer")]
    pub share_server: bool,
}

#[derive(Deserialize, Clone, Debug)]
pub struct FriendRow {
    pub uuid: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub since: i64,
    #[serde(default)]
    pub owner: bool,
    #[serde(default)]
    pub status: Status,
}

#[derive(Deserialize, Clone, Debug, Default)]
pub struct Status {
    #[serde(default)]
    pub state: String,
    #[serde(default)]
    pub server: Option<String>,
    #[serde(default)]
    pub mc: Option<String>,
    #[serde(default, rename = "lastSeen")]
    pub last_seen: Option<i64>,
}

impl Status {
    #[must_use]
    pub fn online(&self) -> bool {
        self.state == "game" || self.state == "launcher"
    }

    /// "Playing on donutsmp.net", "In game", "In the launcher" or "Offline".
    #[must_use]
    pub fn text(&self) -> String {
        match self.state.as_str() {
            "game" => {
                let place = match self.server.as_deref() {
                    None => "In game".to_string(),
                    Some("Singleplayer") => "Playing singleplayer".to_string(),
                    Some("a private server") => "On a private server".to_string(),
                    Some(server) => format!("Playing on {server}"),
                };
                match self.mc.as_deref() {
                    Some(mc) => format!("{place} · {mc}"),
                    None => place,
                }
            }
            "launcher" => "In the launcher".to_string(),
            _ => "Offline".to_string(),
        }
    }
}

#[derive(Deserialize, Clone, Debug)]
pub struct RequestRow {
    pub id: i32,
    pub uuid: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub at: i64,
}

#[derive(Deserialize, Clone, Debug)]
pub struct BlockedRow {
    pub uuid: String,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub since: i64,
}

static TOKEN: Mutex<Option<(Uuid, String)>> = Mutex::new(None);
static LAST: RwLock<Option<(Uuid, Instant, State)>> = RwLock::new(None);
static FETCH: LazyLock<tokio::sync::Mutex<()>> = LazyLock::new(|| tokio::sync::Mutex::new(()));
/// Names we know (friends, requests, and names looked up with Mojang).
static NAMES: LazyLock<RwLock<HashMap<Uuid, String>>> = LazyLock::new(|| RwLock::new(HashMap::new()));
/// What each friend is doing ("Playing on donutsmp.net"), for the friends list.
static ACTIVITY: LazyLock<RwLock<HashMap<Uuid, String>>> = LazyLock::new(|| RwLock::new(HashMap::new()));
static HTTP: LazyLock<Option<reqwest::Client>> = LazyLock::new(|| crate::session::build_socket_client().ok());

fn http() -> Result<&'static reqwest::Client, PlusError> {
    HTTP.as_ref().ok_or_else(|| PlusError::Client("no http client".into()))
}

/// What a friend is doing right now, if they're online.
#[must_use]
pub fn activity(player: Uuid) -> Option<String> {
    ACTIVITY.read().ok().and_then(|map| map.get(&player).cloned())
}

/// A name we know for this player.
#[must_use]
pub fn known_name(player: Uuid) -> Option<String> {
    NAMES.read().ok().and_then(|map| map.get(&player).cloned())
}

fn remember_name(player: Uuid, name: &str) {
    if let Ok(mut map) = NAMES.write() {
        map.insert(player, name.to_string());
    }
}

#[must_use]
pub fn parse_uuid(s: &str) -> Uuid {
    Uuid::parse_str(s).unwrap_or(Uuid::nil())
}

fn time(seconds: i64) -> DateTime<FixedOffset> {
    Utc.timestamp_opt(seconds, 0)
        .single()
        .unwrap_or_else(Utc::now)
        .fixed_offset()
}

// ------------------------------------------------------------------ the key

fn home() -> Option<PathBuf> {
    std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
}

fn key_path(player: Uuid) -> Option<PathBuf> {
    home().map(|home| {
        home.join(".golemsclient")
            .join("friends")
            .join(format!("{}.key", player.simple()))
    })
}

fn read_key(player: Uuid) -> Option<String> {
    let key = std::fs::read_to_string(key_path(player)?).ok()?;
    let key = key.trim().to_string();
    (key.len() == 64 && key.chars().all(|c| c.is_ascii_hexdigit())).then_some(key)
}

fn write_key(player: Uuid, key: &str) {
    let Some(path) = key_path(player) else { return };
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    if let Err(err) = std::fs::write(&path, key) {
        tracing::warn!("[friends] could not save the friends key: {err}");
    }
}

// ------------------------------------------------------------------ requests

fn server_error(status: u16, body: &Value) -> PlusError {
    let message = body
        .get("error")
        .and_then(Value::as_str)
        .map(str::to_string)
        .unwrap_or_else(|| format!("Golem's Client's server answered {status}."));
    PlusError::Server { status, message }
}

async fn post(path: &str, body: &Value) -> Result<(u16, Value), PlusError> {
    let response = http()?
        .post(format!("{SERVER}{path}"))
        .timeout(Duration::from_secs(15))
        .json(body)
        .send()
        .await?;
    let status = response.status().as_u16();
    let value = response.json::<Value>().await.unwrap_or(Value::Null);
    Ok((status, value))
}

async fn login(account: &MinecraftAccount) -> Result<String, PlusError> {
    let mut body = json!({ "uuid": account.id.simple().to_string(), "name": account.username });
    if let Some(key) = read_key(account.id) {
        body["key"] = Value::String(key);
    }
    let (status, value) = post("/friends/login", &body).await?;
    if status != 200 {
        return Err(server_error(status, &value));
    }
    if let Some(key) = value.get("key").and_then(Value::as_str) {
        write_key(account.id, key);
    }
    let token = value
        .get("token")
        .and_then(Value::as_str)
        .ok_or_else(|| server_error(status, &value))?
        .to_string();
    if let Ok(mut current) = TOKEN.lock() {
        *current = Some((account.id, token.clone()));
    }
    tracing::info!("[friends] signed in as {}", account.username);
    Ok(token)
}

fn cached_token(account: &MinecraftAccount) -> Option<String> {
    TOKEN
        .lock()
        .ok()
        .and_then(|current| current.clone())
        .filter(|(id, _)| *id == account.id)
        .map(|(_, token)| token)
}

/// A request with the session token, signing in first (or again) when needed.
pub async fn call(account: &MinecraftAccount, path: &str, mut body: Value) -> Result<Value, PlusError> {
    let token = match cached_token(account) {
        Some(token) => token,
        None => login(account).await?,
    };
    body["token"] = Value::String(token);
    let (mut status, mut value) = post(path, &body).await?;
    if status == 401 {
        body["token"] = Value::String(login(account).await?);
        (status, value) = post(path, &body).await?;
    }
    if status != 200 {
        return Err(server_error(status, &value));
    }
    forget_state();
    Ok(value)
}

fn forget_state() {
    if let Ok(mut last) = LAST.write() {
        *last = None;
    }
}

/// The friends list, sending what this launcher is doing when `presence` is set, and the messages newer than
/// `since` (a message id).
pub async fn fetch_state(
    account: &MinecraftAccount,
    presence: bool,
    since: Option<i64>,
) -> Result<State, PlusError> {
    let mut body = json!({});
    if presence {
        body["presence"] = json!({ "source": "launcher" });
    }
    if let Some(since) = since {
        body["since"] = json!(since);
    }
    let value = call(account, "/friends/state", body).await?;
    let state: State = serde_json::from_value(value)
        .map_err(|err| PlusError::Client(format!("could not read the friends list: {err}")))?;
    remember(account.id, &state);
    Ok(state)
}

/// The friends list, reused for a few seconds.
pub async fn state(account: &MinecraftAccount) -> Result<State, PlusError> {
    let _one_at_a_time = FETCH.lock().await;
    if let Ok(last) = LAST.read()
        && let Some((id, at, state)) = last.as_ref()
        && *id == account.id
        && at.elapsed() < FRESH_FOR
    {
        return Ok(state.clone());
    }
    fetch_state(account, false, None).await
}

fn remember(account: Uuid, state: &State) {
    if let Ok(mut names) = NAMES.write() {
        for f in &state.friends {
            if let Some(name) = &f.name {
                names.insert(parse_uuid(&f.uuid), name.clone());
            }
        }
        for r in state.incoming.iter().chain(&state.outgoing) {
            if let Some(name) = &r.name {
                names.insert(parse_uuid(&r.uuid), name.clone());
            }
        }
        for b in &state.blocked {
            if let Some(name) = &b.name {
                names.insert(parse_uuid(&b.uuid), name.clone());
            }
        }
    }
    if let Ok(mut activity) = ACTIVITY.write() {
        activity.clear();
        for f in &state.friends {
            if f.status.online() {
                activity.insert(parse_uuid(&f.uuid), f.status.text());
            }
        }
    }
    if let Ok(mut last) = LAST.write() {
        *last = Some((account, Instant::now(), state.clone()));
    }
}

/// This launcher closed or went invisible: friends see it as offline straight away.
pub async fn leave(account: &MinecraftAccount) {
    if cached_token(account).is_none() {
        return;
    }
    let _ = call(account, "/friends/leave", json!({ "source": "launcher" })).await;
}

// ------------------------------------------------------------------ the shapes the friends panel uses

#[must_use]
pub fn friends_of(state: &State) -> Vec<Friend> {
    state
        .friends
        .iter()
        .map(|f| Friend {
            player: parse_uuid(&f.uuid),
            kind: RelationshipKind::Friend,
            since: time(f.since),
            online: f.status.online(),
        })
        .collect()
}

#[must_use]
pub fn requests_of(rows: &[RequestRow]) -> Vec<FriendRequest> {
    rows.iter()
        .map(|r| FriendRequest {
            id: r.id,
            player: parse_uuid(&r.uuid),
            created_at: time(r.at),
        })
        .collect()
}

#[must_use]
pub fn blocked_of(state: &State) -> Vec<BlockedPlayer> {
    state
        .blocked
        .iter()
        .map(|b| BlockedPlayer {
            player: parse_uuid(&b.uuid),
            since: time(b.since),
        })
        .collect()
}

#[must_use]
pub fn resolved(ids: &[Uuid]) -> Vec<ResolvedPlayer> {
    ids.iter()
        .filter_map(|id| {
            known_name(*id).map(|username| ResolvedPlayer {
                id: *id,
                username,
            })
        })
        .collect()
}

/// A Minecraft account's UUID from Mojang (asked from this computer), `None` if there's no such account.
pub async fn mojang_uuid(username: &str) -> Result<Option<Uuid>, PlusError> {
    #[derive(Deserialize)]
    struct Profile {
        id: String,
        name: String,
    }
    let response = http()?
        .get(format!(
            "https://api.mojang.com/users/profiles/minecraft/{}",
            url::form_urlencoded::byte_serialize(username.as_bytes()).collect::<String>()
        ))
        .timeout(Duration::from_secs(10))
        .send()
        .await?;
    match response.status().as_u16() {
        200 => {
            let profile = response.json::<Profile>().await?;
            let id = parse_uuid(&profile.id);
            remember_name(id, &profile.name);
            Ok(Some(id))
        }
        204 | 404 => Ok(None),
        status => Err(PlusError::Server {
            status,
            message: "Couldn't check that name with Mojang. Try again in a bit.".into(),
        }),
    }
}

/// Sends a friend request to a player found with [`mojang_uuid`] (or accepts theirs).
pub async fn add(account: &MinecraftAccount, player: Uuid) -> Result<(), PlusError> {
    let Some(name) = known_name(player) else {
        return Err(PlusError::Server {
            status: 400,
            message: "Type their Minecraft name to add them.".into(),
        });
    };
    call(
        account,
        "/friends/add",
        json!({ "name": name, "uuid": player.simple().to_string() }),
    )
    .await
    .map(drop)
}

// ------------------------------------------------------------------ messages

/// Members' names that come with chats (`names`: { uuid: name }), for showing names in the chat.
fn learn_names(value: &Value) {
    let mut learn = |names: &Value| {
        if let Some(names) = names.as_object() {
            for (id, name) in names {
                if let Some(name) = name.as_str() {
                    remember_name(parse_uuid(id), name);
                }
            }
        }
    };
    learn(&value["names"]);
    if let Some(groups) = value["groups"].as_array() {
        for group in groups {
            learn(&group["names"]);
        }
    }
}

/// A request whose answer is read as `T`.
pub async fn call_as<T: serde::de::DeserializeOwned>(
    account: &MinecraftAccount,
    path: &str,
    body: Value,
) -> Result<T, PlusError> {
    let value = call(account, path, body).await?;
    learn_names(&value);
    serde_json::from_value(value)
        .map_err(|err| PlusError::Client(format!("could not read the answer to {path}: {err}")))
}

#[derive(Deserialize)]
pub struct Groups<T> {
    #[serde(default = "Vec::new")]
    pub groups: Vec<T>,
}

#[derive(Deserialize)]
pub struct Messages<T> {
    #[serde(default = "Vec::new")]
    pub messages: Vec<T>,
}
