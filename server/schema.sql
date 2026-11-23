-- Golem's Utils presence: who is online with the client right now, and every player who has used it
-- (Minecraft UUID and name, first and last seen) for the owner's admin page.
CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  uuid TEXT NOT NULL,
  expires INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS online (
  uuid TEXT PRIMARY KEY,
  seen INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS challenges (
  server_id TEXT PRIMARY KEY,
  expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS online_seen ON online (seen);
CREATE TABLE IF NOT EXISTS users (
  uuid TEXT PRIMARY KEY,
  name TEXT,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS capes (
  uuid TEXT PRIMARY KEY,
  cape TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS custom_capes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  png TEXT NOT NULL,
  made INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS cape_gifts (
  uuid TEXT NOT NULL,
  cape TEXT NOT NULL,
  PRIMARY KEY (uuid, cape)
);
CREATE TABLE IF NOT EXISTS owners (
  uuid TEXT PRIMARY KEY,
  since INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS broadcasts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  text TEXT NOT NULL,
  at INTEGER NOT NULL
);

-- small counters, e.g. when any cape last changed
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL);

-- cloud config sync: one saved client config per account
CREATE TABLE IF NOT EXISTS configs (uuid TEXT PRIMARY KEY, data TEXT NOT NULL, updated INTEGER NOT NULL);

-- Golem's Client (the launcher): everyone who used it, and who has it open right now
CREATE TABLE IF NOT EXISTS launcher_users (uuid TEXT PRIMARY KEY, name TEXT, version TEXT, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS launcher_online (uuid TEXT PRIMARY KEY, seen INTEGER NOT NULL, state TEXT NOT NULL, instance TEXT, mc TEXT);
