package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.UUID;

public class BanMenuListener implements Listener {

    private final LimonBanGUI plugin;
    private final BanManager banManager;
    private final BanService banService;

    public BanMenuListener(LimonBanGUI plugin, BanManager banManager, BanService banService) {
        this.plugin = plugin;
        this.banManager = banManager;
        this.banService = banService;
    }

    /** shift + ПКМ по игроку в spectator: открыть полное меню. */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player viewer = event.getPlayer();
        if (viewer.getGameMode() != GameMode.SPECTATOR) return;
        if (!viewer.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!viewer.hasPermission("limonban.admin")) return;

        event.setCancelled(true);
        openMain(viewer, target);
    }

    /** shift + ЛКМ по игроку в spectator: сразу вызвать на проверку, без меню. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        Player viewer = event.getPlayer();
        if (viewer.getGameMode() != GameMode.SPECTATOR) return;
        if (!viewer.isSneaking()) return;
        if (!viewer.hasPermission("limonban.admin")) return;

        double range = plugin.getConfig().getDouble("quick-target-range", 5.0);
        RayTraceResult trace = viewer.getWorld().rayTraceEntities(
                viewer.getEyeLocation(), viewer.getEyeLocation().getDirection(), range,
                entity -> entity instanceof Player p && !p.equals(viewer));
        if (trace == null) return;
        Entity hit = trace.getHitEntity();
        if (!(hit instanceof Player target)) return;

        plugin.getReviewManager().startReview(viewer, target);
    }

    private void openMain(Player viewer, Player target) {
        double trust = plugin.getAntiCheatBridge().getViolationLevel(target);
        boolean inReview = plugin.getReviewManager().isInReview(target.getUniqueId());
        viewer.openInventory(Menus.buildMain(target, trust, inReview));
    }

    // подстраховка: спектатор физически не должен наносить урон, но на всякий случай
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
            case BAN_REASONS -> handleReasonClick(viewer, event.getSlot(), clicked, targetUuid, targetName, targetOnline);
        }
    }

    private void handleMainClick(Player viewer, int slot, UUID targetUuid, String targetName, Player targetOnline) {
        if (slot == Menus.SLOT_CLOSE) {
            viewer.closeInventory();
            return;
        }

        if (slot == Menus.SLOT_REVIEW) {
            if (targetOnline == null) {
                viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
                viewer.closeInventory();
                return;
            }
            plugin.getReviewManager().startReview(viewer, targetOnline);
            viewer.closeInventory();
            return;
        }

        if (slot == Menus.SLOT_END_REVIEW) {
            if (targetOnline == null) {
                viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
                viewer.closeInventory();
                return;
            }
            boolean ok = plugin.getReviewManager().endReview(targetOnline);
            viewer.sendMessage(ok
                    ? Component.text(targetName + " снят(а) с проверки.", NamedTextColor.GREEN)
                    : Component.text(targetName + " не был(а) на проверке.", NamedTextColor.GRAY));
            viewer.closeInventory();
            return;
        }

        if (slot == Menus.SLOT_BAN) {
            if (targetOnline == null) {
                viewer.sendMessage(Component.text("Игрок вышел с сервера.", NamedTextColor.RED));
                viewer.closeInventory();
                return;
            }
            viewer.openInventory(Menus.buildReasonsMenu(targetOnline, banService.loadReasons()));
        }
    }

    private void handleReasonClick(Player viewer, int slot, ItemStack clicked, UUID targetUuid, String targetName, Player targetOnline) {
        if (slot == 31) { // Назад
            if (targetOnline == null) {
                viewer.closeInventory();
                return;
            }
            openMain(viewer, targetOnline);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String key = meta.getPersistentDataContainer().get(BanKeys.CATEGORY_KEY, PersistentDataType.STRING);
        if (key == null) return;

        BanReason reason = banService.loadReasons().stream()
                .filter(r -> r.key().equals(key))
                .findFirst().orElse(null);
        if (reason == null) return;

        banService.executeBan(viewer, targetOnline, targetUuid, targetName, reason);
        viewer.closeInventory();
    }
}
