package com.untitledeventmanager;

import com.untitledeventmanager.command.EventCommand;
import com.untitledeventmanager.gui.EventGui;
import com.untitledeventmanager.listener.ParticipantListener;
import com.untitledeventmanager.permission.PermissionService;
import com.untitledeventmanager.service.EventService;
import com.untitledeventmanager.storage.EventStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class UntitledEventManager extends JavaPlugin {
    private EventStorage storage;
    private EventService eventService;
    private PermissionService permissions;
    @Override public void onEnable() {
        saveDefaultConfig();
        try { storage = new EventStorage(this); eventService = new EventService(this, storage); permissions = new PermissionService(this); }
        catch (RuntimeException ex) { getLogger().severe("Could not initialize persistent data: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        EventGui gui = new EventGui(this, eventService);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new ParticipantListener(eventService), this);
        EventCommand command = new EventCommand(this, eventService, gui);
        if (getCommand("event") == null) { getLogger().severe("Command 'event' missing from plugin.yml."); getServer().getPluginManager().disablePlugin(this); return; }
        getCommand("event").setExecutor(command); getCommand("event").setTabCompleter(command);
        eventService.recover();
        getLogger().info("UntitledEventManager enabled for Paper 1.21.11 / Java 21.");
    }
    @Override public void onDisable() { if (storage != null) storage.save(); }
    public EventService events() { return eventService; }
    public PermissionService permissions() { return permissions; }
}
