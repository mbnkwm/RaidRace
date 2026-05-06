package io.github.mbnkwm.raidrace.mixin;

import io.github.mbnkwm.raidrace.RaidRace;
import io.github.mbnkwm.raidrace.event.ContainerEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    public void receiveContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
        AbstractContainerMenu menu = Objects.requireNonNull(Minecraft.getInstance().player,
                "Player was somehow null when receiving the container contents").containerMenu;

        if (packet.containerId() == menu.containerId && packet.stateId() == menu.getStateId()) {
            try {
                ContainerEvents.SET_CONTENT.invoker().onSetContent(menu, packet.items());
            } catch (Throwable e) {
                RaidRace.LOGGER.error("Unhandled exception thrown from SetContentEvent listener!", e);
            }
        }
    }
}
