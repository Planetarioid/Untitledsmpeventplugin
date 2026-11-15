package com.untitledeventmanager.listener;

import com.untitledeventmanager.service.EventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ParticipantListener implements Listener {
    private final EventService events;

    public ParticipantListener(EventService events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        // Vanilla owns normal drops. The plugin's death handler only clears the Ender Chest
        // and unregisters the eliminated participant.
        events.onParticipantDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        events.onPlayerJoin(event.getPlayer());
    }
}
