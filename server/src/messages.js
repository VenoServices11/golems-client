/*
 * Golem's Client messages: one-on-one messages between friends and group chats, for the launcher and the game.
 * Copyright (C) 2026 veno15 - GNU General Public License v3.0.
 *
 * Messages are kept for 30 days. Only members of a chat can read it, and you can only start a chat with friends
 * (or add friends to a group). Signing in is the same as for friends (friends.js).
 *
 *   POST /messages/groups  { token }                          -> { groups: [GroupSummary] }   newest activity first
 *   POST /messages/dm      { token, uuid }                    -> GroupSummary   (the chat with that friend, made if needed)
 *   POST /messages/create  { token, name, members: [uuid] }   -> GroupSummary
 *   POST /messages/rename  { token, id, name }                -> { ok }
 *   POST /messages/add     { token, id, uuid }                -> { ok }
 *   POST /messages/remove  { token, id, uuid }                -> { ok }   (yourself: leave the group)
 *   POST /messages/list    { token, id, before?, limit? }     -> { messages: [GroupMessage] }   up to 100, before an id
 *   POST /messages/send    { token, id, content, key }        -> GroupMessage   (key: the client's id for the message,
 *                                                                               so a retry doesn't send it twice)
 *   POST /messages/edit    { token, id, message, content }    -> GroupMessage   (your own messages)
 *   POST /messages/delete  { token, id, message }             -> { ok }         (your own messages)
 *   POST /messages/read    { token, id, message }             -> { ok }
 *
 * The shapes (GroupSummary, GroupMessage) are the ones the launcher's chat already reads (a GroupSummary also has
 * `names`: the members' Minecraft names, by UUID). The friends list request
 * (POST /friends/state) also carries { inbox: { latest, unread }, messages: [new since the client's `since`] }.
 */

const UUID = /^[0-9a-f]{32}$/;
const KEEP_FOR = 30 * 24 * 60 * 60;
const MAX_LENGTH = 512;
const MAX_MEMBERS = 20;
const MAX_NAME = 32;
const MAX_PAGE = 100;
const MAX_NEW = 50;

let ready = false;
export async function ensureMessages(env) {
  if (ready) return;
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS m_groups (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, name TEXT, owner TEXT, dm_key TEXT UNIQUE, made INTEGER NOT NULL, last_at INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS m_members (group_id INTEGER NOT NULL, uuid TEXT NOT NULL, joined INTEGER NOT NULL, last_read INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (group_id, uuid))"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS m_members_uuid ON m_members (uuid)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS m_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, sender TEXT NOT NULL, content TEXT NOT NULL, at INTEGER NOT NULL, edited INTEGER, client_key TEXT, UNIQUE (group_id, sender, client_key))"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS m_messages_group ON m_messages (group_id, id)"),
  ]);
  ready = true;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
}

function fail(message, status = 400, code = "bad_request") {
  return json({ error: message, code }, status);
}

function cleanUuid(v) {
  const u = String(v || "").toLowerCase().replace(/-/g, "");
  return UUID.test(u) ? u : null;
}

const iso = (seconds) => new Date(seconds * 1000).toISOString();

function messageOut(m) {
  return { id: m.id, group_id: m.group_id, sender: m.sender, content: m.content, sent_at: iso(m.at), edited_at: m.edited ? iso(m.edited) : null };
}

async function isFriend(env, a, b) {
  return !!(await env.DB.prepare("SELECT 1 FROM f_friends WHERE uuid = ? AND friend = ?").bind(a, b).first());
}

async function member(env, id, uuid) {
  return env.DB.prepare("SELECT g.id, g.kind, g.name, g.owner, m.last_read FROM m_groups g JOIN m_members m ON m.group_id = g.id "
    + "WHERE g.id = ? AND m.uuid = ?").bind(id, uuid).first();
}

