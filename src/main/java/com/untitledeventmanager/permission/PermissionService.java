package com.untitledeventmanager.permission;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** Optional bridge to the public LuckPerms 5.x API; Bukkit permission checks are the fallback. */
public final class PermissionService {
    private final JavaPlugin plugin;
    private Object playerAdapter;
    private Method permissionDataMethod;
    private Method checkPermissionMethod;
    private Method asBooleanMethod;

    public PermissionService(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().warning("LuckPerms not found; using Bukkit permissions (OP fallback).");
            return;
        }
        try {
            org.bukkit.plugin.Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPermsPlugin == null) throw new ClassNotFoundException("LuckPerms plugin disappeared during initialization");
            ClassLoader loader = luckPermsPlugin.getClass().getClassLoader();
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider", true, loader);
            Object api = providerClass.getMethod("get").invoke(null);
            Class<?> luckPermsApi = Class.forName("net.luckperms.api.LuckPerms", true, loader);
            Method adapterMethod = luckPermsApi.getMethod("getPlayerAdapter", Class.class);
            playerAdapter = adapterMethod.invoke(api, Player.class);
            Class<?> adapterApi = Class.forName("net.luckperms.api.platform.PlayerAdapter", true, loader);
            permissionDataMethod = adapterApi.getMethod("getPermissionData", Object.class);
            Class<?> cachedPermissionData = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData", true, loader);
            checkPermissionMethod = cachedPermissionData.getMethod("checkPermission", String.class);
            Class<?> tristate = Class.forName("net.luckperms.api.util.Tristate", true, loader);
            asBooleanMethod = tristate.getMethod("asBoolean");
            plugin.getLogger().info("LuckPerms 5.x public API connected.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            playerAdapter = null;
            plugin.getLogger().warning("LuckPerms is installed but its API could not be initialized; using Bukkit permissions. " + ex.getClass().getSimpleName());
        }
    }

    public boolean has(Player player, String node) {
        if (player.isOp()) return true;
        if (playerAdapter != null) {
            try {
                if (luckPermsCheck(player, node)) return true;
                // Treat the aggregate admin node as a parent even if a permission manager
                // has not imported plugin.yml child relationships into its own data.
                if (!node.equalsIgnoreCase("eventmanager.admin") && luckPermsCheck(player, "eventmanager.admin")) return true;
                return false;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().fine("LuckPerms permission query failed for " + player.getName() + ": " + ex.getClass().getSimpleName());
            }
        }
        return player.hasPermission(node) || (!node.equalsIgnoreCase("eventmanager.admin") && player.hasPermission("eventmanager.admin"));
    }

    private boolean luckPermsCheck(Player player, String node) throws ReflectiveOperationException {
        Object permissionData = permissionDataMethod.invoke(playerAdapter, player);
        Object tristate = checkPermissionMethod.invoke(permissionData, node);
        return (Boolean) asBooleanMethod.invoke(tristate);
    }

    public boolean has(org.bukkit.command.CommandSender sender, String node) {
        if (sender instanceof Player player) return has(player, node);
        return sender.isOp() || sender.hasPermission(node);
    }

    public boolean isConnected() { return playerAdapter != null; }
}
