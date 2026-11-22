//! Golem's Client's online features (chat, friends, sharing and cloud sync) need a server of
//! Golem's Client's own, which doesn't exist yet. Until it does they stay off: every request to that
//! server goes through [`blocks_url`] and is refused before anything is sent.

use std::sync::atomic::{AtomicBool, Ordering};

use crate::constants::PLUS_BACKEND_URL;

static DECLINED: AtomicBool = AtomicBool::new(true);

/// The online features are always off for now, whatever the settings say
pub fn init(_declined: bool) {
    DECLINED.store(true, Ordering::SeqCst);
}

pub fn decline() {
    DECLINED.store(true, Ordering::SeqCst);
}

#[must_use]
pub fn declined() -> bool {
    DECLINED.load(Ordering::Relaxed)
}

#[must_use]
pub fn blocks_url(url: &str) -> bool {
    declined() && is_gated_url(url)
}

/// Requests to the online-features server, and anything that isn't a valid URL, are gated
#[must_use]
pub fn is_gated_url(url: &str) -> bool {
    url::Url::parse(url)
        .ok()
        .and_then(|parsed| parsed.host_str().map(is_gated_host))
        .unwrap_or(true)
}

#[must_use]
pub fn is_gated_host(host: &str) -> bool {
    let host = host.trim_end_matches('.').to_ascii_lowercase();
    online_features_host().is_some_and(|gated| host == gated || host.ends_with(&format!(".{gated}")))
}

fn online_features_host() -> Option<String> {
    url::Url::parse(PLUS_BACKEND_URL)
        .ok()?
        .host_str()
        .map(|host| host.trim_end_matches('.').to_ascii_lowercase())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn leaves_ordinary_hosts_alone() {
        for host in [
            "api.modrinth.com",
            "sessionserver.mojang.com",
            "api.minecraftservices.com",
            "raw.githubusercontent.com",
            "github.com",
        ] {
            assert!(!is_gated_host(host), "unexpected gate on {host}");
        }
    }

    #[test]
    fn unparseable_urls_fail_closed() {
        assert!(is_gated_url("not a url"));
        assert!(is_gated_url(""));
        assert!(is_gated_url("/v1/instances"));
        assert!(is_gated_url("file:///etc/hosts"));
    }

    #[test]
    fn the_online_features_stay_off() {
        init(false);
        assert!(declined());
        assert!(blocks_url(&format!("{PLUS_BACKEND_URL}/v1/instances")));
    }
}
