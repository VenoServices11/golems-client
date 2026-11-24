package dev.golemsclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golemsclient.badge.Badges;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The G badge in front of Golem's Client players' names in the tab list (as OneClient does it, see PolyPlus, GPL-3.0). */
@Mixin(value = PlayerTabOverlay.class, priority = 1500)
public class TabListBadgeMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void golemsclient$badge(GuiGraphics g, Font font, Component name, int x, int y, int color, Operation<Void> original,
                                    @Local PlayerInfo info) {
        int mark = Badges.tabMark(info);
        if (mark != 0) Badges.blitTab(g, x, y, mark);
        original.call(g, font, name, x + (mark != 0 ? Badges.TAB_ADVANCE : 0), y, color);
    }

    // the column is made wide enough for the badge too
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 0))
    private int golemsclient$badgeWidth(Font font, FormattedText text, Operation<Integer> original, @Local PlayerInfo info) {
        return original.call(font, text) + (golemsclient$marked(info) ? Badges.TAB_ADVANCE : 0);
    }

    @Unique
    private static boolean golemsclient$marked(PlayerInfo info) {
        return Badges.tabMark(info) != 0;
    }
}
