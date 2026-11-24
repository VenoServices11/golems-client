package dev.golemsclient.lobby;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** Moves your player in the lobby (instead of the keyboard): forward while running in, jumping over steps. */
public final class LobbyInput extends ClientInput {
    /** Set by the menu: dragging turns you, the mouse is where you look (degrees). */
    public static float turnOffset;
    public static float lookYaw;
    public static float lookPitch;

    private boolean forward, jump, sprint;

    void walk(boolean forward, boolean jump, boolean sprint) {
        this.forward = forward;
        this.jump = jump;
        this.sprint = sprint;
    }

    @Override
    public void tick() {
        keyPresses = new Input(forward, false, false, false, jump, false, sprint);
        moveVector = forward ? new Vec2(0f, 1f) : Vec2.ZERO;
    }
}
