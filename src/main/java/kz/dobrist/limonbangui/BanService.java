package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanService {

    private final LimonBanGUI plugin;
    private final BanManager banManager;

    public BanService(LimonBanGUI plugin, BanManager banManager) {
        this.plugin = plugin;
        this.banManager = banManager;
    }

    public List<BanReason> loadReasons() {
        List<BanReason> result = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("ban-reasons")) {
            String key = String.valueOf(raw.get("key"));
            String label = String.valueOf(raw.get("label"));
            boolean permanent = raw.get("permanent") != null && (boolean) raw.get("permanent");
            int days = raw.get("days") != null ? ((Number) raw.get("days")).intValue() : 0;
            boolean dramatic = raw.get("dramatic") != null && (boolean) raw.get("dramatic");
            result.add(new BanReason(key, label, days, permanent, dramatic));
        }
        return result;
    }

    /**
     * Исполняет бан. Если reason.dramatic() — сначала подкидывает игрока на 5 блоков
     * и роняет все его ресурсы на месте, и только потом (с небольшой задержкой) кикает.
     * Иначе — кикает сразу, ресурсы не трогаются.
     */
    public void executeBan(Player admin, Player targetOnline, UUID targetUuid, String targetName, BanReason reason) {
        if (reason.permanent()) {
            banManager.banPermanent(targetUuid, targetName, reason.label());
        } else {
            banManager.ban(targetUuid, targetName, reason.label(), reason.days());
        }

        String telegram = plugin.getConfig().getString("telegram-contact", "@MIlan4ck3456");
        String remaining = reason.permanent() ? "навсегда" : reason.days() + " дн.";
        Component banScreen = BanMessages.banScreen(reason.label(), remaining, telegram);

        if (targetOnline != null) {
            plugin.getReviewManager().endReview(targetOnline); // на случай если банили прямо с проверки
            if (reason.dramatic()) {
                launchAndDropLoot(targetOnline);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (targetOnline.isOnline()) targetOnline.kick(banScreen);
                }, 15L);
            } else {
                targetOnline.kick(banScreen);
            }
        }

        Bukkit.broadcast(Component.text("[LimonBanGUI] " + targetName + " забанен(а)"
                        + (reason.permanent() ? " навсегда" : " на " + reason.days() + " дней")
                        + " (" + reason.label() + ") — " + admin.getName(),
                NamedTextColor.RED));
    }

    private void launchAndDropLoot(Player target) {
        target.setVelocity(new Vector(0, 1.6, 0)); // ~5 блоков вверх

        Location dropLoc = target.getLocation();
        ItemStack[] contents = target.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, item);
            }
        }
        target.getInventory().clear();
    }
}
