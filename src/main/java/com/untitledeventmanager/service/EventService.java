package com.untitledeventmanager.service;

import com.untitledeventmanager.UntitledEventManager;
import com.untitledeventmanager.storage.EventStorage;
import com.untitledeventmanager.util.EventRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public final class EventService {
    public enum Status { OPEN, CLOSED, ENDED }
    public record Participant(String name, String kit) {}
    private final UntitledEventManager plugin;
    private final EventStorage storage;

    public EventService(UntitledEventManager plugin, EventStorage storage) {
        this.plugin = plugin; this.storage = storage;
    }
    private org.bukkit.configuration.file.YamlConfiguration data() { return storage.data(); }
    public Status status() {
        try { return Status.valueOf(data().getString("event.status", "CLOSED").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return Status.CLOSED; }
    }
    public int capacity() { return data().getInt("event.capacity", 50); }
    public int participantCount() { ConfigurationSection s = data().getConfigurationSection("event.participants"); return s == null ? 0 : s.getKeys(false).size(); }
    public boolean isParticipant(UUID uuid) { return data().contains("event.participants." + uuid); }
    public void setStatus(Status status) {
        if (status == Status.ENDED) { endEvent(); return; }
        data().set("event.status", status.name()); storage.save();
    }
    public void setSpawn(Location loc) { writeLocation("event.spawn", loc); storage.save(); }
    public void setLobby(Location loc) { writeLocation("event.lobby", loc); storage.save(); }
    private void writeLocation(String path, Location loc) {
        data().set(path + ".world", loc.getWorld().getName()); data().set(path + ".x", loc.getX()); data().set(path + ".y", loc.getY());
        data().set(path + ".z", loc.getZ()); data().set(path + ".yaw", loc.getYaw()); data().set(path + ".pitch", loc.getPitch());
    }
    private Location readLocation(String path) {
        String worldName = data().getString(path + ".world"); World world = worldName == null ? null : Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, data().getDouble(path + ".x"), data().getDouble(path + ".y"), data().getDouble(path + ".z"), (float)data().getDouble(path + ".yaw"), (float)data().getDouble(path + ".pitch"));
    }
    public Location lobby() { return readLocation("event.lobby"); }

    public String join(Player player) {
        if (status() != Status.OPEN) return "The event is not open.";
        if (isParticipant(player.getUniqueId())) return "You are already registered for this event.";
        if (participantCount() >= capacity()) return "The event is full.";
        Location spawn = readLocation("event.spawn"); if (spawn == null) return "The event spawn has not been configured.";
        String kit = chooseKit(); if (kit == null) return "No valid event kit is configured.";
        String base = "event.participants." + player.getUniqueId();
        data().set(base + ".name", player.getName());
        data().set(base + ".kit", kit);
        // Persist delivery intent before changing the live inventory. If the server stops
        // between these operations, the next login completes the delivery exactly once.
        data().set(base + ".kit-pending", true);
        data().set(base + ".teleport-pending", true);
        storage.save();
        if (!giveKit(player, kit)) {
            data().set(base, null);
            storage.save();
            return "The assigned kit could not be loaded.";
        }
        data().set(base + ".kit-pending", false);
        storage.save();
        if (!player.teleport(spawn)) {
            plugin.getLogger().warning("Could not teleport participant " + player.getName() + " to the configured event spawn. The teleport will be retried on their next login.");
            return "You joined with kit " + kit + ", but the event spawn teleport failed. It will be retried on your next login.";
        }
        data().set(base + ".teleport-pending", false);
        storage.save();
        return "You joined the event with kit " + kit + ".";
    }
    private String chooseKit() {
        String def = defaultKit(); List<String> pool = randomPool();
        if (!randomEnabled() || pool.isEmpty()) return hasKit(def) ? def : null;
        List<String> bag = new ArrayList<>(data().getStringList("event.random-bag"));
        bag.removeIf(k -> !pool.contains(k) || !hasKit(k));
        if (bag.isEmpty()) { bag.addAll(pool.stream().filter(this::hasKit).toList()); Collections.shuffle(bag); }
        if (bag.isEmpty()) return hasKit(def) ? def : null;
        String selected = bag.remove(0); data().set("event.random-bag", bag); storage.save(); return selected;
    }
    public boolean createKit(String name, Player source) {
        if (!validName(name) || kitNames().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) return false;
        saveKit("kits." + name, source.getInventory());
        storage.save();
        return true;
    }
    public boolean updateKit(String name, Player source) {
        if (!hasKit(name)) return false; saveKit("kits." + name, source.getInventory()); storage.save(); return true;
    }
    private boolean validName(String name) { return EventRules.validKitName(name); }
    private void saveKit(String path, PlayerInventory inv) {
        data().set(path + ".contents", inv.getStorageContents()); data().set(path + ".armor", inv.getArmorContents()); data().set(path + ".offhand", inv.getItemInOffHand());
    }
    public boolean hasKit(String name) { return validName(name) && data().contains("kits." + name); }
    public Set<String> kitNames() { ConfigurationSection s = data().getConfigurationSection("kits"); return s == null ? Collections.emptySet() : new TreeSet<>(s.getKeys(false)); }
    public boolean deleteKit(String name) {
        if (!hasKit(name) || name.equalsIgnoreCase(defaultKit()) || randomPool().contains(name) || participantDetails().values().stream().anyMatch(p -> p.kit().equalsIgnoreCase(name))) return false;
        data().set("kits." + name, null); storage.save(); return true;
    }
    public boolean setDefaultKit(String name) { if (!hasKit(name)) return false; data().set("event.default-kit", name); storage.save(); return true; }
    public String defaultKit() { return data().getString("event.default-kit", ""); }
    public boolean randomEnabled() { return data().getBoolean("event.random-enabled", false); }
    public void setRandomEnabled(boolean value) { data().set("event.random-enabled", value); storage.save(); }
    public List<String> randomPool() { return data().getStringList("event.random-pool"); }
    public void addRandomKit(String name) { if (hasKit(name)) { List<String> p = randomPool(); if (!p.contains(name)) p.add(name); data().set("event.random-pool", p); data().set("event.random-bag", Collections.emptyList()); storage.save(); } }
    public void removeRandomKit(String name) { List<String> p = randomPool(); p.remove(name); data().set("event.random-pool", p); data().set("event.random-bag", Collections.emptyList()); storage.save(); }
    public boolean giveKit(Player target, String name) {
        if (!hasKit(name)) return false; ConfigurationSection kit = data().getConfigurationSection("kits." + name); if (kit == null) return false;
        target.getInventory().clear(); target.getInventory().setStorageContents(readItems(kit.getList("contents"), 36));
        target.getInventory().setArmorContents(readItems(kit.getList("armor"), 4));
        ItemStack offhand = kit.getItemStack("offhand"); target.getInventory().setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone()); return true;
    }
    public ItemStack[] kitContents(String name) { ConfigurationSection s = data().getConfigurationSection("kits." + name); return s == null ? new ItemStack[36] : readItems(s.getList("contents"), 36); }
    public ItemStack[] kitArmor(String name) { ConfigurationSection s = data().getConfigurationSection("kits." + name); return s == null ? new ItemStack[4] : readItems(s.getList("armor"), 4); }
    public ItemStack kitOffhand(String name) { ConfigurationSection s = data().getConfigurationSection("kits." + name); ItemStack i = s == null ? null : s.getItemStack("offhand"); return i == null ? new ItemStack(Material.AIR) : i.clone(); }
    private ItemStack[] readItems(List<?> values, int size) {
        ItemStack[] result = new ItemStack[size]; if (values != null) for (int i=0;i<Math.min(size, values.size());i++) if (values.get(i) instanceof ItemStack item) result[i] = item.clone(); return result;
    }
    public void onParticipantDeath(Player player) {
        if (!isParticipant(player.getUniqueId())) return;
        // Vanilla owns normal drops. The only extra death cleanup is the participant's Ender Chest.
        player.getEnderChest().clear(); data().set("event.participants." + player.getUniqueId(), null); storage.save();
    }
    public void endEvent() {
        if (status() == Status.ENDED && !data().getBoolean("event.ending", false)) return;
        if (!data().getBoolean("event.ending", false)) {
            List<String> snapshot = new ArrayList<>(); ConfigurationSection s = data().getConfigurationSection("event.participants");
            if (s != null) snapshot.addAll(s.getKeys(false));
            data().set("event.status", Status.ENDED.name()); data().set("event.ending-snapshot", snapshot);
            data().set("event.ending", true); data().set("event.end-global-done", false); data().set("event.end-player-done", new ArrayList<String>());
            storage.save();
        }
        processEnding();
    }
    public void recover() {
        if (data().getBoolean("event.ending", false)) processEnding();
        processPendingCleanup();
        for (Player player : Bukkit.getOnlinePlayers()) processPendingKitDelivery(player);
    }

    /** Runs at LOWEST join priority so queued cleanup and interrupted kit delivery happen early. */
    public void onPlayerJoin(Player player) {
        processPendingCleanupFor(player);
        processPendingKitDelivery(player);
    }

    private void processPendingCleanupFor(Player player) {
        String id = player.getUniqueId().toString();
        List<String> pending = data().getStringList("event.pending-cleanup");
        if (!pending.contains(id)) return;
        try {
            cleanupPlayer(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Pending cleanup failed for " + player.getName() + ": " + ex.getMessage());
            return;
        }
        pending.remove(id);
        data().set("event.pending-cleanup", pending);
        storage.save();
    }

    private void processPendingKitDelivery(Player player) {
        String base = "event.participants." + player.getUniqueId();
        if (!isParticipant(player.getUniqueId())) return;
        if (data().getBoolean(base + ".kit-pending", false)) {
            String kit = data().getString(base + ".kit", "");
            if (giveKit(player, kit)) {
                data().set(base + ".kit-pending", false);
                storage.save();
                plugin.getLogger().info("Completed interrupted kit delivery for " + player.getName() + ".");
            } else {
                plugin.getLogger().warning("Could not complete pending kit delivery for " + player.getName() + ".");
                return;
            }
        }
        if (data().getBoolean(base + ".teleport-pending", false)) {
            Location spawn = readLocation("event.spawn");
            if (spawn != null && player.teleport(spawn)) {
                data().set(base + ".teleport-pending", false);
                storage.save();
                plugin.getLogger().info("Completed interrupted event-spawn teleport for " + player.getName() + ".");
            } else {
                plugin.getLogger().warning("Could not complete pending event-spawn teleport for " + player.getName() + ".");
            }
        }
    }
    private void processEnding() {
        List<String> snapshot = data().getStringList("event.ending-snapshot");
        for (String id : snapshot) {
            UUID uuid; try { uuid = UUID.fromString(id); } catch (IllegalArgumentException ex) { continue; }
            String base = "event.participants." + id; String name = data().getString(base + ".name", id); Player p = Bukkit.getPlayer(uuid);
            if (p == null) { List<String> pending = data().getStringList("event.pending-cleanup"); if (!pending.contains(id)) pending.add(id); data().set("event.pending-cleanup", pending); }
            else cleanupPlayer(p);
            List<String> done = data().getStringList("event.end-player-done");
            if (!done.contains(id)) {
                // Mark before dispatch: arbitrary console commands cannot be made transactionally exactly-once.
                done.add(id); data().set("event.end-player-done", done); storage.save();
                runCommands(data().getStringList("event.end-commands.per-player"), name);
            }
            data().set(base, null); storage.save();
        }
        if (!data().getBoolean("event.end-global-done", false)) {
            data().set("event.end-global-done", true); storage.save(); runCommands(data().getStringList("event.end-commands.global"), "");
        }
        data().set("event.ending", false); storage.save();
    }
    private void runCommands(List<String> commands, String player) {
        Location lobby = lobby();
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            if (!EventRules.validEndCommand(raw)) {
                plugin.getLogger().warning("Skipped invalid ending command from saved data: " + raw);
                continue;
            }
            if (!player.isEmpty() && !EventRules.validPlayerName(player)) {
                plugin.getLogger().warning("Skipped per-player ending commands because the stored username is invalid: " + player);
                return;
            }
            if (player.isEmpty() && raw.contains("{player}")) {
                plugin.getLogger().warning("Skipped global ending command because it contains the per-player placeholder: " + raw);
                continue;
            }
            if (lobby == null && raw.contains("{lobby_")) {
                plugin.getLogger().warning("Skipped ending command because a lobby placeholder was used without a lobby location: " + raw);
                continue;
            }
            String command = raw.replace("{player}", player)
                    .replace("{lobby_world}", lobby == null ? "" : lobby.getWorld().getName())
                    .replace("{lobby_x}", lobby == null ? "" : Double.toString(lobby.getX()))
                    .replace("{lobby_y}", lobby == null ? "" : Double.toString(lobby.getY()))
                    .replace("{lobby_z}", lobby == null ? "" : Double.toString(lobby.getZ()))
                    .replace("{lobby_yaw}", lobby == null ? "" : Float.toString(lobby.getYaw()))
                    .replace("{lobby_pitch}", lobby == null ? "" : Float.toString(lobby.getPitch()));
            if (command.contains("{player}") || command.contains("{") || command.contains("}")) { plugin.getLogger().warning("Skipped end command with unresolved placeholder: " + raw); continue; }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ending command failed: " + ex.getClass().getSimpleName() + ": " + raw);
            }
        }
    }
    private void cleanupPlayer(Player p) { p.getInventory().clear(); p.getInventory().setArmorContents(new ItemStack[4]); p.getInventory().setItemInOffHand(new ItemStack(Material.AIR)); p.getEnderChest().clear(); }
    private void processPendingCleanup() {
        List<String> pending = data().getStringList("event.pending-cleanup"); boolean changed = false;
        for (String id : new ArrayList<>(pending)) {
            try {
                Player p = Bukkit.getPlayer(UUID.fromString(id));
                if (p != null) {
                    try { cleanupPlayer(p); pending.remove(id); changed = true; }
                    catch (RuntimeException ex) { plugin.getLogger().warning("Pending cleanup failed for " + id + ": " + ex.getMessage()); }
                }
            } catch (IllegalArgumentException ex) { pending.remove(id); changed = true; }
        }
        if (changed) { data().set("event.pending-cleanup", pending); storage.save(); }
    }
    public void setCapacity(int amount) {
        int maximum = plugin.getConfig().getInt("event.max-capacity", 500);
        if (!EventRules.validCapacity(amount, maximum)) throw new IllegalArgumentException("Capacity must be between 1 and " + maximum);
        data().set("event.capacity", amount);
        storage.save();
    }
    public Map<String,String> participants() { Map<String,String> out = new LinkedHashMap<>(); participantDetails().forEach((id,p)->out.put(id.toString(),p.name())); return out; }
    public Map<UUID,Participant> participantDetails() {
        Map<UUID,Participant> out = new LinkedHashMap<>(); ConfigurationSection s = data().getConfigurationSection("event.participants");
        if (s != null) for (String id : s.getKeys(false)) try { out.put(UUID.fromString(id), new Participant(s.getString(id + ".name", id), s.getString(id + ".kit", ""))); } catch (IllegalArgumentException ignored) {}
        return out;
    }
    public boolean setParticipantKit(UUID id, String kit) { if (!isParticipant(id) || !hasKit(kit)) return false; data().set("event.participants." + id + ".kit", kit); storage.save(); return true; }
    public boolean reGiveParticipantKit(UUID id) { Player p = Bukkit.getPlayer(id); EventService.Participant d = participantDetails().get(id); return p != null && d != null && giveKit(p, d.kit()); }
    public boolean removeParticipant(UUID id) {
        if (!isParticipant(id)) return false;
        Player player = Bukkit.getPlayer(id);
        if (player != null) cleanupPlayer(player);
        else { List<String> pending = data().getStringList("event.pending-cleanup"); if (!pending.contains(id.toString())) pending.add(id.toString()); data().set("event.pending-cleanup", pending); }
        data().set("event.participants." + id, null); storage.save(); return true;
    }
    public void addEndCommand(boolean perPlayer, String command) {
        if (!EventRules.validEndCommand(command)) throw new IllegalArgumentException("Invalid ending command");
        String path = perPlayer ? "event.end-commands.per-player" : "event.end-commands.global";
        List<String> list = data().getStringList(path);
        list.add(command);
        data().set(path, list);
        storage.save();
    }
    public void removeEndCommand(boolean perPlayer, int index) { String path = perPlayer ? "event.end-commands.per-player" : "event.end-commands.global"; List<String> list = data().getStringList(path); if (index >= 0 && index < list.size()) list.remove(index); data().set(path,list); storage.save(); }
    public List<String> endCommands(boolean perPlayer) { return data().getStringList(perPlayer ? "event.end-commands.per-player" : "event.end-commands.global"); }
}
