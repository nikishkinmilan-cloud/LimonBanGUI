package kz.dobrist.limonbangui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class LoginListener implements Listener {

    private final LimonBanGUI plugin;
    private final BanManager banManager;

    public LoginListener(LimonBanGUI plugin, BanManager banManager) {
        this.plugin = plugin;
        this.banManager = banManager;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        BanManager.BanEntry uuidBan = banManager.getActiveBan(event.getPlayer().getUniqueId());

        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : null;
        BanManager.BanEntry ipBan = banManager.getActiveIpBan(ip);

        // приоритет — более долгому/строгому бану, если забанены оба варианта одновременно
        BanManager.BanEntry effective = pickStricter(uuidBan, ipBan);
        if (effective == null) return;

        String telegram = plugin.getConfig().getString("telegram-contact", "@MIlan4ck3456");
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                BanMessages.banScreen(effective.reason(), banManager.formatRemaining(effective), telegram));
    }

    private BanManager.BanEntry pickStricter(BanManager.BanEntry a, BanManager.BanEntry b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.permanent() || b.permanent()) return a.permanent() ? a : b;
        return a.expiresAtEpochMillis() >= b.expiresAtEpochMillis() ? a : b;
    }
}
