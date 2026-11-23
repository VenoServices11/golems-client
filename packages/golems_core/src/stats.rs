//! User stats for the owner: how many people use Golem's Client, and who is online right now.
//!
//! While the launcher is open with a Microsoft account signed in, it checks in with Golem's Client's
//! server every minute. A check-in has the account's Minecraft UUID and username, the launcher
//! version, and whether a game is running (with the instance name and Minecraft version). Nothing
//! else is sent: no login tokens, email or files. Setup and the README tell people about it.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use golems_auth::AccountKind;
use golems_common::constants::PRESENCE_URL;
use serde::{Deserialize, Serialize};

use crate::LauncherState;

const EVERY: Duration = Duration::from_secs(60);
const TIMEOUT: Duration = Duration::from_secs(15);

#[derive(Serialize)]
struct CheckIn {
    uuid: String,
    name: String,
    version: &'static str,
    /// "launcher" or "playing"
    state: &'static str,
    instance: Option<String>,
    mc: Option<String>,
}

/// Checks in every minute until the launcher closes
pub fn start(state: Arc<LauncherState>) {
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(EVERY);
        loop {
            ticker.tick().await;
            if let Err(err) = check_in(&state).await {
                tracing::debug!("stats check-in skipped: {err}");
            }
        }
    });
}

async fn signed_in(state: &LauncherState) -> Option<(String, String)> {
    let account = state
        .auth
        .default_account()
        .await
        .ok()
        .flatten()
        .filter(|account| account.kind == AccountKind::Microsoft && !account.signed_out)?;
    Some((account.id.simple().to_string(), account.username))
}

async fn check_in(state: &LauncherState) -> Result<(), String> {
    let Some((uuid, name)) = signed_in(state).await else {
        return Ok(());
    };

    let mut playing = None;
    for cluster_id in state.games.running_ids() {
        if let Ok(cluster) = state.clusters.get(cluster_id).await {
            playing = Some(cluster);
            break;
        }
    }

    let body = CheckIn {
        uuid,
        name,
        version: env!("CARGO_PKG_VERSION"),
        state: if playing.is_some() { "playing" } else { "launcher" },
        instance: playing.as_ref().map(|cluster| cluster.name.clone()),
        mc: playing.map(|cluster| cluster.mc_version),
    };

    state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/launcher/checkin"))
        .json(&body)
        .timeout(TIMEOUT)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|err| err.to_string())?;
    Ok(())
}

/// Tells the server this launcher closed, so it stops counting as online straight away
pub async fn leave(state: &LauncherState) {
    let Some((uuid, _)) = signed_in(state).await else {
        return;
    };
    let _ = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/launcher/leave"))
        .json(&serde_json::json!({ "uuid": uuid }))
        .timeout(Duration::from_secs(3))
        .send()
        .await;
}

// ------------------------------------------------------------------ the owner's view

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct OwnerStats {
    pub total: u64,
    pub in_launcher: u64,
    pub playing: u64,
    pub new_today: u64,
    pub active_today: u64,
    pub users: Vec<StatsUser>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct StatsUser {
    pub uuid: String,
    pub name: Option<String>,
    pub version: Option<String>,
    pub first_seen: i64,
    pub last_seen: i64,
    /// "launcher", "playing", or none when offline
    pub state: Option<String>,
    pub instance: Option<String>,
    pub mc: Option<String>,
}

/// The owner's stats; needs the owner key (the server's `ADMIN_KEY`)
pub async fn owner_stats(state: &LauncherState, key: &str) -> Result<OwnerStats, String> {
    let response = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/launcher/stats"))
        .json(&serde_json::json!({ "key": key }))
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    match response.status().as_u16() {
        200 => response
            .json::<OwnerStats>()
            .await
            .map_err(|_| "The server sent something unexpected.".to_string()),
        403 => Err("Wrong owner key.".to_string()),
        404 => Err("The server needs updating: run npx wrangler deploy.".to_string()),
        code => Err(format!("The server answered with an error ({code}).")),
    }
}

fn owner_key_file() -> Option<PathBuf> {
    golems_common::paths::config_dir()
        .ok()
        .map(|dir| dir.join("owner.key"))
}

/// The owner key saved on this PC, if any
#[must_use]
pub fn saved_owner_key() -> Option<String> {
    let key = std::fs::read_to_string(owner_key_file()?).ok()?;
    let key = key.trim();
    (!key.is_empty()).then(|| key.to_string())
}

pub fn save_owner_key(key: &str) -> std::io::Result<()> {
    let path = owner_key_file().ok_or_else(|| std::io::Error::other("no settings folder"))?;
    std::fs::write(path, key.trim())
}
