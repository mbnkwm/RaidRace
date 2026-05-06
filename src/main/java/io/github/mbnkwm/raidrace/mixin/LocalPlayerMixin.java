package io.github.mbnkwm.raidrace.mixin;

import com.mojang.authlib.GameProfile;
import io.github.mbnkwm.raidrace.RaidRace;
import io.github.mbnkwm.raidrace.event.ContainerEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "clientSideCloseContainer", at = @At("HEAD"))
    public void onClose(CallbackInfo ci) {
        try {
            ContainerEvents.CLOSE.invoker().onContainerClosed(containerMenu.containerId);
        } catch (Throwable e) {
            RaidRace.LOGGER.error("Unhandled exception thrown from CloseEvent listener!", e);
        }
    }
}
