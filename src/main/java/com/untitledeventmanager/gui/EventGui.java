package com.untitledeventmanager.gui;

import com.untitledeventmanager.UntitledEventManager;
import com.untitledeventmanager.service.EventService;
import com.untitledeventmanager.util.EventRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.*;

/** Secure, paginated inventory menus. Every action is revalidated at click time. */
public final class EventGui implements Listener {
    private static final String HOME = color("&8UntitledEventManager");
    private static final String KITS = color("&8Kit Library");
    private static final String KIT = color("&8Kit: ");
    private static final String PEOPLE = color("&8Participants");
    private static final String PERSON = color("&8Participant: ");
    private static final String SETUP = color("&8Event Kit Setup");
    private static final String ENDING = color("&8Ending Commands");
    private static final String COMMANDS = color("&8Commands: ");
    private static final String CONFIRM = color("&8Confirm action");
    private static final String ASSIGN = color("&8Assign participant kit");
    private static final String PREVIEW = color("&8Preview: ");
    private static final String GIVE = color("&8Give kit: ");
    private static final String LIMIT = "Set event limit";

    private final UntitledEventManager plugin;
    private final EventService events;
    private final Map<UUID, String> selectedKit = new HashMap<>();
    private final Map<UUID, UUID> selectedPlayer = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, String> confirmations = new HashMap<>();
    private final Set<UUID> choosingDefault = new HashSet<>();
    private final Map<UUID, UUID> choosingParticipantKit = new HashMap<>();
    private final Map<UUID, String> anvilActions = new HashMap<>();
    private final Map<UUID, Boolean> commandListType = new HashMap<>();

