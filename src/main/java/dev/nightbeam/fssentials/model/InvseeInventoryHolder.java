package dev.nightbeam.fssentials.model;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class InvseeInventoryHolder implements InventoryHolder {
    private final UUID targetUuid;

    public InvseeInventoryHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
