//! The owner's page: how many people use Golem's Client, who is online right now, and everyone who
//! has used it. Only shown when signed in as the owner, and the stats need the owner key too.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use freya::prelude::*;
use golems_core::stats::{
    OwnerStats, StatsUser, owner_stats, post_announcement, save_owner_key, saved_owner_key,
};

use super::{section_header, settings_page};
use crate::components::{Avatar, Button, IconType, TextInput};
use crate::theme::{self, colors, rounded, text_size};
use crate::ui::border_all_color;
use crate::view::app::settings::settings_row;

const REFRESH_EVERY: Duration = Duration::from_secs(20);

#[derive(PartialEq)]
pub struct SettingsOwner;

impl Component for SettingsOwner {
    fn render(&self) -> impl IntoElement {
        let mut key = use_state(|| saved_owner_key().unwrap_or_default());
        let mut has_key = use_state(|| saved_owner_key().is_some());
        let mut stats = use_state(|| None::<Result<OwnerStats, String>>);
        let mut revision = use_state(|| 0u32);
        let banner = use_state(String::new);
        let banner_status = use_state(|| None::<String>);

        // refresh every few seconds while the page is open
        use_hook(move || {
            spawn(async move {
                let mut revision = revision;
                loop {
                    tokio::time::sleep(REFRESH_EVERY).await;
                    *revision.write() += 1;
                }
            });
        });

        let deps = (*revision.read(), *has_key.read());
        use_side_effect_with_deps(&deps, move |(_, has_key)| {
            if !*has_key {
                return;
            }
            let key = key.peek().clone();
            spawn(async move {
                let mut stats = stats;
                let result = crate::launcher::off_ui(async move {
                    match crate::launcher::state() {
                        Ok(state) => owner_stats(&state, &key).await,
                        Err(_) => Err("The launcher is still starting.".to_string()),
                    }
                })
                .await;
                stats.set(Some(result));
            });
        });

        let mut page = settings_page();

        if !*has_key.read() {
            return page
                .child(section_header("Owner key"))
                .child(settings_row(
                    IconType::Key01,
                    "Owner key",
                    "The ADMIN_KEY you set on your server. Saved on this PC only.",
                    rect()
                        .horizontal()
                        .spacing(8.)
                        .cross_align(Alignment::Center)
                        .child(
                            TextInput::new(key)
                                .placeholder("Owner key")
                                .width(Size::px(200.)),
                        )
                        .child(
                            Button::new()
                                .primary()
                                .small()
                                .on_press(move |_| {
                                    let value = key.peek().trim().to_string();
                                    if value.is_empty() {
                                        return;
                                    }
                                    if save_owner_key(&value).is_ok() {
                                        stats.set(None);
                                        has_key.set(true);
                                        *revision.write() += 1;
                                    }
                                })
                                .text("Save"),
                        ),
                ))
                .into_element();
        }

        page = page
            .child(section_header("Main menu banner"))
            .child(settings_row(
                IconType::Announcement01,
                "Announcement",
                banner_status.read().clone().unwrap_or_else(|| {
                    "Shown at the top of everyone's in-game main menu until they dismiss it.".to_string()
                }),
                rect()
                    .horizontal()
                    .spacing(8.)
                    .cross_align(Alignment::Center)
                    .child(
                        TextInput::new(banner)
                            .placeholder("Live tonight at 8 PM on TikTok")
                            .width(Size::px(240.)),
                    )
                    .child(
                        Button::new()
                            .primary()
                            .small()
                            .on_press(move |_| announce(key, banner, banner_status, false))
                            .text("Post"),
                    )
                    .child(
                        Button::new()
                            .secondary()
                            .small()
                            .on_press(move |_| announce(key, banner, banner_status, true))
                            .text("Take down"),
                    ),
            ))
            .child(section_header("Golem's Client users"));

        match stats.read().clone() {
            None => {
                page = page.child(note("Loading..."));
            }
            Some(Err(error)) => {
                page = page.child(note(error));
            }
            Some(Ok(stats)) => {
                page = page
                    .child(
                        rect()
                            .horizontal()
                            .width(Size::fill())
                            .spacing(8.)
                            .child(stat_card("Total users", stats.total))
                            .child(stat_card("In the launcher", stats.in_launcher))
                            .child(stat_card("Playing", stats.playing))
                            .child(stat_card("Active today", stats.active_today))
                            .child(stat_card("New today", stats.new_today)),
                    )
                    .child(section_header("Everyone"))
                    .children(stats.users.into_iter().map(|user| user_row(user).into_element()));
            }
        }

        page.child(
            rect()
                .padding(Gaps::new(16., 0., 0., 0.))
                .child(
                    Button::new()
                        .secondary()
                        .small()
                        .on_press(move |_| {
                            let _ = save_owner_key("");
                            key.set(String::new());
                            stats.set(None);
                            has_key.set(false);
                        })
                        .text("Forget owner key"),
                ),
        )
        .into_element()
    }
}

