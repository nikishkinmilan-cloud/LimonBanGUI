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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BanService {

    // Параметры "красивого" бана-античита
    private static final int FLIGHT_DURATION_TICKS = 140; // 7 секунд

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
     * Если reason.dramatic() — игрок плавно левитирует 7 секунд, при этом полностью
     * заморожен (двигаться не может вообще — только поднимается). Обычные предметы
     * выпадают по одному в течение полёта, броня остаётся на игроке почти весь полёт
     * и слетает последней — эффектнее смотрится. Кик — в конце, в воздухе.
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
        animationLocked.add(uuid); // полная заморозка — двигаться нельзя, только левитация вверх

        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, FLIGHT_DURATION_TICKS + 20, 0, false, true, true));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);

        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!target.isOnline()) return;
            Location loc = target.getLocation();
            target.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.05);
            target.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.1, 0), 6, 0.3, 0.05, 0.3, 0.01);
        }, 0L, 4L);

        // обычные предметы забираем сразу — будут дропаться по одному
        List<ItemStack> mainItems = new ArrayList<>();
        for (ItemStack it : target.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) mainItems.add(it.clone());
        }
        target.getInventory().clear();

        // список "действий-дропов": сначала обычные вещи, броня — в конце списка,
        // так что физически она провисит на игроке почти весь полёт и слетит последней
        List<Runnable> drops = new ArrayList<>();
        for (ItemStack item : mainItems) {
            drops.add(() -> dropAt(target, item));
        }
        for (int slot = 0; slot < 4; slot++) {
            int armorSlot = slot;
            drops.add(() -> {
                ItemStack piece = getArmorSlot(target, armorSlot);
                if (piece != null && piece.getType() != Material.AIR) {
                    setArmorSlot(target, armorSlot, null);
                    dropAt(target, piece);
                }
            });
        }

        if (!drops.isEmpty()) {
            int interval = Math.max(1, FLIGHT_DURATION_TICKS / drops.size());
            for (int i = 0; i < drops.size(); i++) {
                Runnable action = drops.get(i);
                long delay = (long) i * interval;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline()) action.run();
                }, delay);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            particleTask.cancel();
            animationLocked.remove(uuid);
            if (target.isOnline()) {
                Location loc = target.getLocation();
                target.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                target.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                target.kick(banScreen); // кикаем прямо в воздухе, в момент "взрыва"
            }
        }, FLIGHT_DURATION_TICKS);
    }

    private void dropAt(Player target, ItemStack item) {
        Item dropped = target.getWorld().dropItemNaturally(target.getLocation(), item);
        dropped.setPickupDelay(200); // банимый физически не успеет подобрать
    }

    private ItemStack getArmorSlot(Player p, int index) {
        return p.getInventory().getArmorContents()[index]; // 0=ботинки 1=штаны 2=нагрудник 3=шлем
    }

    private void setArmorSlot(Player p, int index, ItemStack value) {
        switch (index) {
            case 0 -> p.getInventory().setBoots(value);
            case 1 -> p.getInventory().setLeggings(value);
            case 2 -> p.getInventory().setChestplate(value);
            case 3 -> p.getInventory().setHelmet(value);
        }
    }
}
