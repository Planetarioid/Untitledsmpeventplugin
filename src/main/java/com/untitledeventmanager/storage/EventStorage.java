package com.untitledeventmanager.storage;

import com.untitledeventmanager.UntitledEventManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Durable YAML storage. A malformed existing file is never silently replaced with defaults. */
public final class EventStorage {
    private final UntitledEventManager plugin;
    private final File file;
    private YamlConfiguration data;
    public EventStorage(UntitledEventManager plugin) {
        this.plugin=plugin;
        if(!plugin.getDataFolder().exists()&&!plugin.getDataFolder().mkdirs())throw new IllegalStateException("Could not create plugin data directory");
        file=new File(plugin.getDataFolder(),"data.yml"); data=new YamlConfiguration();
        if(file.exists()) { try { data.load(file); } catch(IOException|InvalidConfigurationException ex) { throw new IllegalStateException("Existing data.yml could not be read; it has been left untouched",ex); } }
        migrateDefaults(); save();
    }
    private void migrateDefaults(){
        data.addDefault("event.status","CLOSED"); data.addDefault("event.capacity",50); data.addDefault("event.max-capacity",500);
        data.addDefault("event.participants",null); data.addDefault("event.pending-cleanup",null); data.addDefault("event.random-enabled",false);
        data.addDefault("event.random-pool",null); data.addDefault("event.random-bag",null); data.addDefault("event.default-kit", "");
        data.addDefault("event.ending",false); data.addDefault("event.ending-snapshot",null);
        data.addDefault("event.end-global-done",false); data.addDefault("event.end-player-done",null);
        data.addDefault("event.end-commands.per-player",null); data.addDefault("event.end-commands.global",null);
        data.options().copyDefaults(true);
    }
    public YamlConfiguration data(){return data;}
    public synchronized void save(){
        File temp=new File(plugin.getDataFolder(),"data.yml.tmp");
        try { data.save(temp); try { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(IOException unsupported){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);} }
        catch(IOException ex){plugin.getLogger().severe("Could not save data.yml: "+ex.getMessage());}
    }
}
