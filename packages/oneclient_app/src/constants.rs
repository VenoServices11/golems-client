pub const WINDOW_APP_ID: &str = "golems_client";
pub const WINDOW_TITLE: &str = "Golem's Client";

/// Golem's Client's update signing key (cargo-packager / minisign). Empty until the key is made;
/// the update check is skipped while it is, so no update is ever installed unsigned.
pub const UPDATER_PUBKEY: &str = "";
pub const UPDATER_ENDPOINT: &str =
    "https://github.com/VenoServices11/golems-client/releases/download/latest/latest.json";
pub const RELEASES_URL: &str = "https://github.com/VenoServices11/golems-client/releases/tag/latest";
