pub const DUMMY_REPLACE_NEWLINE: &str = "\n";

/// Golem's Client's own Azure app registration (personal Microsoft accounts).
/// Placeholder until the app is registered; Microsoft sign-in fails until it is filled in.
pub const MICROSOFT_CLIENT_ID: &str = "00000000-0000-0000-0000-000000000000";
pub const MINECRAFT_SCOPES: &str = "XboxLive.SignIn XboxLive.offline_access";
/// Empty until Golem's Client has its own CurseForge key; CurseForge is hidden while it is.
pub const CURSEFORGE_API_KEY: &str = "";
/// Golem's Client's Discord application. Empty until it is made; Rich Presence stays off while it is.
pub const DISCORD_CLIENT_ID: &str = "";

pub const MODRINTH_API_URL: &str = "https://api.modrinth.com";
pub const MODRINTH_CDN_PREFIX: &str = "https://cdn.modrinth.com/data/";
pub const CURSEFORGE_API_URL: &str = "https://api.curseforge.com/v1";
pub const CURSEFORGE_GAME_ID: u32 = 432;
pub const METADATA_API_URL: &str = "https://meta.polyfrost.org";
pub const MCLOGS_API_URL: &str = "https://api.mclo.gs/1";
pub const SKYCLIENT_BASE_URL: &str =
    "https://raw.githubusercontent.com/SkyblockClient/SkyblockClient-REPO/refs/heads/main/v1";
/// Our own copy of the launcher data (the `data` branch of the repo)
pub const META_URL_BASE: &str =
    "https://raw.githubusercontent.com/VenoServices11/golems-client/data";
pub const TOS_URL: &str = "https://github.com/VenoServices11/golems-client/blob/main/LICENSE";
pub const PRIVACY_URL: &str = "https://github.com/VenoServices11/golems-client#privacy";
pub const PLUS_BACKEND_URL: &str = "https://plus.polyfrost.org";
pub const CRASH_DATA_URL: &str =
    "https://raw.githubusercontent.com/Polyfrost/CrashData/refs/heads/main/crashes.json";

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
