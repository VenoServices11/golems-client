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

/// Posts the banner shown on Golem's Client's in-game main menu; an empty text takes it down.
pub async fn post_announcement(state: &LauncherState, key: &str, text: &str) -> Result<(), String> {
    let response = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/launcher/announce"))
        .json(&serde_json::json!({ "key": key, "text": text }))
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    match response.status().as_u16() {
        200 => Ok(()),
        403 => Err("Wrong owner key.".to_string()),
        404 => Err("The server needs updating.".to_string()),
        code => Err(format!("The server answered with an error ({code}).")),
    }
}

/// A post on the News page of Golem's Client's in-game main menu
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct NewsPost {
    pub id: String,
    pub title: String,
    pub body: String,
    #[serde(default)]
    pub link: String,
    pub at: u64,
}

#[derive(Deserialize)]
struct NewsList {
    #[serde(default)]
    posts: Vec<NewsPost>,
}

/// The News page's posts, newest first
pub async fn news_posts(state: &LauncherState) -> Result<Vec<NewsPost>, String> {
    let response = state
        .services
        .requester
        .http()
        .get(format!("{PRESENCE_URL}/launcher/news"))
        .header("cache-control", "no-cache")
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    match response.status().as_u16() {
        200 => response
            .json::<NewsList>()
            .await
            .map(|list| list.posts)
            .map_err(|_| "The server sent something unexpected.".to_string()),
        404 => Err("The server needs updating.".to_string()),
        code => Err(format!("The server answered with an error ({code}).")),
    }
}

/// Writes a News post (the link is optional and must start with https://)
pub async fn post_news(state: &LauncherState, key: &str, title: &str, body: &str, link: &str) -> Result<(), String> {
    news_call(state, "/launcher/news/post", serde_json::json!({ "key": key, "title": title, "body": body, "link": link })).await
}

/// Takes a News post down
pub async fn delete_news(state: &LauncherState, key: &str, id: &str) -> Result<(), String> {
    news_call(state, "/launcher/news/delete", serde_json::json!({ "key": key, "id": id })).await
}

/// A cape the owner can give out, and who has it
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct GiftCape {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub people: Vec<String>,
}

#[derive(Deserialize)]
struct GiftCapes {
    #[serde(default)]
    capes: Vec<GiftCape>,
}

/// Gives a cape to a player (by name), or takes it back. Answers what happened
pub async fn give_cape(state: &LauncherState, key: &str, player: &str, cape: &str, take: bool) -> Result<String, String> {
    let response = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/admin/capes/give"))
        .json(&serde_json::json!({ "key": key, "player": player, "cape": cape, "take": take }))
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    let status = response.status().as_u16();
    let body = response.json::<serde_json::Value>().await.unwrap_or_default();
    let who = body["player"].as_str().unwrap_or(player).to_string();
    match status {
        200 if take => Ok(format!("Took the cape back from {who}.")),
        200 => Ok(format!("Gave the cape to {who}. They can wear it from their Wardrobe.")),
        404 if body["code"] == "not_found" => Err("Wrong owner key, or the server needs updating.".to_string()),
        _ => Err(body["error"].as_str().map(str::to_string).unwrap_or_else(|| format!("The server answered with an error ({status})."))),
    }
}

/// The capes the owner can give out, with who has them
pub async fn gift_capes(state: &LauncherState, key: &str) -> Result<Vec<GiftCape>, String> {
    let response = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}/admin/capes/list"))
        .json(&serde_json::json!({ "key": key }))
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    match response.status().as_u16() {
        200 => response
            .json::<GiftCapes>()
            .await
            .map(|list| list.capes)
            .map_err(|_| "The server sent something unexpected.".to_string()),
        404 => Err("Wrong owner key, or the server needs updating.".to_string()),
        code => Err(format!("The server answered with an error ({code}).")),
    }
}

async fn news_call(state: &LauncherState, path: &str, body: serde_json::Value) -> Result<(), String> {
    let response = state
        .services
        .requester
        .http()
        .post(format!("{PRESENCE_URL}{path}"))
        .json(&body)
        .timeout(TIMEOUT)
        .send()
        .await
        .map_err(|_| "Couldn't reach the server.".to_string())?;
    match response.status().as_u16() {
        200 => Ok(()),
        400 => Err("A post needs a title and some text.".to_string()),
        403 => Err("Wrong owner key.".to_string()),
        404 => Err("The server needs updating.".to_string()),
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
