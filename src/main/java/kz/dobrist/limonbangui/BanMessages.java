package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Красивый экран, который видит игрок при бане/кике.
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
}
