package kz.dobrist.limonbangui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MuteListener implements Listener {

    private final MuteManager muteManager;

    public MuteListener(MuteManager muteManager) {
        this.muteManager = muteManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        MuteManager.MuteEntry mute = muteManager.getActiveMute(event.getPlayer().getUniqueId());
        if (mute == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("Вы в муте.\n", NamedTextColor.RED)
                .append(Component.text("Причина: " + mute.reason() + "\n", NamedTextColor.GRAY))
                .append(Component.text("Осталось: " + muteManager.formatRemaining(mute), NamedTextColor.GRAY)));
    }
}
