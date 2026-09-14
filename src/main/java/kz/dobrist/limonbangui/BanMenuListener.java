package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class BanMenuListener implements Listener {

    private final LimonBanGUI plugin;
    private final BanManager banManager;

    public BanMenuListener(LimonBanGUI plugin, BanManager banManager) {
        this.plugin = plugin;
        this.banManager = banManager;
    }

    /** Триггер: в spectator, shift + ПКМ по игроку, есть право. */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player viewer = event.getPlayer();
        if (viewer.getGameMode() != GameMode.SPECTATOR) return;
        if (!viewer.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!viewer.hasPermission("limonban.admin")) return;

        event.setCancelled(true);
        double trust = plugin.getAntiCheatBridge().getViolationLevel(target);
        viewer.openInventory(Menus.buildMain(target, trust));
    }

    // на всякий случай — блокируем урон от спектатора (должно и так игнориться ванилой)
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BanMenuHolder holder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Player viewer = (Player) event.getWhoClicked();
        UUID targetUuid = holder.getTargetUuid();
        String targetName = holder.getTargetName();
        Player targetOnline = Bukkit.getPlayer(targetUuid);

        switch (holder.getType()) {
            case MAIN -> handleMainClick(viewer, event.getSlot(), targetUuid, targetName, targetOnline);
            case ANTICHEAT_SUB -> handleAnticheatClick(viewer, event.getSlot(), clicked, targetUuid, targetName, targetOnline);
        }
    }

    private void handleMainClick(Player viewer, int slot, UUID targetUuid, String targetName, Player targetOnline) {
        if (slot == Menus.SLOT_CLOSE) {
            viewer.closeInventory();
        } else if (slot == Menus.SLOT_REVIEW) {
            viewer.closeInventory();
            callForReview(viewer, targetOnline, targetName);
        } else if (slot == Menus.SLOT_ROOM) {
            viewer.closeInventory();
            sendToCheckRoom(viewer, targetOnline, targetName);
        } else if (slot == Menus.SLOT_ANTICHEAT) {
            if (targetOnline == null) {
                viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
                viewer.closeInventory();
                return;
            }
            viewer.openInventory(Menus.buildAnticheatSub(targetOnline));
        }
    }

    private void handleAnticheatClick(Player viewer, int slot, ItemStack clicked, UUID targetUuid, String targetName, Player targetOnline) {
        if (slot == 22) { // Назад
            if (targetOnline == null) {
                viewer.closeInventory();
                return;
            }
            double trust = plugin.getAntiCheatBridge().getViolationLevel(targetOnline);
            viewer.openInventory(Menus.buildMain(targetOnline, trust));
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String category = meta.getPersistentDataContainer().get(BanKeys.CATEGORY_KEY, PersistentDataType.STRING);
        if (category == null) return;

        int days = plugin.getConfig().getInt("ban-days", 25);
        String reason = "Читы (п." + category + ")";
        banManager.ban(targetUuid, targetName, reason, days);

        if (targetOnline != null) {
            targetOnline.kick(Component.text("Вы забанены.\nПричина: " + reason + "\nСрок: " + days + " дней", NamedTextColor.RED));
        }

        Bukkit.broadcast(Component.text("[LimonBanGUI] " + targetName + " забанен на " + days + " дней (" + reason + ") — "
                + viewer.getName(), NamedTextColor.RED));

        viewer.closeInventory();
    }

    private void sendToCheckRoom(Player viewer, Player targetOnline, String targetName) {
        if (targetOnline == null) {
            viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
            return;
        }
        boolean ok = plugin.getCheckRoomManager().sendToRoom(targetOnline);
        if (!ok) {
            viewer.sendMessage(Component.text("Комната проверки не настроена. Встань в нужном месте и выполни /limonban room set", NamedTextColor.RED));
            return;
        }
        Bukkit.broadcast(Component.text("[LimonBanGUI] " + targetName + " отправлен(а) в комнату проверки — "
                + viewer.getName(), NamedTextColor.LIGHT_PURPLE), "limonban.admin");
    }

    private void callForReview(Player viewer, Player targetOnline, String targetName) {
        // Если у вас уже есть /acprov review — можно просто продублировать его вызов:
        if (targetOnline != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "acprov review " + targetOnline.getName());
        }
        Bukkit.broadcast(Component.text("[LimonBanGUI] " + targetName + " отправлен(а) на проверку анти-читом ("
                + viewer.getName() + ")", NamedTextColor.YELLOW),
                "limonban.admin");
    }
}
