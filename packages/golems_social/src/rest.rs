//! The friends panel's requests, answered by Golem's Client's own server (see `golems.rs`).
//! Messages and groups aren't on that server yet: their lists are empty and sending says so.

use std::sync::atomic::{AtomicBool, Ordering};

use serde_json::json;
use uuid::Uuid;

use golems_auth::MinecraftAccount;

use crate::models::{
    BlockedPlayer, CursorPage, Friend, FriendRequest, GroupMessage, GroupSummary,
    IncomingSessionInvite, PlayerStatus, ResolvedPlayer,
};
use crate::{PlusClient, PlusError, golems, session};

/// "Invisible" in the panel: this launcher stops telling friends it's open.
static INVISIBLE: AtomicBool = AtomicBool::new(false);

#[must_use]
pub fn invisible() -> bool {
    INVISIBLE.load(Ordering::SeqCst)
}

impl PlusClient {
    /// One page of conversations: none yet (messages come in a later update).
    pub async fn groups_page(
        &self,
        _cursor: Option<&str>,
        _limit: u64,
    ) -> Result<CursorPage<GroupSummary>, PlusError> {
        Ok(CursorPage {
            items: Vec::new(),
            cursor: None,
        })
    }

    pub async fn open_direct_message(&self, _player: Uuid) -> Result<GroupSummary, PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn create_group(
        &self,
        _name: &str,
        _members: &[Uuid],
    ) -> Result<GroupSummary, PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn rename_group(&self, _group_id: i32, _name: &str) -> Result<(), PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn add_member(&self, _group_id: i32, _player: Uuid) -> Result<(), PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn remove_member(&self, _group_id: i32, _player: Uuid) -> Result<(), PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn messages(
        &self,
        _group_id: i32,
        _before: Option<i64>,
        _limit: Option<u64>,
    ) -> Result<Vec<GroupMessage>, PlusError> {
        Ok(Vec::new())
    }

    pub async fn send_message(
        &self,
        _group_id: i32,
        _content: &str,
        _idempotency_key: Uuid,
    ) -> Result<GroupMessage, PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn edit_message(
        &self,
        _group_id: i32,
        _message_id: i64,
        _content: &str,
    ) -> Result<GroupMessage, PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn delete_message(&self, _group_id: i32, _message_id: i64) -> Result<(), PlusError> {
        Err(golems::messages_not_yet())
    }

    pub async fn mark_read(&self, _group_id: i32, _message_id: i64) -> Result<(), PlusError> {
        Ok(())
    }

    pub async fn friends(&self) -> Result<Vec<Friend>, PlusError> {
        let account = self.account().await?;
        Ok(golems::friends_of(&golems::state(&account).await?))
    }

    pub async fn remove_friend(&self, player: Uuid) -> Result<(), PlusError> {
        let account = self.account().await?;
        golems::call(&account, "/friends/remove", json!({ "uuid": player.simple().to_string() }))
            .await
            .map(drop)
    }

    pub async fn incoming_requests(&self) -> Result<Vec<FriendRequest>, PlusError> {
        let account = self.account().await?;
        Ok(golems::requests_of(&golems::state(&account).await?.incoming))
    }

    pub async fn outgoing_requests(&self) -> Result<Vec<FriendRequest>, PlusError> {
        let account = self.account().await?;
        Ok(golems::requests_of(&golems::state(&account).await?.outgoing))
    }

    pub async fn send_friend_request(
        &self,
        player: Uuid,
    ) -> Result<Option<FriendRequest>, PlusError> {
        let account = self.account().await?;
        golems::add(&account, player).await?;
        Ok(None)
    }

    pub async fn accept_request(&self, request_id: i32) -> Result<(), PlusError> {
        self.by_id("/friends/accept", request_id).await
    }

    pub async fn decline_request(&self, request_id: i32) -> Result<(), PlusError> {
        self.by_id("/friends/decline", request_id).await
    }

    pub async fn incoming_session_invites(&self) -> Result<Vec<IncomingSessionInvite>, PlusError> {
        Ok(Vec::new())
    }

    pub async fn decline_session_invite(&self, _invite_id: i32) -> Result<(), PlusError> {
        Ok(())
    }

    pub async fn cancel_request(&self, request_id: i32) -> Result<(), PlusError> {
        self.by_id("/friends/cancel", request_id).await
    }

    pub async fn blocked(&self) -> Result<Vec<BlockedPlayer>, PlusError> {
        let account = self.account().await?;
        Ok(golems::blocked_of(&golems::state(&account).await?))
    }

    pub async fn block(&self, player: Uuid) -> Result<(), PlusError> {
        let account = self.account().await?;
        golems::call(&account, "/friends/block", json!({ "uuid": player.simple().to_string() }))
            .await
            .map(drop)
    }

    pub async fn unblock(&self, player: Uuid) -> Result<(), PlusError> {
        let account = self.account().await?;
        golems::call(&account, "/friends/unblock", json!({ "uuid": player.simple().to_string() }))
            .await
            .map(drop)
    }

    pub async fn is_special_chat_target(&self) -> Result<bool, PlusError> {
        Ok(false)
    }

    pub async fn status(&self) -> Result<PlayerStatus, PlusError> {
        Ok(if invisible() {
            PlayerStatus::Offline
        } else {
            PlayerStatus::Online
        })
    }

    pub async fn set_status(&self, status: PlayerStatus) -> Result<(), PlusError> {
        let hide = status == PlayerStatus::Offline;
        INVISIBLE.store(hide, Ordering::SeqCst);
        if hide {
            let account = self.account().await?;
            golems::leave(&account).await;
        }
        // the session sends (or stops sending) this launcher's presence straight away
        self.request_restart();
        Ok(())
    }

    pub async fn resolve_players(&self, ids: &[Uuid]) -> Result<Vec<ResolvedPlayer>, PlusError> {
        Ok(golems::resolved(ids))
    }

    pub async fn lookup_username(&self, username: &str) -> Result<Option<Uuid>, PlusError> {
        golems::mojang_uuid(username).await
    }

    async fn by_id(&self, path: &str, id: i32) -> Result<(), PlusError> {
        let account = self.account().await?;
        golems::call(&account, path, json!({ "id": id })).await.map(drop)
    }

    async fn account(&self) -> Result<MinecraftAccount, PlusError> {
        session::current_account(&self.auth)
            .await?
            .ok_or(PlusError::NoAccount)
    }
}
