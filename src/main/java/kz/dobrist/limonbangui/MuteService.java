package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MuteService {

    private final LimonBanGUI plugin;
    private final MuteManager muteManager;

    public MuteService(LimonBanGUI plugin, MuteManager muteManager) {
        this.plugin = plugin;
        this.muteManager = muteManager;
    }

    public List<MuteReason> loadReasons() {
        List<MuteReason> result = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("mute-reasons")) {
            String key = String.valueOf(raw.get("key"));
            String label = String.valueOf(raw.get("label"));
            int minutes = raw.get("minutes") != null ? ((Number) raw.get("minutes")).intValue() : 30;
            result.add(new MuteReason(key, label, minutes));
        }
        return result;
    }

    public void executeMute(Player admin, Player targetOnline, UUID targetUuid, String targetName, MuteReason reason) {
        muteManager.mute(targetUuid, targetName, reason.label(), reason.minutes());

        if (targetOnline != null) {
            targetOnline.sendMessage(net.kyori.adventure.text.Component.text(
                    "Вы замьючены.\nПричина: " + reason.label() + "\nСрок: " + formatMinutes(reason.minutes()),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }

        Bukkit.broadcast(BanMessages.publicMuteAnnouncement(targetName, reason.label(), formatMinutes(reason.minutes())));
    }

    public static String formatMinutes(int minutes) {
        if (minutes % 1440 == 0) return (minutes / 1440) + " дн.";
        if (minutes % 60 == 0) return (minutes / 60) + " ч.";
        return minutes + " мин.";
    }
}
