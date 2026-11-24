// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.cosmetics.access;

import dev.golemsclient.oc.client.bedrock.playback.BoneTransform;
import dev.golemsclient.oc.client.emotes.playback.EmoteController;
import java.util.Map;

public interface AvatarEmoteRenderAccess {

    EmoteController golems$boundEmoteController();

    void golems$bindEmoteController(EmoteController controller);

    Map<String, BoneTransform> golems$lastEmoteSample();

    void golems$setLastEmoteSample(Map<String, BoneTransform> sample);
}
