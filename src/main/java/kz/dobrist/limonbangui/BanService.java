package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanService {

    // Длительность "красивого" бана-античита: плавный подъём + поштучный дроп ресурсов
    private static final int DRAMATIC_DURATION_TICKS = 100; // 5 секунд

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
     * Исполняет бан. Если reason.dramatic() — игрок плавно взлетает (левитация + частицы),
     * в течение 5 секунд по одному роняет все свои ресурсы, кик — в конце, в воздухе.
     * Иначе — кикает сразу, ресурсы не трогаются.
     * В обоих случаях в общий чат уходит публичное объявление без названия плагина.
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
                playDramaticBanAndKick(targetOnline, banScreen);
            } else {
                targetOnline.kick(banScreen);
            }
        }

        Bukkit.broadcast(BanMessages.publicBanAnnouncement(targetName, reason.label(), remaining));
    }

    /** Плавный подъём (левитация + частицы) + поштучный дроп инвентаря, кик в воздухе в конце. */
    private void playDramaticBanAndKick(Player target, Component banScreen) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, DRAMATIC_DURATION_TICKS + 20, 0, false, true, true));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);

        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!target.isOnline()) return;
            Location loc = target.getLocation();
            target.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.05);
            target.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.1, 0), 6, 0.3, 0.05, 0.3, 0.01);
        }, 0L, 4L);

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : target.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                items.add(it.clone());
            }
        }
        target.getInventory().clear();

        if (!items.isEmpty()) {
            int interval = Math.max(1, DRAMATIC_DURATION_TICKS / items.size());
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                long delay = (long) i * interval;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), item);
                    }
                }, delay);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            particleTask.cancel();
            if (target.isOnline()) {
                target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                target.kick(banScreen); // кикаем прямо в воздухе, в момент "взрыва"
            }
        }, DRAMATIC_DURATION_TICKS);
    }
}
