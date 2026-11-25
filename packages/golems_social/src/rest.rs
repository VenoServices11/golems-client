//! The friends panel's requests, answered by Golem's Client's own server (see `golems.rs`).
//! Messages and group chats are kept there too (30 days).

use std::sync::atomic::{AtomicBool, Ordering};

use serde_json::json;
use uuid::Uuid;

use golems_auth::MinecraftAccount;

use crate::models::{
    BlockedPlayer, CursorPage, Friend, FriendRequest, GroupMessage, GroupSummary,
    IncomingSessionInvite, MAX_PAGE_SIZE, PlayerStatus, ResolvedPlayer,
};
use crate::{PlusClient, PlusError, golems, session};

/// "Invisible" in the panel: this launcher stops telling friends it's open.
static INVISIBLE: AtomicBool = AtomicBool::new(false);

#[must_use]
pub fn invisible() -> bool {
    INVISIBLE.load(Ordering::SeqCst)
}

impl PlusClient {
    /// Your chats, most recently active first (all of them in one page).
    pub async fn groups_page(
        &self,
        cursor: Option<&str>,
        _limit: u64,
    ) -> Result<CursorPage<GroupSummary>, PlusError> {
        if cursor.is_some() {
            return Ok(CursorPage {
                items: Vec::new(),
                cursor: None,
            });
        }
        let account = self.account().await?;
        let groups: golems::Groups<GroupSummary> =
            golems::call_as(&account, "/messages/groups", json!({})).await?;
        Ok(CursorPage {
            items: groups.groups,
            cursor: None,
        })
    }

    pub async fn open_direct_message(&self, player: Uuid) -> Result<GroupSummary, PlusError> {
        let account = self.account().await?;
        golems::call_as(&account, "/messages/dm", json!({ "uuid": player.simple().to_string() })).await
    }

    pub async fn create_group(
        &self,
        name: &str,
        members: &[Uuid],
    ) -> Result<GroupSummary, PlusError> {
        let account = self.account().await?;
        let members: Vec<String> = members.iter().map(|m| m.simple().to_string()).collect();
        golems::call_as(&account, "/messages/create", json!({ "name": name, "members": members })).await
    }

    pub async fn rename_group(&self, group_id: i32, name: &str) -> Result<(), PlusError> {
        self.chat_call("/messages/rename", json!({ "id": group_id, "name": name })).await
    }

    pub async fn add_member(&self, group_id: i32, player: Uuid) -> Result<(), PlusError> {
        self.chat_call("/messages/add", json!({ "id": group_id, "uuid": player.simple().to_string() }))
            .await
    }

    pub async fn remove_member(&self, group_id: i32, player: Uuid) -> Result<(), PlusError> {
        self.chat_call(
            "/messages/remove",
            json!({ "id": group_id, "uuid": player.simple().to_string() }),
        )
        .await
    }

    pub async fn messages(
        &self,
        group_id: i32,
        before: Option<i64>,
        limit: Option<u64>,
    ) -> Result<Vec<GroupMessage>, PlusError> {
        let account = self.account().await?;
        let mut body = json!({ "id": group_id, "limit": limit.unwrap_or(50).min(MAX_PAGE_SIZE) });
        if let Some(before) = before {
            body["before"] = json!(before);
        }
        let page: golems::Messages<GroupMessage> =
            golems::call_as(&account, "/messages/list", body).await?;
        Ok(page.messages)
    }

    pub async fn send_message(
        &self,
        group_id: i32,
        content: &str,
        idempotency_key: Uuid,
    ) -> Result<GroupMessage, PlusError> {
        let account = self.account().await?;
        golems::call_as(
            &account,
            "/messages/send",
            json!({ "id": group_id, "content": content, "key": idempotency_key.simple().to_string() }),
        )
        .await
    }

    pub async fn edit_message(
        &self,
        group_id: i32,
        message_id: i64,
        content: &str,
    ) -> Result<GroupMessage, PlusError> {
        let account = self.account().await?;
        golems::call_as(
            &account,
            "/messages/edit",
            json!({ "id": group_id, "message": message_id, "content": content }),
        )
        .await
    }

    pub async fn delete_message(&self, group_id: i32, message_id: i64) -> Result<(), PlusError> {
        self.chat_call("/messages/delete", json!({ "id": group_id, "message": message_id }))
            .await
    }

    pub async fn mark_read(&self, group_id: i32, message_id: i64) -> Result<(), PlusError> {
        self.chat_call("/messages/read", json!({ "id": group_id, "message": message_id }))
            .await
    }

    async fn chat_call(&self, path: &str, body: serde_json::Value) -> Result<(), PlusError> {
        let account = self.account().await?;
        golems::call(&account, path, body).await.map(drop)
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
