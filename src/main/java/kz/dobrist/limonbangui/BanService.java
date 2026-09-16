package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BanService {

    // Параметры "красивого" бана-античита
    private static final double RISE_HEIGHT_BLOCKS = 5.0; // насколько медленно поднимается
    private static final int DURATION_TICKS = 100;         // за сколько тиков (5 секунд)

    private final LimonBanGUI plugin;
    private final BanManager banManager;

    /** Игроки, которых сейчас поднимаем на бан-анимации — полностью заблокированы для движения. */
    private final Set<UUID> animationLocked = new HashSet<>();

    public BanService(LimonBanGUI plugin, BanManager banManager) {
        this.plugin = plugin;
        this.banManager = banManager;
    }

    public boolean isAnimationLocked(UUID uuid) {
        return animationLocked.contains(uuid);
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
     * Исполняет бан. Банит и по UUID, и по IP игрока (если он сейчас онлайн и его
     * адрес доступен) — это ловит альты с того же устройства/сети.
     * Если reason.dramatic() — игрок медленно и плавно поднимается 5 секунд,
     * полностью заморожен, а весь его инвентарь (включая броню) за эти же 5 секунд
     * постепенно вылетает по одному предмету — каждый под своим углом по кругу,
     * так что визуально получается спираль падающих вниз вещей. Кик — в конце.
     * Иначе — кикает сразу, ресурсы не трогаются.
     */
    public void executeBan(Player admin, Player targetOnline, UUID targetUuid, String targetName, BanReason reason) {
        String ip = (targetOnline != null && targetOnline.getAddress() != null)
                ? targetOnline.getAddress().getAddress().getHostAddress() : null;

        if (reason.permanent()) {
            banManager.banPermanent(targetUuid, targetName, reason.label());
            if (ip != null) banManager.banIpPermanent(ip, targetName, reason.label());
        } else {
            banManager.ban(targetUuid, targetName, reason.label(), reason.days());
            if (ip != null) banManager.banIp(ip, targetName, reason.label(), reason.days());
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

    private void playDramaticBanAndKick(Player target, Component banScreen) {
        UUID uuid = target.getUniqueId();
        animationLocked.add(uuid);

        List<ItemStack> allItems = new ArrayList<>();
        for (ItemStack it : target.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) allItems.add(it.clone());
        }
        for (ItemStack it : target.getInventory().getArmorContents()) {
            if (it != null && it.getType() != Material.AIR) allItems.add(it.clone());
        }
        target.getInventory().clear();
        target.getInventory().setArmorContents(new ItemStack[4]);

        Location start = target.getLocation().clone();
        double perTick = RISE_HEIGHT_BLOCKS / DURATION_TICKS;
        int totalItems = allItems.size();
        double angleStep = totalItems > 0 ? 360.0 / totalItems : 0;

        target.getWorld().playSound(start, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);

        int[] tick = {0};
        int[] nextDropIndex = {0};
        BukkitTask[] taskRef = new BukkitTask[1];

        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;

            if (!target.isOnline()) {
                taskRef[0].cancel();
                animationLocked.remove(uuid);
                return;
            }

            Location next = start.clone().add(0, perTick * tick[0], 0);
            next.setYaw(target.getLocation().getYaw());
            next.setPitch(target.getLocation().getPitch());
            target.teleport(next);

            target.getWorld().spawnParticle(Particle.PORTAL, next.clone().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.04);
            target.getWorld().spawnParticle(Particle.FLAME, next.clone().add(0, 0.1, 0), 4, 0.3, 0.05, 0.3, 0.01);

            // равномерно распределяем выпадение предметов по всей длительности —
            // каждый следующий под своим углом, получается спираль вниз
            if (totalItems > 0) {
                int shouldHaveDropped = (int) ((long) tick[0] * totalItems / DURATION_TICKS);
                while (nextDropIndex[0] < shouldHaveDropped && nextDropIndex[0] < totalItems) {
                    int i = nextDropIndex[0];
                    ItemStack stack = allItems.get(i);
                    double rad = Math.toRadians(angleStep * i);
                    Item dropped = target.getWorld().dropItem(next.clone().add(0, 1, 0), stack);
                    double speed = 0.18;
                    dropped.setVelocity(new Vector(Math.cos(rad) * speed, 0.15, Math.sin(rad) * speed));
                    dropped.setPickupDelay(200); // банимый физически не успеет подобрать
                    nextDropIndex[0]++;
                }
            }

            if (tick[0] >= DURATION_TICKS) {
                taskRef[0].cancel();
                animationLocked.remove(uuid);

                Location peak = target.getLocation();
                target.getWorld().spawnParticle(Particle.EXPLOSION, peak, 1);
                target.getWorld().playSound(peak, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                target.kick(banScreen); // кикаем в момент "взрыва", в воздухе
            }
        }, 0L, 1L);
    }
}
