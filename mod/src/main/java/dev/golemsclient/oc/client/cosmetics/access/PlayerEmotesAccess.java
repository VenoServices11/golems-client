// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.cosmetics.access;

import dev.golemsclient.oc.client.emotes.playback.EmoteController;

public interface PlayerEmotesAccess {

    EmoteController golems$emoteController();
}
