/*
 * Golem's Client parties: up to 8 friends together, with a party chat, and the leader's server so members can follow.
 * Copyright (C) 2026 veno15 - GNU General Public License v3.0.
 *
 * Everyone is in at most one party. Anyone in a party can invite their friends (invites last 10 minutes). When the
 * last member leaves, the party and its chat are gone. The shapes (Party, PartyInvite) are the ones the launcher's
 * Party tab already reads; a Party also has `names` (members' Minecraft names) and `server`: the server the leader is
 * playing on, sent by the leader's game, so the others can join them (only party members see it).
 *
 *   POST /party/state   { token }                     -> { party, incoming, outgoing }
 *   POST /party/create  { token }                     -> Party
 *   POST /party/invite  { token, uuid }               -> PartyInvite   (makes a party first if you have none)
 *   POST /party/accept  { token, id, leave_current }  -> Party
 *   POST /party/decline { token, id }                 -> { ok }
 *   POST /party/cancel  { token, id }                 -> { ok }
 *   POST /party/leave   { token }                     -> { ok }
 *   POST /party/disband { token }                     -> { ok }   (the leader)
 *   POST /party/leader  { token, uuid }               -> Party     (the leader hands it on)
 *   POST /party/kick    { token, uuid }               -> { ok }    (the leader)
 *
 * The friends list request (POST /friends/state) carries the same { party, incoming, outgoing } as `parties`, and
 * its presence can carry `party_server` (the full address the leader's game is on).
 */

import { ensureMessages } from "./messages.js";

const UUID = /^[0-9a-f]{32}$/;
const ID = /^[0-9a-f-]{36}$/;
export const MAX_PARTY = 8;
const INVITE_FOR = 10 * 60;
const ONLINE_FOR = 5 * 60;
const ADDRESS = /^[A-Za-z0-9.\-:\[\]_]{1,100}$/;

let ready = false;
async function ensure(env) {
  if (ready) return;
  await ensureMessages(env);
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS p_parties (id TEXT PRIMARY KEY, leader TEXT NOT NULL, chat_id INTEGER, made INTEGER NOT NULL, server TEXT, server_at INTEGER)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS p_members (uuid TEXT PRIMARY KEY, party_id TEXT NOT NULL, joined INTEGER NOT NULL)"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS p_members_party ON p_members (party_id)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS p_invites (id TEXT PRIMARY KEY, party_id TEXT NOT NULL, sender TEXT NOT NULL, receiver TEXT NOT NULL, at INTEGER NOT NULL, UNIQUE (party_id, receiver))"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS p_invites_receiver ON p_invites (receiver)"),
  ]);
  ready = true;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
}

function fail(message, status = 400, code = "bad_request") {
  return json({ error: message, code, message }, status);
}

function cleanUuid(v) {
  const u = String(v || "").toLowerCase().replace(/-/g, "");
  return UUID.test(u) ? u : null;
}

const dashed = (u) => `${u.slice(0, 8)}-${u.slice(8, 12)}-${u.slice(12, 16)}-${u.slice(16, 20)}-${u.slice(20)}`;
const iso = (s) => new Date(s * 1000).toISOString();

async function partyOf(env, uuid) {
  return env.DB.prepare("SELECT p.* FROM p_members m JOIN p_parties p ON p.id = m.party_id WHERE m.uuid = ?").bind(uuid).first();
}

async function isFriend(env, a, b) {
  return !!(await env.DB.prepare("SELECT 1 FROM f_friends WHERE uuid = ? AND friend = ?").bind(a, b).first());
}

/** A party, the way the launcher reads it. */
async function view(env, party, now) {
  const rows = await env.DB.prepare("SELECT m.uuid, m.joined, a.name, a.share_server, g.seen AS gseen, g.server AS gserver, l.seen AS lseen "
    + "FROM p_members m LEFT JOIN f_accounts a ON a.uuid = m.uuid "
    + "LEFT JOIN f_presence g ON g.uuid = m.uuid AND g.source = 'game' "
    + "LEFT JOIN f_presence l ON l.uuid = m.uuid AND l.source = 'launcher' "
    + "WHERE m.party_id = ? ORDER BY m.joined").bind(party.id).all();
  const names = {};
  const members = rows.results.map((r) => {
    if (r.name) names[r.uuid] = r.name;
    const inGame = r.gseen && r.gseen >= now - ONLINE_FOR;
    const online = inGame || (r.lseen && r.lseen >= now - ONLINE_FOR);
    let location = { kind: "menu", display: null };
    if (inGame && r.gserver === "Singleplayer") location = { kind: "singleplayer", display: "Singleplayer" };
    else if (inGame && r.gserver) location = { kind: "server", display: r.share_server ? r.gserver : "A server" };
    else if (!inGame && online) location = { kind: "other", display: "In the launcher" };
    return { player: dashed(r.uuid), joined_at: iso(r.joined), online: !!online, state: { location } };
  });
  return {
    id: party.id,
    leader: dashed(party.leader),
    settings: { invite_policy: "members" },
    chat_group_id: party.chat_id,
    members,
    names,
    server: party.server || null,
    server_at: party.server_at || 0,
  };
}