/** Chats (GroupSummary) for these group ids, as `uuid` sees them. */
async function summaries(env, uuid, ids) {
  if (ids.length === 0) return [];
  const marks = ids.map(() => "?").join(",");
  const [groups, members, lasts] = await env.DB.batch([
    env.DB.prepare(`SELECT g.id, g.kind, g.name, g.last_at, m.last_read FROM m_groups g JOIN m_members m ON m.group_id = g.id AND m.uuid = ? WHERE g.id IN (${marks})`).bind(uuid, ...ids),
    env.DB.prepare(`SELECT m.group_id, m.uuid, a.name FROM m_members m LEFT JOIN f_accounts a ON a.uuid = m.uuid WHERE m.group_id IN (${marks})`).bind(...ids),
    env.DB.prepare(`SELECT m.* FROM m_messages m JOIN (SELECT group_id, MAX(id) AS id FROM m_messages WHERE group_id IN (${marks}) GROUP BY group_id) l ON l.id = m.id`).bind(...ids),
  ]);
  const byGroup = new Map();
  const names = new Map();
  for (const m of members.results) {
    if (!byGroup.has(m.group_id)) byGroup.set(m.group_id, []);
    byGroup.get(m.group_id).push(m.uuid);
    if (!names.has(m.group_id)) names.set(m.group_id, {});
    if (m.name) names.get(m.group_id)[m.uuid] = m.name;
  }
  const lastBy = new Map(lasts.results.map((m) => [m.group_id, m]));
  return groups.results
    .sort((a, b) => b.last_at - a.last_at)
    .map((g) => {
      const last = lastBy.get(g.id);
      return {
        id: g.id,
        kind: g.kind,
        name: g.name || null,
        members: byGroup.get(g.id) || [],
        names: names.get(g.id) || {},
        last_message: last ? { content: last.content, sender: last.sender, sent_at: iso(last.at) } : null,
        unread: !!last && last.id > g.last_read && last.sender !== uuid,
        special: false,
        awaiting_reply: false,
      };
    });
}

async function one(env, uuid, id) {
  return (await summaries(env, uuid, [id]))[0];
}

/** For /friends/state: what's new in your chats since the message id `since`. */
export async function inbox(env, uuid, since) {
  await ensureMessages(env);
  const [latest, unread] = await env.DB.batch([
    env.DB.prepare("SELECT MAX(x.id) AS id FROM m_messages x JOIN m_members m ON m.group_id = x.group_id AND m.uuid = ?").bind(uuid),
    env.DB.prepare("SELECT COUNT(*) AS n FROM m_members m JOIN (SELECT group_id, MAX(id) AS id FROM m_messages GROUP BY group_id) l ON l.group_id = m.group_id "
      + "JOIN m_messages x ON x.id = l.id WHERE m.uuid = ? AND l.id > m.last_read AND x.sender != ?").bind(uuid, uuid),
  ]);
  const out = { inbox: { latest: latest.results[0]?.id || 0, unread: unread.results[0]?.n || 0 } };
  const from = Number(since);
  if (Number.isInteger(from) && from >= 0 && out.inbox.latest > from) {
    const rows = await env.DB.prepare("SELECT x.* FROM m_messages x JOIN m_members m ON m.group_id = x.group_id AND m.uuid = ? "
      + "WHERE x.id > ? ORDER BY x.id DESC LIMIT ?").bind(uuid, from, MAX_NEW).all();
    out.messages = rows.results.reverse().map(messageOut);
  }
  return out;
}

export async function messages(path, env, body, now, uuid) {
  await ensureMessages(env);
  switch (path) {
    case "/messages/groups": return groups(env, uuid);
    case "/messages/dm": return dm(env, uuid, body, now);
    case "/messages/create": return create(env, uuid, body, now);
    case "/messages/rename": return rename(env, uuid, body);
    case "/messages/add": return addMember(env, uuid, body, now);
    case "/messages/remove": return removeMember(env, uuid, body);
    case "/messages/list": return list(env, uuid, body);
    case "/messages/send": return send(env, uuid, body, now);
    case "/messages/edit": return edit(env, uuid, body, now);
    case "/messages/delete": return del(env, uuid, body);
    case "/messages/read": return read(env, uuid, body);
    default: return fail("not found", 404, "not_found");
  }
}

async function groups(env, uuid) {
  const rows = await env.DB.prepare("SELECT group_id FROM m_members WHERE uuid = ?").bind(uuid).all();
  const ids = rows.results.map((r) => r.group_id);
  const out = [];
  for (let i = 0; i < ids.length; i += 40) out.push(...(await summaries(env, uuid, ids.slice(i, i + 40))));
  out.sort((a, b) => (b.last_message ? Date.parse(b.last_message.sent_at) : 0) - (a.last_message ? Date.parse(a.last_message.sent_at) : 0));
  return json({ groups: out });
}

