package io.github.mbnkwm.raidrace.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ContainerEvents {
    public static final Event<SetContentEvent> SET_CONTENT =
            EventFactory.createArrayBacked(SetContentEvent.class, (listeners) -> (menu, items) -> {
                for (SetContentEvent listener : listeners) {
                    listener.onSetContent(menu, items);
                }
            });
    public static final Event<CloseEvent> CLOSE =
            EventFactory.createArrayBacked(CloseEvent.class, (listeners) -> id -> {
                for (CloseEvent listener : listeners) {
                    listener.onContainerClosed(id);
                }
            });

    public interface CloseEvent {
        void onContainerClosed(int containerId);
    }

    public interface SetContentEvent {
        void onSetContent(@NonNull AbstractContainerMenu menu, @NonNull List<ItemStack> items);
    }
}
