package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class MuteMenuListener implements Listener {

    private final MuteService muteService;

    public MuteMenuListener(MuteService muteService) {
        this.muteService = muteService;
    }

    /** Открыть меню выбора игрока — вызывается командой /limonban mute, не требует приближения к цели. */
    public void openPicker(Player viewer) {
        viewer.openInventory(MuteMenus.buildPicker(viewer));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MuteMenuHolder holder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Player viewer = (Player) event.getWhoClicked();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (holder.getType() == MuteMenuHolder.MenuType.PICKER) {
            String uuidStr = meta.getPersistentDataContainer().get(BanKeys.MUTE_TARGET_KEY, PersistentDataType.STRING);
            if (uuidStr == null) return;

            Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
            if (target == null) {
                viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
                return;
            }
            viewer.openInventory(MuteMenus.buildReasons(target, muteService.loadReasons()));
            return;
        }

        // REASONS
        if (event.getSlot() == MuteMenus.SLOT_BACK) {
            openPicker(viewer);
            return;
        }

        String key = meta.getPersistentDataContainer().get(BanKeys.CATEGORY_KEY, PersistentDataType.STRING);
        if (key == null) return;

        UUID targetUuid = holder.getTargetUuid();
        String targetName = holder.getTargetName();
        Player targetOnline = Bukkit.getPlayer(targetUuid);

        MuteReason reason = muteService.loadReasons().stream()
                .filter(r -> r.key().equals(key))
                .findFirst().orElse(null);
        if (reason == null) return;

        muteService.executeMute(viewer, targetOnline, targetUuid, targetName, reason);
        viewer.closeInventory();
    }
}
