package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
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
    private static final double RISE_HEIGHT_BLOCKS = 8.0;   // на сколько блоков поднимаем (если не упрётся в потолок)
    private static final int RISE_DURATION_TICKS = 100;      // за сколько тиков (5 сек)
    private static final double HEAD_CLEARANCE = 1.9;         // насколько выше головы проверяем потолок

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
     * Если reason.dramatic() — игрок спокойно (без левитации, ручным телепортом)
     * поднимается на 8 блоков ИЛИ до потолка, если тот ближе (не пролетает сквозь
     * блоки), полностью заморожен на время подъёма, на пике взрывается фейерверк
     * и все его ресурсы разлетаются в стороны — и только тогда кик.
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

        Location start = target.getLocation().clone();
        double perTick = RISE_HEIGHT_BLOCKS / RISE_DURATION_TICKS;

        // забираем инвентарь сразу, чтобы одним взрывом высыпать его на пике
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : target.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                items.add(it.clone());
            }
        }
        target.getInventory().clear();

        target.getWorld().playSound(start, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);

        int[] tickCounter = {0};
        BukkitTask[] riseTaskRef = new BukkitTask[1];

        riseTaskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter[0]++;

            if (!target.isOnline()) {
                riseTaskRef[0].cancel();
                animationLocked.remove(uuid);
                return;
            }

            Location candidate = start.clone().add(0, perTick * tickCounter[0], 0);
            Location currentFacing = target.getLocation();
            candidate.setYaw(currentFacing.getYaw());
            candidate.setPitch(currentFacing.getPitch());

            // не пролетаем сквозь потолок пещеры/постройки — смотрим блок чуть выше головы в точке назначения
            boolean ceilingHit = candidate.clone().add(0, HEAD_CLEARANCE, 0).getBlock().getType().isSolid();
            boolean durationDone = tickCounter[0] >= RISE_DURATION_TICKS;

            if (ceilingHit || durationDone) {
                riseTaskRef[0].cancel();
                animationLocked.remove(uuid);
                finishWithExplosion(target, items, banScreen);
                return;
            }

            target.teleport(candidate);
            target.getWorld().spawnParticle(Particle.PORTAL, candidate.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
            target.getWorld().spawnParticle(Particle.FLAME, candidate.clone().add(0, 0.1, 0), 5, 0.3, 0.05, 0.3, 0.01);
        }, 0L, 1L);
    }

    private void finishWithExplosion(Player target, List<ItemStack> items, Component banScreen) {
        Location peak = target.getLocation();
        explodeFirework(peak);
        scatterItems(peak, items);

        target.getWorld().playSound(peak, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        target.kick(banScreen); // кикаем прямо в момент взрыва
    }

    private void explodeFirework(Location loc) {
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED, Color.ORANGE)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BURST)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        fw.detonate(); // взрываем сразу, не ждём "полёта" ракеты
    }

    private void scatterItems(Location center, List<ItemStack> items) {
        for (ItemStack item : items) {
            Item dropped = center.getWorld().dropItem(center, item);
            double angle = Math.random() * Math.PI * 2;
            double speed = 0.25 + Math.random() * 0.3; // подобрано под разлёт ~4-5 блоков
            Vector velocity = new Vector(
                    Math.cos(angle) * speed,
                    0.25 + Math.random() * 0.2,
                    Math.sin(angle) * speed);
            dropped.setVelocity(velocity);
            dropped.setPickupDelay(200); // банимый физически не успеет подобрать
        }
    }
}
