// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: only the emote playback the lobby uses (the run-in, being dragged), no cosmetics.
package dev.golemsclient.oc.mixin.client.cosmetics;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import dev.golemsclient.oc.client.bedrock.playback.BoneTransform;
import dev.golemsclient.oc.client.cosmetics.access.AvatarEmoteRenderAccess;
import dev.golemsclient.oc.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Collections;
import java.util.Map;


@Mixin(AvatarRenderState.class)
public class MixinAvatarRenderState
    implements AvatarEmoteRenderAccess
{
    @Unique
    private EmoteController golems$boundEmoteController;

    @Unique
    private Map<String, BoneTransform> golems$lastEmoteSample;

    @Override
    public EmoteController golems$boundEmoteController() {
        EmoteController controller = golems$boundEmoteController;
        if (controller == null) {
            controller = new EmoteController();
            golems$boundEmoteController = controller;
        }
        return controller;
    }

    @Override
    public void golems$bindEmoteController(EmoteController controller) {
        golems$boundEmoteController = controller;
    }

    @Override
    public Map<String, BoneTransform> golems$lastEmoteSample() {
        Map<String, BoneTransform> sample = golems$lastEmoteSample;
        return sample == null ? Collections.emptyMap() : sample;
    }

    @Override
    public void golems$setLastEmoteSample(Map<String, BoneTransform> sample) {
        golems$lastEmoteSample = sample == null ? Collections.emptyMap() : sample;
    }
}
