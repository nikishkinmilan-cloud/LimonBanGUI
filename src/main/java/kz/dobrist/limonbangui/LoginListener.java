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
        BanManager.BanEntry ban = banManager.getActiveBan(event.getPlayer().getUniqueId());
        if (ban == null) return;

        String telegram = plugin.getConfig().getString("telegram-contact", "@MIlan4ck3456");
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                BanMessages.banScreen(ban.reason(), banManager.formatRemaining(ban), telegram));
    }
}
