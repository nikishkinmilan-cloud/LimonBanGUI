package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Для каждого онлайн игрока держит TextDisplay-сущность над головой
 * с числом уровня подозрения (0.00 и т.п.). Сущность видна ТОЛЬКО
 * игрокам с правом limonban.admin (через setVisibleByDefault(false) +
 * showEntity/hideEntity, без ProtocolLib).
 */
public class TrustDisplayManager implements Listener {

    private final LimonBanGUI plugin;
    private final AntiCheatBridge bridge;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private BukkitTask task;

    private final double greenMax;
    private final double yellowMax;
    private final long intervalTicks;

    public TrustDisplayManager(LimonBanGUI plugin, AntiCheatBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.greenMax = plugin.getConfig().getDouble("trust-thresholds.green-max", 0.10);
        this.yellowMax = plugin.getConfig().getDouble("trust-thresholds.yellow-max", 0.50);
        this.intervalTicks = plugin.getConfig().getLong("update-interval-ticks", 10);
    }

    public void start() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            spawnFor(p);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stopAndRemoveAll() {
        if (task != null) task.cancel();
        for (TextDisplay d : displays.values()) {
            if (d.isValid()) d.remove();
        }
        displays.clear();
    }

    private void tick() {
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            TextDisplay display = displays.get(target.getUniqueId());
            if (display == null || !display.isValid()) {
                spawnFor(target);
                display = displays.get(target.getUniqueId());
                if (display == null) continue;
            }

            double level = bridge.getViolationLevel(target);
            display.text(formatLevel(level));
            display.teleport(target.getEyeLocation().add(0, 0.35, 0));

            // видимость только для тех, у кого есть право
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                boolean shouldSee = viewer.hasPermission("limonban.admin") && !viewer.equals(target);
                if (shouldSee) {
                    viewer.showEntity(plugin, display);
                } else {
                    viewer.hideEntity(plugin, display);
                }
            }
        }
    }

    private Component formatLevel(double level) {
        NamedTextColor color;
        if (level <= greenMax) color = NamedTextColor.GREEN;
        else if (level <= yellowMax) color = NamedTextColor.YELLOW;
        else color = NamedTextColor.RED;
        return Component.text(String.format("%.2f", level), color);
    }

    private void spawnFor(Player target) {
        if (displays.containsKey(target.getUniqueId())) return;
        Location loc = target.getEyeLocation().add(0, 0.35, 0);
        TextDisplay display = target.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(formatLevel(0.0));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(false);
            d.setDefaultBackground(false);
            d.setVisibleByDefault(false); // видно только тем, кому явно покажем
            d.setPersistent(false);
        });
        displays.put(target.getUniqueId(), display);
    }

    private void removeFor(UUID uuid) {
        TextDisplay display = displays.remove(uuid);
        if (display != null && display.isValid()) display.remove();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        spawnFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFor(event.getPlayer().getUniqueId());
    }
}
