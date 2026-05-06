package io.github.mbnkwm.raidrace.mixin;

import io.github.mbnkwm.raidrace.RaidRace;
import io.github.mbnkwm.raidrace.event.ContainerEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
    @Shadow
    @Final
    protected T menu;

    @Inject(method = "onClose", at = @At("HEAD"))
    public void onClose(CallbackInfo ci) {
        try {
            ContainerEvents.CLOSE.invoker().onContainerClosed(this.menu.containerId);
        } catch (Throwable e) {
            RaidRace.LOGGER.error("Unhandled exception thrown from CloseEvent listener!", e);
        }
    }
}
