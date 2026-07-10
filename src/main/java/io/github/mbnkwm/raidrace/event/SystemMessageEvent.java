package io.github.mbnkwm.raidrace.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.chat.Component;

public interface SystemMessageEvent {
    Event<SystemMessageEvent> RECEIVED =
            EventFactory.createArrayBacked(SystemMessageEvent.class, (listeners) -> (component, isOverlay) -> {
                for (SystemMessageEvent listener : listeners) {
                    listener.onMessageReceived(component, isOverlay);
                }
            });

    void onMessageReceived(Component component, boolean isOverlay);
}