async function invitesView(env, where, uuid, now) {
  const rows = await env.DB.prepare(`SELECT i.*, (SELECT COUNT(*) FROM p_members m WHERE m.party_id = i.party_id) AS size, a.name AS sender_name `
    + `FROM p_invites i LEFT JOIN f_accounts a ON a.uuid = i.sender WHERE i.${where} = ? AND i.at >= ? ORDER BY i.at DESC`).bind(uuid, now - INVITE_FOR).all();
  return rows.results.map((i) => ({
    id: i.id, party_id: i.party_id, sender: dashed(i.sender), recipient: dashed(i.receiver), status: "pending",
    created_at: iso(i.at), expires_at: iso(i.at + INVITE_FOR), party_size: i.size, sender_name: i.sender_name || null,
  }));
}

/** For /friends/state and /party/state. */
export async function partyState(env, uuid, now) {
  await ensure(env);
  const p = await partyOf(env, uuid);
  return {
    party: p ? await view(env, p, now) : null,
    incoming: await invitesView(env, "receiver", uuid, now),
    outgoing: await invitesView(env, "sender", uuid, now),
  };
}

/** The leader's game says which server it's on (members can follow). */
export async function leaderServer(env, uuid, address, now) {
  await ensure(env);
  const p = await partyOf(env, uuid);
  if (!p || p.leader !== uuid) return;
  const server = address && ADDRESS.test(String(address)) ? String(address) : null;
  if ((p.server || null) === server) return;
  await env.DB.prepare("UPDATE p_parties SET server = ?, server_at = ? WHERE id = ?").bind(server, now, p.id).run();
}

async function makeParty(env, uuid, now) {
  const id = crypto.randomUUID();
  const chat = await env.DB.prepare("INSERT INTO m_groups (kind, name, owner, made, last_at) VALUES ('party', 'Party', ?, ?, ?) RETURNING id").bind(uuid, now, now).first();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO p_parties (id, leader, chat_id, made) VALUES (?, ?, ?, ?)").bind(id, uuid, chat.id, now),
    env.DB.prepare("INSERT INTO p_members (uuid, party_id, joined) VALUES (?, ?, ?)").bind(uuid, id, now),
    env.DB.prepare("INSERT INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(chat.id, uuid, now),
  ]);
  return env.DB.prepare("SELECT * FROM p_parties WHERE id = ?").bind(id).first();
}

/** Takes someone out of their party; the next member leads, and an empty party (and its chat) goes. */
async function leaveParty(env, uuid) {
  const p = await partyOf(env, uuid);
  if (!p) return;
  await env.DB.batch([
    env.DB.prepare("DELETE FROM p_members WHERE uuid = ?").bind(uuid),
    env.DB.prepare("DELETE FROM m_members WHERE group_id = ? AND uuid = ?").bind(p.chat_id, uuid),
    env.DB.prepare("DELETE FROM p_invites WHERE party_id = ? AND sender = ?").bind(p.id, uuid),
  ]);
  const next = await env.DB.prepare("SELECT uuid FROM p_members WHERE party_id = ? ORDER BY joined LIMIT 1").bind(p.id).first();
  if (!next) {
    await disbandParty(env, p);
  } else if (p.leader === uuid) {
    await env.DB.prepare("UPDATE p_parties SET leader = ?, server = NULL, server_at = NULL WHERE id = ?").bind(next.uuid, p.id).run();
  }
}

async function disbandParty(env, p) {
  await env.DB.batch([
    env.DB.prepare("DELETE FROM p_members WHERE party_id = ?").bind(p.id),
    env.DB.prepare("DELETE FROM p_invites WHERE party_id = ?").bind(p.id),
    env.DB.prepare("DELETE FROM p_parties WHERE id = ?").bind(p.id),
    env.DB.prepare("DELETE FROM m_messages WHERE group_id = ?").bind(p.chat_id),
    env.DB.prepare("DELETE FROM m_members WHERE group_id = ?").bind(p.chat_id),
    env.DB.prepare("DELETE FROM m_groups WHERE id = ?").bind(p.chat_id),
  ]);
}

