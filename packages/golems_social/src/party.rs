use std::sync::LazyLock;

use chrono::{DateTime, Utc};
use serde::Deserialize;
use tokio::sync::broadcast;
use uuid::Uuid;

use golems_auth::MinecraftAccount;

use crate::{PlusError, golems};
use serde_json::json;

pub const MAX_PARTY_SIZE: usize = 8;

static EVENTS: LazyLock<broadcast::Sender<LiveEvent>> =
    LazyLock::new(|| broadcast::channel(64).0);

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Party {
    pub id: Uuid,
    pub leader: Uuid,
    pub settings: PartySettings,
    #[serde(default)]
    pub chat_group_id: Option<i32>,
    pub members: Vec<PartyMember>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct PartySettings {
    pub invite_policy: InvitePolicy,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum InvitePolicy {
    Leader,
    Members,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct PartyMember {
    pub player: Uuid,
    pub joined_at: DateTime<Utc>,
    pub online: bool,
    #[serde(default)]
    pub state: Option<MemberState>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct MemberState {
    pub location: MemberLocation,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct MemberLocation {
    pub kind: LocationKind,
    #[serde(default)]
    pub display: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum LocationKind {
    Menu,
    Singleplayer,
    Server,
    HostedWorld,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct PartyInvite {
    pub id: Uuid,
    pub party_id: Uuid,
    pub sender: Uuid,
    pub recipient: Uuid,
    pub status: InviteStatus,
    pub created_at: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub party_size: usize,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum InviteStatus {
    Pending,
    Accepted,
    Declined,
    Cancelled,
    Expired,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum LeftReason {
    Left,
    Kicked,
    Disbanded,
    Expired,
    #[serde(other)]
    Other,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(tag = "type", rename_all = "PascalCase")]
pub enum PartyPacket {
    PartyUpdated {
        party: Party,
    },
    PartyMemberStateChanged {
        party_id: Uuid,
        player: Uuid,
        state: MemberState,
    },
    PartyLeft {
        party_id: Uuid,
        reason: LeftReason,
    },
    PartyInviteReceived {
        invite: PartyInvite,
    },
    PartyInviteUpdated {
        invite_id: Uuid,
        status: InviteStatus,
    },
    FriendRemoved {
        player: Uuid,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub enum LiveEvent {
    Connected,
    Packet(Box<PartyPacket>),
}

pub fn subscribe() -> broadcast::Receiver<LiveEvent> {
    EVENTS.subscribe()
}

pub(crate) fn connected() {
    let _ = EVENTS.send(LiveEvent::Connected);
}

pub(crate) fn dispatch(text: &str) {
    if let Ok(packet) = serde_json::from_str::<PartyPacket>(text) {
        let _ = EVENTS.send(LiveEvent::Packet(Box::new(packet)));
    }
}

pub fn error_message(err: &PlusError) -> Option<String> {
    #[derive(Deserialize)]
    struct Body {
        message: String,
    }

    match err {
        PlusError::Server { status, message } if *status < 500 => Some(message.clone()),
        PlusError::Api { status, body } if *status < 500 => serde_json::from_str::<Body>(body)
            .ok()
            .map(|body| body.message)
            .filter(|message| !message.trim().is_empty()),
        _ => None,
    }
}

/// Golem's Client: parties are on Golem's Client's own server (see `server/src/parties.js`).
#[derive(Deserialize)]
struct PartyState {
    #[serde(default)]
    party: Option<Party>,
    #[serde(default)]
    incoming: Vec<PartyInvite>,
    #[serde(default)]
    outgoing: Vec<PartyInvite>,
}

async fn state(account: &MinecraftAccount) -> Result<PartyState, PlusError> {
    golems::call_as(account, "/party/state", json!({})).await
}

pub async fn current(account: &MinecraftAccount) -> Result<Option<Party>, PlusError> {
    Ok(state(account).await?.party)
}

pub async fn create(account: &MinecraftAccount) -> Result<Party, PlusError> {
    golems::call_as(account, "/party/create", json!({})).await
}

pub async fn leave(account: &MinecraftAccount) -> Result<(), PlusError> {
    golems::call(account, "/party/leave", json!({})).await.map(drop)
}

pub async fn leave_for_sign_out(account: &MinecraftAccount) -> Result<(), PlusError> {
    if !crate::session::usable(account, crate::social_allowed()) {
        return Ok(());
    }

    if current(account).await?.is_some() {
        leave(account).await?;
    }
    Ok(())
}

pub async fn disband(account: &MinecraftAccount) -> Result<(), PlusError> {
    golems::call(account, "/party/disband", json!({})).await.map(drop)
}

pub async fn transfer_leader(account: &MinecraftAccount, player: Uuid) -> Result<Party, PlusError> {
    golems::call_as(account, "/party/leader", json!({ "uuid": player.simple().to_string() })).await
}

pub async fn kick(account: &MinecraftAccount, player: Uuid) -> Result<(), PlusError> {
    golems::call(account, "/party/kick", json!({ "uuid": player.simple().to_string() }))
        .await
        .map(drop)
}

pub async fn invite(account: &MinecraftAccount, player: Uuid) -> Result<PartyInvite, PlusError> {
    golems::call_as(account, "/party/invite", json!({ "uuid": player.simple().to_string() })).await
}

pub async fn incoming(account: &MinecraftAccount) -> Result<Vec<PartyInvite>, PlusError> {
    Ok(state(account).await?.incoming)
}

pub async fn outgoing(account: &MinecraftAccount) -> Result<Vec<PartyInvite>, PlusError> {
    Ok(state(account).await?.outgoing)
}

pub async fn accept(
    account: &MinecraftAccount,
    invite: Uuid,
    leave_current: bool,
) -> Result<Party, PlusError> {
    golems::call_as(
        account,
        "/party/accept",
        json!({ "id": invite.to_string(), "leave_current": leave_current }),
    )
    .await
}

pub async fn decline(account: &MinecraftAccount, invite: Uuid) -> Result<(), PlusError> {
    golems::call(account, "/party/decline", json!({ "id": invite.to_string() }))
        .await
        .map(drop)
}

pub async fn cancel_invite(account: &MinecraftAccount, invite: Uuid) -> Result<(), PlusError> {
    golems::call(account, "/party/cancel", json!({ "id": invite.to_string() }))
        .await
        .map(drop)
}

/// What changed in the party since the last fetch of the friends list, as the Party tab's events.
pub(crate) fn changes(before: Option<&serde_json::Value>, now: Option<&serde_json::Value>) {
    let Some(now) = now else { return };
    if before == Some(now) {
        return;
    }
    let old_invites: Vec<&str> = before
        .and_then(|b| b["incoming"].as_array())
        .map(|list| list.iter().filter_map(|i| i["id"].as_str()).collect())
        .unwrap_or_default();
    if before.is_some()
        && let Some(list) = now["incoming"].as_array()
    {
        for invite in list {
            if invite["id"].as_str().is_some_and(|id| !old_invites.contains(&id))
                && let Ok(invite) = serde_json::from_value::<PartyInvite>(invite.clone())
            {
                let _ = EVENTS.send(LiveEvent::Packet(Box::new(PartyPacket::PartyInviteReceived { invite })));
            }
        }
    }
    // the Party tab reads it all again
    connected();
}