/// Posts the text in the banner field (or takes the banner down), then says how it went.
fn announce(
    key: State<String>,
    mut banner: State<String>,
    mut status: State<Option<String>>,
    take_down: bool,
) {
    let key = key.peek().trim().to_string();
    let text = if take_down {
        String::new()
    } else {
        banner.peek().trim().to_string()
    };
    if !take_down && text.is_empty() {
        return;
    }
    status.set(Some("Sending...".to_string()));
    spawn(async move {
        let sent = text.clone();
        let result = crate::launcher::off_ui(async move {
            match crate::launcher::state() {
                Ok(state) => post_announcement(&state, &key, &sent).await,
                Err(_) => Err("The launcher is still starting.".to_string()),
            }
        })
        .await;
        match result {
            Ok(()) if text.is_empty() => status.set(Some("Taken down.".to_string())),
            Ok(()) => {
                status.set(Some(format!("Posted: \"{text}\"")));
                banner.set(String::new());
            }
            Err(error) => status.set(Some(error)),
        }
    });
}

fn note(text: impl Into<String>) -> impl IntoElement {
    label()
        .text(text.into())
        .font_size(text_size(13.))
        .color(colors::fg_secondary())
}

fn stat_card(title: &'static str, value: u64) -> impl IntoElement {
    rect()
        .vertical()
        .width(Size::flex(1.0))
        .spacing(4.)
        .padding(Gaps::new_symmetric(12., 14.))
        .background(colors::card())
        .corner_radius(rounded(8.))
        .border(border_all_color(1., colors::component_border()))
        .child(
            label()
                .text(value.to_string())
                .font_size(text_size(22.))
                .font_weight(FontWeight::SEMI_BOLD)
                .font_family(theme::mono_font())
                .color(colors::fg_strong()),
        )
        .child(
            label()
                .text(title)
                .font_size(text_size(11.))
                .color(colors::fg_secondary()),
        )
}

fn user_row(user: StatsUser) -> impl IntoElement {
    let name = user.name.clone().unwrap_or_else(|| "Unknown".to_string());
    let (status, live) = match user.state.as_deref() {
        Some("playing") => (
            match (&user.instance, &user.mc) {
                (Some(instance), Some(mc)) => format!("Playing {instance} ({mc})"),
                (Some(instance), None) => format!("Playing {instance}"),
                _ => "Playing".to_string(),
            },
            true,
        ),
        Some(_) => ("In the launcher".to_string(), true),
        None => (format!("Last seen {}", ago(user.last_seen)), false),
    };
    let details = format!(
        "{}first seen {}",
        user.version
            .as_deref()
            .map(|version| format!("v{version} · "))
            .unwrap_or_default(),
        ago(user.first_seen)
    );

    rect()
        .horizontal()
        .width(Size::fill())
        .spacing(12.)
        .cross_align(Alignment::Center)
        .padding(Gaps::new_symmetric(8., 12.))
        .background(colors::card())
        .corner_radius(rounded(8.))
        .child(Avatar::new(user.uuid.clone()).size(28.))
        .child(
            rect()
                .vertical()
                .width(Size::flex(1.0))
                .spacing(2.)
                .child(
                    label()
                        .text(name)
                        .font_size(text_size(14.))
                        .font_weight(FontWeight::MEDIUM)
                        .color(colors::fg_primary()),
                )
                .child(
                    label()
                        .text(details)
                        .font_size(text_size(11.))
                        .color(colors::fg_secondary()),
                ),
        )
        .child(
            label()
                .text(status)
                .font_size(text_size(12.))
                .color(if live {
                    colors::success()
                } else {
                    colors::fg_secondary()
                }),
        )
}

/// "just now", "5 min ago", "3 h ago", "2 days ago"
fn ago(unix_seconds: i64) -> String {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(unix_seconds);
    let seconds = (now - unix_seconds).max(0);
    match seconds {
        0..60 => "just now".to_string(),
        60..3600 => format!("{} min ago", seconds / 60),
        3600..86400 => format!("{} h ago", seconds / 3600),
        _ => {
            let days = seconds / 86400;
            if days == 1 {
                "1 day ago".to_string()
            } else {
                format!("{days} days ago")
            }
        }
    }
}
