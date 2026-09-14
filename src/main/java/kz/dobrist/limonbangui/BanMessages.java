package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Красивые тексты: экран кика/бана (видит только забаненный) и
 * публичное объявление в чат (видят все, без упоминания названия плагина).
 */
public class BanMessages {

    public static Component banScreen(String reason, String remaining, String telegramContact) {
        String tgHandle = telegramContact.startsWith("@") ? telegramContact.substring(1) : telegramContact;
        String tgUrl = "https://t.me/" + tgHandle;

        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n", NamedTextColor.DARK_RED)
                .append(Component.text("      ВЫ ЗАБАНЕНЫ\n\n", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Причина: ", NamedTextColor.GRAY))
                .append(Component.text(reason + "\n", NamedTextColor.WHITE))
                .append(Component.text("Срок: ", NamedTextColor.GRAY))
                .append(Component.text(remaining + "\n\n", NamedTextColor.WHITE))
                .append(Component.text("Подать апелляцию: ", NamedTextColor.GRAY))
                .append(Component.text(telegramContact, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(tgUrl)))
                .append(Component.text("\n▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.DARK_RED));
    }

    /** Публичное объявление в чат серверу — без названия плагина, видят все. */
    public static Component publicBanAnnouncement(String playerName, String reason, String remaining) {
        return Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("⚔ ", NamedTextColor.RED))
                .append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" забанен(а)\n", NamedTextColor.RED))
                .append(Component.text("Причина: ", NamedTextColor.GRAY))
                .append(Component.text(reason + "\n", NamedTextColor.WHITE))
                .append(Component.text("Срок: ", NamedTextColor.GRAY))
                .append(Component.text(remaining, NamedTextColor.WHITE))
                .append(Component.text("\n▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.DARK_GRAY));
    }
}
