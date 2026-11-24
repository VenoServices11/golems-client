// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.access;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.CompletableFuture;


import com.mojang.authlib.yggdrasil.ProfileResult;


import net.minecraft.client.particle.ParticleEngine;


@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("missTime")
    void golems$setAttackDelay(int ticks);

    @Mutable
    @Accessor("user")
    void golems$setUser(User user);

    @Mutable
    @Accessor("profileFuture")
    void golems$setProfileFuture(CompletableFuture<ProfileResult> profileFuture);

    @Mutable
    @Accessor("userApiService")
    void golems$setUserApiService(UserApiService userApiService);

    @Mutable
    @Accessor("userPropertiesFuture")
    void golems$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> userPropertiesFuture);

    @Mutable
    @Accessor("profileKeyPairManager")
    void golems$setProfileKeyPairManager(ProfileKeyPairManager profileKeyPairManager);


    @Mutable
    @Accessor("particleEngine")
    void golems$setParticleEngine(ParticleEngine engine);

}
