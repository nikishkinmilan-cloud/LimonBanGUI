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

    // Параметры "красивого" бана-античита (в стиле кольца вокруг игрока)
    private static final double LAUNCH_HEIGHT_BLOCKS = 3.0; // на сколько резко подкидывает вверх
    private static final int RING_DURATION_TICKS = 70;       // сколько крутится кольцо (~3.5 сек)
    private static final double RING_BASE_RADIUS = 1.6;
    private static final double RING_SPIN_DEGREES_PER_TICK = 6.0;
    private static final long KICK_DELAY_AFTER_RELEASE_TICKS = 20L;

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
     * Если reason.dramatic() — игрока резко подкидывает вверх, весь его инвентарь
     * (включая броню) сразу становится вращающимся кольцом предметов вокруг него,
     * игрок полностью заморожен и висит на пике. Через несколько секунд кольцо
     * рассыпается (гравитация включается, предметы разлетаются и падают), и только
     * тогда кик. Иначе — кикает сразу, ресурсы не трогаются.
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
                playRingBanAndKick(targetOnline, banScreen);
            } else {
                targetOnline.kick(banScreen);
            }
        }

        Bukkit.broadcast(BanMessages.publicBanAnnouncement(targetName, reason.label(), remaining));
    }

    private void playRingBanAndKick(Player target, Component banScreen) {
        UUID uuid = target.getUniqueId();
        animationLocked.add(uuid);

        // забираем весь инвентарь (включая броню) сразу — он станет кольцом
        List<ItemStack> allItems = new ArrayList<>();
        for (ItemStack it : target.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) allItems.add(it.clone());
        }
        for (ItemStack it : target.getInventory().getArmorContents()) {
            if (it != null && it.getType() != Material.AIR) allItems.add(it.clone());
        }
        target.getInventory().clear();
        target.getInventory().setArmorContents(new ItemStack[4]);

        Location peak = target.getLocation().clone().add(0, LAUNCH_HEIGHT_BLOCKS, 0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.9f);
        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
        target.teleport(peak);

        List<Item> ring = new ArrayList<>();
        for (ItemStack stack : allItems) {
            Item entity = target.getWorld().dropItem(peak.clone().add(0, 1, 0), stack);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setPickupDelay(Short.MAX_VALUE); // недоступно для подбора, пока крутится в кольце
            ring.add(entity);
        }

        double baseRadius = ring.isEmpty() ? 0 : RING_BASE_RADIUS;
        int[] tick = {0};
        BukkitTask[] taskRef = new BukkitTask[1];

        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;

            if (!target.isOnline()) {
                taskRef[0].cancel();
                ring.forEach(Item::remove);
                animationLocked.remove(uuid);
                return;
            }

            // держим игрока строго на пике — кольцо должно крутиться вокруг стабильной точки
            Location hold = peak.clone();
            hold.setYaw(target.getLocation().getYaw());
            hold.setPitch(target.getLocation().getPitch());
            target.teleport(hold);

            double spin = tick[0] * RING_SPIN_DEGREES_PER_TICK;
            double radius = baseRadius + Math.sin(tick[0] * 0.1) * 0.15;
            for (int i = 0; i < ring.size(); i++) {
                Item entity = ring.get(i);
                if (entity.isDead()) continue;
                double angleDeg = spin + (360.0 / ring.size()) * i;
                double rad = Math.toRadians(angleDeg);
                double x = peak.getX() + Math.cos(rad) * radius;
                double z = peak.getZ() + Math.sin(rad) * radius;
                double y = peak.getY() + 1.1 + Math.sin(tick[0] * 0.15 + i) * 0.1;
                entity.teleport(new Location(peak.getWorld(), x, y, z));
            }

            if (tick[0] % 5 == 0) {
                target.getWorld().spawnParticle(Particle.PORTAL, peak.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.03);
            }

            if (tick[0] >= RING_DURATION_TICKS) {
                taskRef[0].cancel();
                releaseRing(peak, ring);
                animationLocked.remove(uuid);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline()) target.kick(banScreen);
                }, KICK_DELAY_AFTER_RELEASE_TICKS);
            }
        }, 0L, 1L);
    }

    /** Кольцо "рассыпается" — гравитация включается, предметы разлетаются в стороны и падают. */
    private void releaseRing(Location center, List<Item> ring) {
        for (int i = 0; i < ring.size(); i++) {
            Item entity = ring.get(i);
            if (entity.isDead()) continue;
            entity.setGravity(true);
            entity.setPickupDelay(200);
            double angleDeg = (360.0 / ring.size()) * i;
            double rad = Math.toRadians(angleDeg);
            entity.setVelocity(new Vector(Math.cos(rad) * 0.15, 0.05, Math.sin(rad) * 0.15));
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }
}