    public EventGui(UntitledEventManager plugin, EventService events) {
        this.plugin = plugin;
        this.events = events;
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private int pageSize() {
        return Math.max(1, Math.min(45, plugin.getConfig().getInt("gui.entries-per-page", 45)));
    }

    private Inventory menu(Player player, int size, String title, String id) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), id);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bind(inventory);
        return inventory;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(EventGui::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private void open(Player player, Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.openInventory(inventory);
        });
    }

    private void close(Player player) {
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
    }

    private boolean allowed(Player player, String permission) {
        if (plugin.permissions().has(player, permission)) return true;
        player.sendMessage(color("&cYou do not have permission to do that."));
        return false;
    }

    public void limitInput(Player player) {
        openAnvil(player, "capacity", LIMIT, "Enter a positive whole number");
    }

    private void openAnvil(Player player, String action, String title, String prompt) {
        anvilActions.put(player.getUniqueId(), action);
        Inventory inventory = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text(title));
        inventory.setItem(0, item(Material.PAPER, "&f" + prompt));
        open(player, inventory);
    }

    public void home(Player player) {
        Inventory inv = menu(player, 27, HOME, "home");
        inv.setItem(10, item(Material.ENDER_EYE, "&aEvent status", "&7Current: &f" + events.status(), "&7Click to toggle OPEN/CLOSED"));
        inv.setItem(11, item(Material.CHEST, "&eKit library", "&7Browse and manage saved kits"));
        inv.setItem(12, item(Material.PLAYER_HEAD, "&bParticipants", "&7Manage registered participants"));
        inv.setItem(13, item(Material.COMPARATOR, "&dKit assignment", "&7Default kit and random pool"));
        inv.setItem(14, item(Material.BEACON, "&6Set event spawn", "&7Save your current location"));
        inv.setItem(15, item(Material.RECOVERY_COMPASS, "&6Set lobby location", "&7Placeholder location only"));
        inv.setItem(16, item(Material.TNT, "&cEnd event", "&7Confirm event cleanup"));
        inv.setItem(17, item(Material.WRITABLE_BOOK, "&6Ending commands", "&7Manage per-player and global commands"));
        inv.setItem(25, item(Material.HOPPER, "&eSet player limit", "&7Current: " + events.capacity(), "&7Enter a number in the anvil"));
        inv.setItem(22, item(Material.BARRIER, "&cClose"));
        open(player, inv);
    }

    public void kitLibrary(Player player, int page) {
        List<String> names = sorted(events.kitNames());
        int total = Math.max(1, (names.size() + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, total - 1));
        pages.put(player.getUniqueId(), page);
        Inventory inv = menu(player, 54, KITS, "kits");
        int start = page * pageSize();
        for (int i = start; i < Math.min(names.size(), start + pageSize()); i++) {
            String name = names.get(i);
            inv.setItem(i - start, item(Material.BOOK, "&f" + name, "&7Left-click: manage", "&7Right-click: preview"));
        }
        inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + total));
        inv.setItem(53, item(Material.ARROW, "&eNext page"));
        inv.setItem(47, item(Material.WRITABLE_BOOK, "&aCreate kit", "&7Save your current inventory as a kit"));
        inv.setItem(51, item(Material.BARRIER, "&cBack"));
        open(player, inv);
    }

    private List<String> sorted(Collection<String> values) {
        List<String> result = new ArrayList<>(values);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public void kitMenu(Player player, String name) {
        if (!events.hasKit(name)) { kitLibrary(player, pages.getOrDefault(player.getUniqueId(), 0)); return; }
        selectedKit.put(player.getUniqueId(), name);
        Inventory inv = menu(player, 27, KIT + name, "kit-management");
        inv.setItem(10, item(Material.CHEST, "&aPreview kit"));
        inv.setItem(11, item(Material.DIAMOND, "&eGive kit", "&7Choose an online player"));
        inv.setItem(12, item(Material.LIME_DYE, "&bUpdate kit", "&7Replace with your current inventory"));
        inv.setItem(13, item(Material.EMERALD, "&aAdd to random pool"));
        inv.setItem(14, item(Material.REDSTONE, "&cRemove from random pool"));
        inv.setItem(15, item(Material.BARRIER, "&4Delete kit"));
        inv.setItem(22, item(Material.ARROW, "&cBack"));
        open(player, inv);
    }

    public void preview(Player player, String name) {
        if (!events.hasKit(name)) return;
        Inventory inv = menu(player, 54, PREVIEW + name, "kit-preview");
        ItemStack[] contents = events.kitContents(name);
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) inv.setItem(i < 9 ? 36 + i : i, contents[i].clone());
        }
        ItemStack[] armor = events.kitArmor(name);
        inv.setItem(45, cloneOrAir(armor.length > 0 ? armor[0] : null)); // boots
        inv.setItem(46, cloneOrAir(armor.length > 1 ? armor[1] : null)); // leggings
        inv.setItem(47, cloneOrAir(armor.length > 2 ? armor[2] : null)); // chestplate
        inv.setItem(48, cloneOrAir(armor.length > 3 ? armor[3] : null)); // helmet
        inv.setItem(50, cloneOrAir(events.kitOffhand(name)));
        inv.setItem(53, item(Material.BARRIER, "&cClose preview"));
        open(player, inv);
    }

    private ItemStack cloneOrAir(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    public void participants(Player player, int page) {
        List<Map.Entry<UUID, EventService.Participant>> list = new ArrayList<>(events.participantDetails().entrySet());
        int total = Math.max(1, (list.size() + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, total - 1));
        pages.put(player.getUniqueId(), page);
        Inventory inv = menu(player, 54, PEOPLE, "participants");
        for (int i = page * pageSize(); i < Math.min(list.size(), page * pageSize() + pageSize()); i++) {
            Map.Entry<UUID, EventService.Participant> entry = list.get(i);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            ItemStack head = item(Material.PLAYER_HEAD, "&f" + entry.getValue().name(), "&7Kit: &f" + entry.getValue().kit(), "&7Status: " + (online ? "&aOnline" : "&cOffline"), "&7Click to manage");
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offline);
                head.setItemMeta(meta);
            }
            inv.setItem(i - page * pageSize(), head);
        }
        inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + total));
        inv.setItem(53, item(Material.ARROW, "&eNext page"));
        inv.setItem(51, item(Material.BARRIER, "&cBack"));
        open(player, inv);
    }

    public void participantMenu(Player player, UUID id) {
        EventService.Participant participant = events.participantDetails().get(id);
        if (participant == null) { participants(player, 0); return; }
        selectedPlayer.put(player.getUniqueId(), id);
        Inventory inv = menu(player, 27, PERSON + participant.name(), "participant-management");
        inv.setItem(10, item(Material.BOOK, "&aView assigned kit", "&7" + participant.kit()));
        inv.setItem(12, item(Material.CHEST, "&eChange kit"));
        inv.setItem(14, item(Material.DIAMOND, "&bRe-give assigned kit"));
        inv.setItem(16, item(Material.BARRIER, "&cRemove participant"));
        inv.setItem(22, item(Material.ARROW, "&cBack"));
        open(player, inv);
    }

    private void participantKitSelector(Player player, UUID id, int page) {
        if (!events.participantDetails().containsKey(id)) { participants(player, 0); return; }
        choosingParticipantKit.put(player.getUniqueId(), id);
        List<String> kits = sorted(events.kitNames());
        int total = Math.max(1, (kits.size() + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, total - 1));
        pages.put(player.getUniqueId(), page);
        Inventory inv = menu(player, 54, ASSIGN, "participant-kit-selector");
        for (int i = page * pageSize(); i < Math.min(kits.size(), page * pageSize() + pageSize()); i++) {
            inv.setItem(i - page * pageSize(), item(Material.BOOK, "&f" + kits.get(i)));
        }
        inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + total));
        inv.setItem(53, item(Material.ARROW, "&eNext page"));
        inv.setItem(51, item(Material.BARRIER, "&cBack"));
        open(player, inv);
    }

    public void kitSetup(Player player) {
        Inventory inv = menu(player, 27, SETUP, "kit-setup");
        inv.setItem(10, item(Material.BOOK, "&aSet default kit", "&7Current: " + events.defaultKit()));
        inv.setItem(12, item(Material.CHEST, "&eManage random pool", "&7Add/remove kits in their kit menu"));
        inv.setItem(14, item(Material.LEVER, "&bToggle random assignment", "&7Currently: " + events.randomEnabled()));
        inv.setItem(16, item(Material.PAPER, "&fCurrent settings", "&7Default: " + events.defaultKit(), "&7Pool: " + String.join(", ", events.randomPool())));
        inv.setItem(22, item(Material.ARROW, "&cBack"));
        open(player, inv);
    }

    private void endCommandsMenu(Player player, boolean perPlayer, int page) {
        commandListType.put(player.getUniqueId(), perPlayer);
        List<String> commands = events.endCommands(perPlayer);
        int total = Math.max(1, (commands.size() + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, total - 1));
        pages.put(player.getUniqueId(), page);
        Inventory inv = menu(player, 54, COMMANDS + (perPlayer ? "Per-player" : "Global"), "end-command-list");
        for (int i = page * pageSize(); i < Math.min(commands.size(), page * pageSize() + pageSize()); i++) {
            inv.setItem(i - page * pageSize(), item(Material.PAPER, "&f#" + i, "&7" + commands.get(i), "&cClick to remove"));
        }
        inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(47, item(Material.WRITABLE_BOOK, "&aAdd command"));
        inv.setItem(49, item(Material.COMPARATOR, "&eSwitch: " + (perPlayer ? "Global" : "Per-player")));
        inv.setItem(51, item(Material.BARRIER, "&cBack"));
        inv.setItem(53, item(Material.ARROW, "&eNext page"));
        open(player, inv);
    }

    private void endingCommands(Player player) {
        Inventory inv = menu(player, 27, ENDING, "ending-commands");
        inv.setItem(11, item(Material.PLAYER_HEAD, "&ePer-player commands", "&7Run once for each participant at ending"));
        inv.setItem(15, item(Material.COMMAND_BLOCK, "&6Global commands", "&7Run once for the ending"));
        inv.setItem(22, item(Material.ARROW, "&cBack"));
        open(player, inv);
    }

    private void confirmation(Player player, String action) {
        confirmations.put(player.getUniqueId(), action);
        Inventory inv = menu(player, 27, CONFIRM, "confirmation");
        inv.setItem(11, item(Material.LIME_WOOL, "&aConfirm", "&7" + action));
        inv.setItem(15, item(Material.RED_WOOL, "&cCancel"));
        open(player, inv);
    }

    public void confirmUpdate(Player player, String kit) {
        if (events.hasKit(kit)) confirmation(player, "update:" + kit);
        else player.sendMessage(color("&cThat kit does not exist."));
    }

    public void confirmDelete(Player player, String kit) {
        if (events.hasKit(kit)) confirmation(player, "delete:" + kit);
        else player.sendMessage(color("&cThat kit does not exist."));
    }


    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String action = anvilActions.get(uuid);
        if (action != null && event.getView().getTopInventory().getType() == InventoryType.ANVIL) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory() && event.getRawSlot() == 2) {
                String value = event.getView() instanceof AnvilView view ? view.getRenameText() : null;
                anvilActions.remove(uuid);
                handleAnvil(player, action, value);
            }
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder) || !holder.owner().equals(uuid)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        String menuId = holder.menuId();

        switch (menuId) {
            case "home" -> handleHome(player, slot);
            case "kits" -> handleKitLibrary(player, slot, event.isRightClick());
            case "kit-management" -> handleKitManagement(player, slot);
            case "participants" -> handleParticipants(player, slot);
            case "participant-management" -> handleParticipantMenu(player, slot);
            case "participant-kit-selector" -> handleParticipantKitSelector(player, slot);
            case "kit-setup" -> handleKitSetup(player, slot);
            case "confirmation" -> handleConfirmation(player, slot);
            case "kit-preview" -> { if (slot == 53) close(player); }
            case "give-kit-selector" -> handleGiveKit(player, slot);
            case "ending-commands" -> handleEndingMenu(player, slot);
            case "end-command-list" -> handleEndCommandList(player, slot);
        }
    }

    private void handleAnvil(Player player, String action, String value) {
        if (action.equals("capacity")) {
            if (!allowed(player, "eventmanager.playerlimit")) return;
            try {
                int amount = Integer.parseInt(value == null ? "" : value.trim());
                int maximum = plugin.getConfig().getInt("event.max-capacity", 500);
                if (amount < 1 || amount > maximum) throw new NumberFormatException();
                events.setCapacity(amount);
                player.sendMessage(color("&aEvent limit set to " + amount + ". Existing participants were not removed."));
            } catch (NumberFormatException ex) {
                player.sendMessage(color("&cEnter a positive whole number within the configured maximum."));
            }
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) home(player); });
        } else if (action.equals("create-kit")) {
            if (!allowed(player, "eventmanager.kits.create")) return;
            if (!EventRules.validKitName(value) || !events.createKit(value, player)) {
                player.sendMessage(color("&cInvalid kit name or a kit with that name already exists."));
            } else {
                player.sendMessage(color("&aKit " + value + " created from your current inventory."));
            }
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) kitLibrary(player, 0); });
        } else if (action.equals("end-player") || action.equals("end-global")) {
            if (!allowed(player, "eventmanager.endcommands")) return;
            if (!EventRules.validEndCommand(value)) {
                player.sendMessage(color("&cInvalid command. Use supported placeholders and keep it under 256 characters."));
            } else {
                events.addEndCommand(action.equals("end-player"), value);
                player.sendMessage(color("&aEnding command added."));
            }
            boolean perPlayer = action.equals("end-player");
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) endCommandsMenu(player, perPlayer, 0); });
        }
    }

    private void handleHome(Player player, int slot) {
        switch (slot) {
            case 10 -> { if (allowed(player, "eventmanager.status")) { events.setStatus(events.status() == EventService.Status.OPEN ? EventService.Status.CLOSED : EventService.Status.OPEN); home(player); } }
            case 11 -> { if (allowed(player, "eventmanager.kits")) kitLibrary(player, 0); }
            case 12 -> { if (allowed(player, "eventmanager.participants")) participants(player, 0); }
            case 13 -> { if (allowed(player, "eventmanager.settings")) kitSetup(player); }
            case 14 -> { if (allowed(player, "eventmanager.locations")) { events.setSpawn(player.getLocation()); player.sendMessage(color("&aEvent spawn saved.")); } }
            case 15 -> { if (allowed(player, "eventmanager.locations")) { events.setLobby(player.getLocation()); player.sendMessage(color("&aLobby placeholder location saved.")); } }
            case 16 -> { if (allowed(player, "eventmanager.status")) confirmation(player, "end-event"); }
            case 17 -> { if (allowed(player, "eventmanager.endcommands")) endingCommands(player); }
            case 22 -> close(player);
            case 25 -> { if (allowed(player, "eventmanager.playerlimit")) limitInput(player); }
        }
    }

    private void handleKitLibrary(Player player, int slot, boolean rightClick) {
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45) { kitLibrary(player, page - 1); return; }
        if (slot == 53) { kitLibrary(player, page + 1); return; }
        if (slot == 47) { openAnvil(player, "create-kit", "Create kit", "Enter kit name"); return; }
        if (slot == 51) { choosingDefault.remove(player.getUniqueId()); home(player); return; }
        if (slot < 0 || slot >= pageSize()) return;
        List<String> names = sorted(events.kitNames());
        int index = page * pageSize() + slot;
        if (index >= names.size()) return;
        String name = names.get(index);
        if (choosingDefault.contains(player.getUniqueId())) {
            if (allowed(player, "eventmanager.settings")) events.setDefaultKit(name);
            choosingDefault.remove(player.getUniqueId());
            kitSetup(player);
        } else if (rightClick) {
            if (allowed(player, "eventmanager.kits")) preview(player, name);
        } else if (allowed(player, "eventmanager.kits")) kitMenu(player, name);
    }

    private void handleKitManagement(Player player, int slot) {
        String name = selectedKit.get(player.getUniqueId());
        if (name == null || !events.hasKit(name)) { kitLibrary(player, 0); return; }
        switch (slot) {
            case 10 -> { if (allowed(player, "eventmanager.kits")) preview(player, name); }
            case 11 -> { if (allowed(player, "eventmanager.kits.give")) giveSelector(player, name, 0); }
            case 12 -> { if (allowed(player, "eventmanager.kits.update")) confirmation(player, "update:" + name); }
            case 13 -> { if (allowed(player, "eventmanager.settings")) { events.addRandomKit(name); player.sendMessage(color("&aAdded to random pool.")); kitMenu(player, name); } }
            case 14 -> { if (allowed(player, "eventmanager.settings")) { events.removeRandomKit(name); player.sendMessage(color("&aRemoved from random pool.")); kitMenu(player, name); } }
            case 15 -> { if (allowed(player, "eventmanager.kits.delete")) confirmation(player, "delete:" + name); }
            case 22 -> kitLibrary(player, pages.getOrDefault(player.getUniqueId(), 0));
        }
    }

    private void handleParticipants(Player player, int slot) {
        if (!allowed(player, "eventmanager.participants")) return;
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45) { participants(player, page - 1); return; }
        if (slot == 53) { participants(player, page + 1); return; }
        if (slot == 51) { home(player); return; }
        if (slot < 0 || slot >= pageSize()) return;
        List<UUID> ids = new ArrayList<>(events.participantDetails().keySet());
        int index = page * pageSize() + slot;
        if (index < ids.size()) participantMenu(player, ids.get(index));
    }

    private void handleParticipantMenu(Player player, int slot) {
        UUID id = selectedPlayer.get(player.getUniqueId());
        if (id == null || !events.participantDetails().containsKey(id)) { participants(player, 0); return; }
        EventService.Participant participant = events.participantDetails().get(id);
        switch (slot) {
            case 10 -> { if (allowed(player, "eventmanager.kits")) preview(player, participant.kit()); }
            case 12 -> { if (allowed(player, "eventmanager.participants")) participantKitSelector(player, id, 0); }
            case 14 -> {
                if (!allowed(player, "eventmanager.participants")) return;
                if (!events.reGiveParticipantKit(id)) player.sendMessage(color("&cThe participant must be online and registered."));
                else player.sendMessage(color("&aAssigned kit re-given."));
            }
            case 16 -> { if (allowed(player, "eventmanager.participants")) confirmation(player, "remove:" + id); }
            case 22 -> participants(player, 0);
        }
    }

    private void handleParticipantKitSelector(Player player, int slot) {
        UUID id = choosingParticipantKit.get(player.getUniqueId());
        if (id == null || !events.participantDetails().containsKey(id)) { participants(player, 0); return; }
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45) { participantKitSelector(player, id, page - 1); return; }
        if (slot == 53) { participantKitSelector(player, id, page + 1); return; }
        if (slot == 51) { choosingParticipantKit.remove(player.getUniqueId()); participantMenu(player, id); return; }
        if (slot < 0 || slot >= pageSize() || !allowed(player, "eventmanager.participants")) return;
        List<String> kits = sorted(events.kitNames());
        int index = page * pageSize() + slot;
        if (index >= kits.size()) return;
        String kit = kits.get(index);
        if (events.setParticipantKit(id, kit)) {
            Player target = Bukkit.getPlayer(id);
            if (target != null) events.reGiveParticipantKit(id);
            else player.sendMessage(color("&eKit assignment saved. It will be applied only if you re-give it later."));
        }
        choosingParticipantKit.remove(player.getUniqueId());
        participantMenu(player, id);
    }

    private void handleKitSetup(Player player, int slot) {
        if (slot == 10 && allowed(player, "eventmanager.settings")) {
            choosingDefault.add(player.getUniqueId());
            kitLibrary(player, 0);
        } else if (slot == 12 && allowed(player, "eventmanager.settings")) {
            kitLibrary(player, 0);
        } else if (slot == 14 && allowed(player, "eventmanager.settings")) {
            events.setRandomEnabled(!events.randomEnabled());
            kitSetup(player);
        } else if (slot == 22) home(player);
    }

    private void handleConfirmation(Player player, int slot) {
        String action = confirmations.get(player.getUniqueId());
        if (action == null) return;
        if (slot == 15) {
            confirmations.remove(player.getUniqueId());
            if (action.startsWith("delete:") || action.startsWith("update:")) kitMenu(player, action.substring(action.indexOf(':') + 1));
            else if (action.startsWith("remove:")) participantMenu(player, UUID.fromString(action.substring(7)));
            else if (action.startsWith("remove-end:")) {
                String[] parts = action.split(":");
                endCommandsMenu(player, Boolean.parseBoolean(parts[1]), pages.getOrDefault(player.getUniqueId(), 0));
            } else home(player);
            return;
        }
        if (slot != 11) return;
        confirmations.remove(player.getUniqueId());
        if (action.equals("end-event")) {
            if (allowed(player, "eventmanager.status")) { events.endEvent(); player.sendMessage(color("&cEvent ending cleanup has been processed.")); }
            close(player);
        } else if (action.startsWith("delete:")) {
            String name = action.substring(7);
            if (allowed(player, "eventmanager.kits.delete") && events.deleteKit(name)) player.sendMessage(color("&aKit deleted."));
            else player.sendMessage(color("&cKit could not be deleted. It may be assigned or configured."));
            kitLibrary(player, 0);
        } else if (action.startsWith("update:")) {
            String name = action.substring(7);
            if (allowed(player, "eventmanager.kits.update") && events.updateKit(name, player)) player.sendMessage(color("&aKit updated."));
            kitMenu(player, name);
        } else if (action.startsWith("remove:")) {
            UUID id = UUID.fromString(action.substring(7));
            if (allowed(player, "eventmanager.participants")) events.removeParticipant(id);
            participants(player, 0);
        } else if (action.startsWith("remove-end:")) {
            String[] parts = action.split(":");
            boolean perPlayer = Boolean.parseBoolean(parts[1]);
            int index = Integer.parseInt(parts[2]);
            if (allowed(player, "eventmanager.endcommands")) events.removeEndCommand(perPlayer, index);
            endCommandsMenu(player, perPlayer, pages.getOrDefault(player.getUniqueId(), 0));
        }
    }

    private void giveSelector(Player player, String kit, int page) {
        if (!events.hasKit(kit)) { kitLibrary(player, 0); return; }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int total = Math.max(1, (online.size() + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, total - 1));
        pages.put(player.getUniqueId(), page);
        selectedKit.put(player.getUniqueId(), kit);
        Inventory inv = menu(player, 54, GIVE + kit, "give-kit-selector");
        for (int i = page * pageSize(); i < Math.min(online.size(), page * pageSize() + pageSize()); i++) {
            inv.setItem(i - page * pageSize(), item(Material.PLAYER_HEAD, "&f" + online.get(i).getName()));
        }
        inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(49, item(Material.BARRIER, "&cBack"));
        inv.setItem(53, item(Material.ARROW, "&eNext page"));
        open(player, inv);
    }

    private void handleGiveKit(Player player, int slot) {
        String kit = selectedKit.get(player.getUniqueId());
        if (kit == null || !events.hasKit(kit)) { kitLibrary(player, 0); return; }
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45) { giveSelector(player, kit, page - 1); return; }
        if (slot == 53) { giveSelector(player, kit, page + 1); return; }
        if (slot == 49) { kitMenu(player, kit); return; }
        if (slot < 0 || slot >= pageSize() || !allowed(player, "eventmanager.kits.give")) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int index = page * pageSize() + slot;
        if (index >= online.size()) return;
        Player target = online.get(index);
        if (events.giveKit(target, kit)) player.sendMessage(color("&aGave " + kit + " to " + target.getName() + "."));
        close(player);
    }

    private void handleEndingMenu(Player player, int slot) {
        if (!allowed(player, "eventmanager.endcommands")) return;
        if (slot == 11) endCommandsMenu(player, true, 0);
        else if (slot == 15) endCommandsMenu(player, false, 0);
        else if (slot == 22) home(player);
    }

    private void handleEndCommandList(Player player, int slot) {
        if (!allowed(player, "eventmanager.endcommands")) return;
        boolean perPlayer = commandListType.getOrDefault(player.getUniqueId(), false);
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45) { endCommandsMenu(player, perPlayer, page - 1); return; }
        if (slot == 53) { endCommandsMenu(player, perPlayer, page + 1); return; }
        if (slot == 47) {
            openAnvil(player, perPlayer ? "end-player" : "end-global", "Add ending command", "Enter console command");
            return;
        }
        if (slot == 49) { endCommandsMenu(player, !perPlayer, 0); return; }
        if (slot == 51) { endingCommands(player); return; }
        if (slot < 0 || slot >= pageSize()) return;
        int index = page * pageSize() + slot;
        if (index < events.endCommands(perPlayer).size()) confirmation(player, "remove-end:" + perPlayer + ":" + index);
    }

    private boolean isManaged(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                && holder.owner().equals(player.getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isManaged(player)) event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && anvilActions.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isManaged(event.getPlayer()) || anvilActions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isManaged(event.getPlayer()) || anvilActions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() == InventoryType.ANVIL) anvilActions.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selectedKit.remove(id);
        selectedPlayer.remove(id);
        pages.remove(id);
        confirmations.remove(id);
        choosingDefault.remove(id);
        choosingParticipantKit.remove(id);
        anvilActions.remove(id);
        commandListType.remove(id);
    }
}
