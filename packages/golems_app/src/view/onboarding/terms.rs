use freya::prelude::*;
use freya::router::RouterContext;

use crate::hooks::{has_migration_data, query_is_loading, use_migration, use_settings_snapshot};
use crate::routes::Route;

/// Golem's Client has no terms to accept, so this step only waits for the search for other
/// launchers' instances to finish, then moves straight on to importing or signing in.
#[derive(PartialEq)]
pub struct OnboardingTerms;

impl Component for OnboardingTerms {
    fn render(&self) -> impl IntoElement {
        let settings = use_settings_snapshot().settings;
        let migration_query = use_migration();

        let next = if settings.seen_onboarding {
            Some(Route::Home {})
        } else if query_is_loading(&migration_query) {
            None
        } else if has_migration_data(&migration_query) {
            Some(Route::OnboardingMigration {})
        } else {
            Some(Route::OnboardingAccount {})
        };

        if let Some(next) = next {
            let _ = RouterContext::get().replace(next);
        }

        rect().width(Size::fill()).height(Size::fill())
    }
}
