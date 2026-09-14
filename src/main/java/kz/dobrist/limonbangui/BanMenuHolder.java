package kz.dobrist.limonbangui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class BanMenuHolder implements InventoryHolder {

    public enum MenuType { MAIN, BAN_REASONS }

    private final MenuType type;
    private final UUID targetUuid;
    private final String targetName;
    private Inventory inventory;

    public BanMenuHolder(MenuType type, UUID targetUuid, String targetName) {
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public MenuType getType() {
        return type;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }
}
