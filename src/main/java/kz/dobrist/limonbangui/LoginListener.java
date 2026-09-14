package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class LoginListener implements Listener {

    private final BanManager banManager;

    public LoginListener(BanManager banManager) {
        this.banManager = banManager;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        BanManager.BanEntry ban = banManager.getActiveBan(event.getPlayer().getUniqueId());
        if (ban == null) return;

        Component kickMessage = Component.text("Вы забанены.\n", NamedTextColor.RED)
                .append(Component.text("Причина: " + ban.reason() + "\n", NamedTextColor.GRAY))
                .append(Component.text("Осталось: " + banManager.formatRemaining(ban), NamedTextColor.GRAY));

        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
    }
}
