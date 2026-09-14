package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Держит множество игроков, которые сейчас "на проверке" (заморожены).
 * Кроме отмены событий (FreezeListener), сам следит тикером за позицией
 * игрока и силой возвращает на замороженную точку. Постоянно держит на
 * экране HUD (action bar) "ПРОВЕРКА НА ЧИТЫ" + раз в 20 сек дублирует
 * инструкцию про AnyDesk в чат.
 */
public class ReviewManager {

    private static final long POSITION_CHECK_PERIOD_TICKS = 4L;   // 0.2 сек — позиция + HUD
    private static final long MESSAGE_PERIOD_TICKS = 400L;        // 20 сек — сообщение в чат

    private final LimonBanGUI plugin;
    private final CheckRoomManager checkRoomManager;

    private final Set<UUID> inReview = new HashSet<>();
    private final Map<UUID, Location> frozenAt = new HashMap<>();
    private final Map<UUID, Long> nextMessageTick = new HashMap<>();
    private final Set<UUID> teleportBypass = new HashSet<>();

    private BukkitTask tickTask;
    private long tickCounter = 0L;

    public ReviewManager(LimonBanGUI plugin, CheckRoomManager checkRoomManager) {
        this.plugin = plugin;
        this.checkRoomManager = checkRoomManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, POSITION_CHECK_PERIOD_TICKS, POSITION_CHECK_PERIOD_TICKS);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
    }

    public boolean isInReview(UUID uuid) {
        return inReview.contains(uuid);
    }

    /** Вызвать игрока на проверку: сначала телепорт в комнату (если задана), потом заморозка. */
    public void startReview(Player admin, Player target) {
        UUID uuid = target.getUniqueId();

        // ВАЖНО: телепорт делаем ДО того как пометить игрока замороженным,
        // иначе FreezeListener отменит наш же собственный телепорт в комнату.
        if (checkRoomManager.hasRoom()) {
            checkRoomManager.sendToRoom(target);
        } else {
            target.sendMessage(Component.text("Вы вызваны на проверку анти-читом. Оставайтесь на месте.", NamedTextColor.YELLOW));
        }

        inReview.add(uuid);
        frozenAt.put(uuid, target.getLocation().clone());
        nextMessageTick.put(uuid, tickCounter + MESSAGE_PERIOD_TICKS);
        target.setWalkSpeed(0f);
        target.setFlySpeed(0f);
        target.setVelocity(new Vector(0, 0, 0));

        target.sendMessage(reviewReminder());

        target.showTitle(Title.title(
                Component.text("🔍 ПРОВЕРКА", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Напишите свой AnyDesk ID в чат", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));

        Bukkit.broadcast(Component.text("[LimonBanGUI] " + target.getName() + " вызван(а) на проверку — "
                + admin.getName(), NamedTextColor.YELLOW), "limonban.admin");
    }

    /** Снять с проверки: разморозить + вернуть на прежнее место. */
    public boolean endReview(Player target) {
        UUID uuid = target.getUniqueId();
        if (!inReview.remove(uuid)) return false;
        frozenAt.remove(uuid);
        nextMessageTick.remove(uuid);
        target.setWalkSpeed(0.2f);
        target.setFlySpeed(0.1f);
        target.clearTitle();
        target.sendActionBar(Component.empty());
        checkRoomManager.returnFromRoom(target);
        return true;
    }

    /** Игрок вышел, пока был на проверке — снимаем флаг заморозки (без возврата, он офлайн). */
    public void clearOnQuit(UUID uuid) {
        inReview.remove(uuid);
        frozenAt.remove(uuid);
        nextMessageTick.remove(uuid);
    }

    /** true, если это НАШ собственный корректирующий телепорт — FreezeListener должен его пропустить, а не отменить. */
    public boolean consumeTeleportBypass(UUID uuid) {
        return teleportBypass.remove(uuid);
    }

    private void tick() {
        tickCounter += POSITION_CHECK_PERIOD_TICKS;
        if (inReview.isEmpty()) return;

        for (UUID uuid : new HashSet<>(inReview)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue; // офлайн — обработается через PlayerQuitEvent

            Location frozen = frozenAt.get(uuid);
            if (frozen != null) {
                Location current = p.getLocation();
                boolean sameWorld = current.getWorld() != null && current.getWorld().equals(frozen.getWorld());
                if (!sameWorld || current.distanceSquared(frozen) > 0.02) {
                    teleportBypass.add(uuid);
                    p.teleport(frozen);
                }
                p.setVelocity(new Vector(0, 0, 0));
            }

            // постоянный HUD на экране — обновляем каждый тик проверки позиции, чтобы не пропадал
            p.sendActionBar(actionBarHud());

            Long next = nextMessageTick.get(uuid);
            if (next != null && tickCounter >= next) {
                p.sendMessage(reviewReminder());
                nextMessageTick.put(uuid, tickCounter + MESSAGE_PERIOD_TICKS);
            }
        }
    }

    private Component actionBarHud() {
        return Component.text("🔍 ПРОВЕРКА НА ЧИТЫ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Напишите AnyDesk ID в чат", NamedTextColor.YELLOW))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Лив = бан", NamedTextColor.DARK_RED));
    }

    private Component reviewReminder() {
        return Component.text("Это проверка на читы. Напишите свой AnyDesk ID в чат.\n", NamedTextColor.GOLD)
                .append(Component.text("В случае выхода/лива вы будете забанены.\n", NamedTextColor.RED))
                .append(Component.text("Признание вины уменьшает срок бана на 8 дней.", NamedTextColor.GRAY));
    }
}
