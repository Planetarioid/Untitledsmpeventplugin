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
