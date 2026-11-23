pub const DUMMY_REPLACE_NEWLINE: &str = "\n";

/// Golem's Client's own Azure app registration (personal Microsoft accounts)
pub const MICROSOFT_CLIENT_ID: &str = "a783668c-37a5-4a71-80ad-4006674966d1";
pub const MINECRAFT_SCOPES: &str = "XboxLive.SignIn XboxLive.offline_access";
/// Empty until Golem's Client has its own CurseForge key; CurseForge is hidden while it is.
pub const CURSEFORGE_API_KEY: &str = "";
/// Golem's Client's Discord application (Rich Presence)
pub const DISCORD_CLIENT_ID: &str = "1573996158890803301";

pub const MODRINTH_API_URL: &str = "https://api.modrinth.com";
pub const MODRINTH_CDN_PREFIX: &str = "https://cdn.modrinth.com/data/";
pub const CURSEFORGE_API_URL: &str = "https://api.curseforge.com/v1";
pub const CURSEFORGE_GAME_ID: u32 = 432;
/// Game and loader version files: our own copy on the `data` branch
pub const METADATA_API_URL: &str =
    "https://raw.githubusercontent.com/VenoServices11/golems-client/data/meta";
pub const MCLOGS_API_URL: &str = "https://api.mclo.gs/1";
/// Our own copy of the launcher data (the `data` branch of the repo)
pub const META_URL_BASE: &str =
    "https://raw.githubusercontent.com/VenoServices11/golems-client/data";
pub const TOS_URL: &str = "https://github.com/VenoServices11/golems-client/blob/main/LICENSE";
pub const PRIVACY_URL: &str = "https://github.com/VenoServices11/golems-client#privacy";
/// Golem's Client's online-features server (chat, sharing, sync). Empty until there is one, which
/// keeps those features off (see `consent`).
pub const PLUS_BACKEND_URL: &str = "";
/// Known crashes and their fixes: our own copy on the `data` branch
pub const CRASH_DATA_URL: &str =
    "https://raw.githubusercontent.com/VenoServices11/golems-client/data/golems/crashes.json";

/// Golem's Client's own server: user stats (and the old Golem's Utils mod's presence)
pub const PRESENCE_URL: &str = "https://c418-presence.c418client.workers.dev";
/// emeraldgolem3, the owner: sees the Owner tab
pub const OWNER_UUID: &str = "dabe852b94dd4bb9b18d151c206be824";
pub const DISCORD_URL: &str = "https://discord.gg/q67qzHG2kf";
pub const TIKTOK_URL: &str = "https://www.tiktok.com/@holygrajl";
pub const GITHUB_URL: &str = "https://github.com/VenoServices11/golems-client";

/// Crash reporting is removed from Golem's Client: no DSN, and `reporting::init` never starts Sentry.
pub const SENTRY_DSN: &str = "";

pub const LAUNCHER_LABEL: &str = concat!(
    "Golem's Client v",
    env!("CARGO_PKG_VERSION"),
    cfg_select! {
        debug_assertions => " (debug)",
        _ => " (release)"
    }
);

pub const TARGET_OS: &str = cfg_select! {
    target_os = "windows" => "windows",
    target_os = "macos" => "osx",
    target_os = "linux" => "linux"
};

pub const NATIVE_ARCH: &str = cfg_select! {
    target_arch = "x86" => "32",
    target_arch = "x86_64" => "64",
    _ => "64"
};

pub const JAVA_BIN: &str = if cfg!(windows) { "javaw.exe" } else { "java" };

pub const ARCH_WIDTH: &str = if cfg!(target_pointer_width = "64") {
    "64"
} else {
    "32"
};

pub const LINE_ENDING: &str = if cfg!(windows) { "\r\n" } else { "\n" };
pub const CLASSPATH_SEPARATOR: &str = if cfg!(windows) { ";" } else { ":" };
