package kz.dobrist.limonbangui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Пока игрок поднимается в анимации бана (BanService.isAnimationLocked),
 * блокирует любое движение по горизонтали, которое пытается сделать сам
 * игрок. По вертикали его двигает сам BanService напрямую через teleport()
 * — тот не проходит через PlayerMoveEvent, так что подъём эту блокировку
 * не задевает.
 */
public class BanAnimationFreezeListener implements Listener {

    private final BanService banService;

    public BanAnimationFreezeListener(BanService banService) {
        this.banService = banService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!banService.isAnimationLocked(p.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location fixed = to.clone();
            fixed.setX(from.getX());
            fixed.setZ(from.getZ());
            event.setTo(fixed);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && banService.isAnimationLocked(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