export async function parties(path, env, body, now, uuid) {
  await ensure(env);
  switch (path) {
    case "/party/state":
      return json(await partyState(env, uuid, now));
    case "/party/create": {
      await leaveParty(env, uuid);
      return json(await view(env, await makeParty(env, uuid, now), now));
    }
    case "/party/invite": {
      const them = cleanUuid(body.uuid);
      if (!them || them === uuid) return fail("Pick a friend to invite.");
      if (!(await isFriend(env, uuid, them))) return fail("You can only invite friends.", 403, "not_friends");
      const p = (await partyOf(env, uuid)) || (await makeParty(env, uuid, now));
      const n = (await env.DB.prepare("SELECT COUNT(*) AS n FROM p_members WHERE party_id = ?").bind(p.id).first()).n;
      if (n >= MAX_PARTY) return fail(`A party can have up to ${MAX_PARTY} people.`, 409, "party_full");
      if (await env.DB.prepare("SELECT 1 FROM p_members WHERE uuid = ? AND party_id = ?").bind(them, p.id).first()) {
        return fail("They're already in your party.", 409, "already_member");
      }
      const id = crypto.randomUUID();
      await env.DB.prepare("INSERT INTO p_invites (id, party_id, sender, receiver, at) VALUES (?, ?, ?, ?, ?) "
        + "ON CONFLICT(party_id, receiver) DO UPDATE SET id = excluded.id, sender = excluded.sender, at = excluded.at").bind(id, p.id, uuid, them, now).run();
      return json({
        id, party_id: p.id, sender: dashed(uuid), recipient: dashed(them), status: "pending",
        created_at: iso(now), expires_at: iso(now + INVITE_FOR), party_size: n,
      });
    }
    case "/party/accept": {
      const id = String(body.id || "");
      if (!ID.test(id)) return fail("bad request");
      const invite = await env.DB.prepare("SELECT * FROM p_invites WHERE id = ? AND receiver = ? AND at >= ?").bind(id, uuid, now - INVITE_FOR).first();
      if (!invite) return fail("That invite isn't there any more.", 404, "invite_gone");
      const p = await env.DB.prepare("SELECT * FROM p_parties WHERE id = ?").bind(invite.party_id).first();
      if (!p) return fail("That party isn't there any more.", 404, "party_gone");
      const n = (await env.DB.prepare("SELECT COUNT(*) AS n FROM p_members WHERE party_id = ?").bind(p.id).first()).n;
      if (n >= MAX_PARTY) return fail("That party is full.", 409, "party_full");
      const current = await partyOf(env, uuid);
      if (current && current.id !== p.id) {
        if (body.leave_current === false) return fail("Leave your party first.", 409, "in_party");
        await leaveParty(env, uuid);
      }
      await env.DB.batch([
        env.DB.prepare("INSERT OR REPLACE INTO p_members (uuid, party_id, joined) VALUES (?, ?, ?)").bind(uuid, p.id, now),
        env.DB.prepare("INSERT OR IGNORE INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(p.chat_id, uuid, now),
        env.DB.prepare("DELETE FROM p_invites WHERE party_id = ? AND receiver = ?").bind(p.id, uuid),
      ]);
      return json(await view(env, p, now));
    }
    case "/party/decline":
      await env.DB.prepare("DELETE FROM p_invites WHERE id = ? AND receiver = ?").bind(String(body.id || ""), uuid).run();
      return json({ ok: true });
    case "/party/cancel":
      await env.DB.prepare("DELETE FROM p_invites WHERE id = ? AND sender = ?").bind(String(body.id || ""), uuid).run();
      return json({ ok: true });
    case "/party/leave":
      await leaveParty(env, uuid);
      return json({ ok: true });
    case "/party/disband": {
      const p = await partyOf(env, uuid);
      if (!p) return json({ ok: true });
      if (p.leader !== uuid) return fail("Only the leader can end the party.", 403, "not_leader");
      await disbandParty(env, p);
      return json({ ok: true });
    }
    case "/party/leader":
    case "/party/kick": {
      const p = await partyOf(env, uuid);
      const them = cleanUuid(body.uuid);
      if (!p) return fail("You're not in a party.", 404, "no_party");
      if (p.leader !== uuid) return fail("Only the leader can do that.", 403, "not_leader");
      if (!them || them === uuid || !(await env.DB.prepare("SELECT 1 FROM p_members WHERE uuid = ? AND party_id = ?").bind(them, p.id).first())) {
        return fail("They're not in your party.", 404, "not_member");
      }
      if (path === "/party/leader") {
        await env.DB.prepare("UPDATE p_parties SET leader = ?, server = NULL, server_at = NULL WHERE id = ?").bind(them, p.id).run();
        return json(await view(env, { ...p, leader: them, server: null, server_at: null }, now));
      }
      await leaveParty(env, them);
      return json({ ok: true });
    }
    default:
      return fail("not found", 404, "not_found");
  }
}

/** Old invites go (from the 15-minute clean-up). */
export async function partiesCleanup(env, now) {
  await ensure(env);
  await env.DB.prepare("DELETE FROM p_invites WHERE at < ?").bind(now - INVITE_FOR).run();
}
