package com.untitledeventmanager.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Identifies a plugin menu without relying on a potentially-colliding inventory title. */
public final class MenuHolder implements InventoryHolder {
    private final UUID owner;
    private final String menuId;
    private Inventory inventory;

    public MenuHolder(UUID owner, String menuId) {
        this.owner = owner;
        this.menuId = menuId;
    }

    public UUID owner() { return owner; }
    public String menuId() { return menuId; }
    public void bind(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