async function dm(env, uuid, body, now) {
  const them = cleanUuid(body.uuid);
  if (!them || them === uuid) return fail("bad request");
  const key = [uuid, them].sort().join(":");
  const existing = await env.DB.prepare("SELECT id FROM m_groups WHERE dm_key = ?").bind(key).first();
  if (existing) return json(await one(env, uuid, existing.id));
  if (!(await isFriend(env, uuid, them))) return fail("You can only message friends.", 403, "not_friends");
  const made = await env.DB.prepare("INSERT INTO m_groups (kind, dm_key, made, last_at) VALUES ('dm', ?, ?, ?) RETURNING id").bind(key, now, now).first();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(made.id, uuid, now),
    env.DB.prepare("INSERT INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(made.id, them, now),
  ]);
  return json(await one(env, uuid, made.id));
}

function cleanName(v) {
  const name = String(v || "").replace(/\s+/g, " ").trim().slice(0, MAX_NAME);
  return name || null;
}

async function create(env, uuid, body, now) {
  const wanted = [...new Set((Array.isArray(body.members) ? body.members : []).map(cleanUuid).filter((u) => u && u !== uuid))];
  if (wanted.length === 0) return fail("Pick at least one friend.");
  if (wanted.length + 1 > MAX_MEMBERS) return fail(`A group can have up to ${MAX_MEMBERS} people.`);
  for (const u of wanted) if (!(await isFriend(env, uuid, u))) return fail("You can only add friends to a group.", 403, "not_friends");
  const made = await env.DB.prepare("INSERT INTO m_groups (kind, name, owner, made, last_at) VALUES ('group', ?, ?, ?, ?) RETURNING id")
    .bind(cleanName(body.name), uuid, now, now).first();
  await env.DB.batch([uuid, ...wanted].map((u) => env.DB.prepare("INSERT INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(made.id, u, now)));
  return json(await one(env, uuid, made.id));
}

async function rename(env, uuid, body) {
  const g = await member(env, Number(body.id), uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  if (g.kind !== "group") return fail("Only groups have names.");
  await env.DB.prepare("UPDATE m_groups SET name = ? WHERE id = ?").bind(cleanName(body.name), g.id).run();
  return json({ ok: true });
}

async function addMember(env, uuid, body, now) {
  const g = await member(env, Number(body.id), uuid);
  const them = cleanUuid(body.uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  if (g.kind !== "group" || !them) return fail("bad request");
  if (!(await isFriend(env, uuid, them))) return fail("You can only add friends to a group.", 403, "not_friends");
  const n = (await env.DB.prepare("SELECT COUNT(*) AS n FROM m_members WHERE group_id = ?").bind(g.id).first()).n;
  if (n >= MAX_MEMBERS) return fail(`A group can have up to ${MAX_MEMBERS} people.`);
  await env.DB.prepare("INSERT OR IGNORE INTO m_members (group_id, uuid, joined) VALUES (?, ?, ?)").bind(g.id, them, now).run();
  return json({ ok: true });
}

async function removeMember(env, uuid, body) {
  const g = await member(env, Number(body.id), uuid);
  const them = cleanUuid(body.uuid) || uuid;
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  if (g.kind !== "group") return fail("You can't leave a one-on-one chat.");
  if (them !== uuid && g.owner !== uuid) return fail("Only the person who made the group can remove people.", 403, "not_owner");
  await env.DB.prepare("DELETE FROM m_members WHERE group_id = ? AND uuid = ?").bind(g.id, them).run();
  const left = (await env.DB.prepare("SELECT COUNT(*) AS n FROM m_members WHERE group_id = ?").bind(g.id).first()).n;
  if (left === 0) {
    await env.DB.batch([
      env.DB.prepare("DELETE FROM m_messages WHERE group_id = ?").bind(g.id),
      env.DB.prepare("DELETE FROM m_groups WHERE id = ?").bind(g.id),
    ]);
  }
  return json({ ok: true });
}

async function list(env, uuid, body) {
  const g = await member(env, Number(body.id), uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  const limit = Math.max(1, Math.min(MAX_PAGE, Number(body.limit) || 50));
  const before = Number(body.before);
  const rows = Number.isInteger(before) && before > 0
    ? await env.DB.prepare("SELECT * FROM m_messages WHERE group_id = ? AND id < ? ORDER BY id DESC LIMIT ?").bind(g.id, before, limit).all()
    : await env.DB.prepare("SELECT * FROM m_messages WHERE group_id = ? ORDER BY id DESC LIMIT ?").bind(g.id, limit).all();
  return json({ messages: rows.results.reverse().map(messageOut) });
}

async function send(env, uuid, body, now) {
  const g = await member(env, Number(body.id), uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  const content = String(body.content || "").trim();
  if (!content) return fail("Type a message first.");
  if (content.length > MAX_LENGTH) return fail(`Messages can be up to ${MAX_LENGTH} characters.`);
  const key = /^[A-Za-z0-9-]{1,64}$/.test(String(body.key || "")) ? String(body.key) : null;
  if (g.kind === "dm") {
    // a one-on-one chat stays open only while you're friends
    const other = await env.DB.prepare("SELECT uuid FROM m_members WHERE group_id = ? AND uuid != ?").bind(g.id, uuid).first();
    if (!other || !(await isFriend(env, uuid, other.uuid))) return fail("You can only message friends.", 403, "not_friends");
  }
  // too many messages in a minute
  const recent = (await env.DB.prepare("SELECT COUNT(*) AS n FROM m_messages WHERE sender = ? AND at >= ?").bind(uuid, now - 60).first()).n;
  if (recent >= 30) return fail("Slow down a little.", 429, "rate_limited");
  if (key) {
    const again = await env.DB.prepare("SELECT * FROM m_messages WHERE group_id = ? AND sender = ? AND client_key = ?").bind(g.id, uuid, key).first();
    if (again) return json(messageOut(again));
  }
  const m = await env.DB.prepare("INSERT INTO m_messages (group_id, sender, content, at, client_key) VALUES (?, ?, ?, ?, ?) RETURNING *")
    .bind(g.id, uuid, content, now, key).first();
  await env.DB.batch([
    env.DB.prepare("UPDATE m_groups SET last_at = ? WHERE id = ?").bind(now, g.id),
    env.DB.prepare("UPDATE m_members SET last_read = ? WHERE group_id = ? AND uuid = ?").bind(m.id, g.id, uuid),
  ]);
  return json(messageOut(m));
}

async function edit(env, uuid, body, now) {
  const g = await member(env, Number(body.id), uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  const content = String(body.content || "").trim();
  if (!content || content.length > MAX_LENGTH) return fail(`Messages can be 1 to ${MAX_LENGTH} characters.`);
  const m = await env.DB.prepare("UPDATE m_messages SET content = ?, edited = ? WHERE id = ? AND group_id = ? AND sender = ? RETURNING *")
    .bind(content, now, Number(body.message), g.id, uuid).first();
  if (!m) return fail("You can only edit your own messages.", 403, "not_yours");
  return json(messageOut(m));
}

async function del(env, uuid, body) {
  const g = await member(env, Number(body.id), uuid);
  if (!g) return fail("You're not in that chat.", 403, "not_member");
  await env.DB.prepare("DELETE FROM m_messages WHERE id = ? AND group_id = ? AND sender = ?").bind(Number(body.message), g.id, uuid).run();
  return json({ ok: true });
}

async function read(env, uuid, body) {
  const id = Number(body.id), message = Number(body.message);
  if (!Number.isInteger(id) || !Number.isInteger(message)) return fail("bad request");
  await env.DB.prepare("UPDATE m_members SET last_read = MAX(last_read, ?) WHERE group_id = ? AND uuid = ?").bind(message, id, uuid).run();
  return json({ ok: true });
}

/** Messages older than 30 days go (from the 15-minute clean-up). */
export async function messagesCleanup(env, now) {
  await ensureMessages(env);
  await env.DB.prepare("DELETE FROM m_messages WHERE at < ?").bind(now - KEEP_FOR).run();
}
