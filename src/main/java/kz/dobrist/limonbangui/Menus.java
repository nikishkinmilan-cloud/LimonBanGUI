package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public class Menus {

    // главное меню: слот -> действие
    public static final int SLOT_REVIEW = 10;        // "Вызвать на проверку"
    public static final int SLOT_END_REVIEW = 12;     // "Завершить проверку"
    public static final int SLOT_REPORT = 13;          // "Анти-чит отчёт"
    public static final int SLOT_BAN = 15;              // -> открывает подменю причин бана
    public static final int SLOT_MUTE = 16;             // "Замутить" — открывает причины мута для этого игрока
    public static final int SLOT_CLOSE = 22;

    public static Inventory buildMain(Player target, double trustLevel, boolean inReview) {
        BanMenuHolder holder = new BanMenuHolder(BanMenuHolder.MenuType.MAIN, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Бан: " + target.getName(), NamedTextColor.GOLD));
        holder.setInventory(inv);

        inv.setItem(4, playerHead(target, trustLevel, inReview));

        inv.setItem(SLOT_REVIEW, item(Material.COMPASS,
                Component.text("Вызвать на проверку", NamedTextColor.AQUA),
                List.of(Component.text("Заморозить игрока и отправить", NamedTextColor.GRAY),
                        Component.text("в комнату проверки", NamedTextColor.GRAY))));

        inv.setItem(SLOT_END_REVIEW, item(Material.LIME_DYE,
                Component.text("Завершить проверку", NamedTextColor.GREEN),
                List.of(Component.text(inReview ? "Разморозить и вернуть игрока" : "Игрок сейчас не на проверке", NamedTextColor.GRAY))));

        inv.setItem(SLOT_REPORT, item(Material.WRITTEN_BOOK,
                Component.text("Анти-чит отчёт", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("Разбивка нарушений по типам", NamedTextColor.GRAY),
                        Component.text("(Fly/Speed/Reach/Aim/...)", NamedTextColor.DARK_GRAY))));

        inv.setItem(SLOT_BAN, item(Material.NETHERITE_SWORD,
                Component.text("Забанить", NamedTextColor.RED),
                List.of(Component.text("Выбрать причину и забанить", NamedTextColor.GRAY))));

        inv.setItem(SLOT_MUTE, item(Material.JUKEBOX,
                Component.text("Замутить", NamedTextColor.GOLD),
                List.of(Component.text("Выбрать причину и замутить", NamedTextColor.GRAY))));

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, Component.text("Закрыть", NamedTextColor.GRAY), List.of()));

        fillBorder(inv);
        return inv;
    }

    public static Inventory buildReasonsMenu(Player target, List<BanReason> reasons) {
        BanMenuHolder holder = new BanMenuHolder(BanMenuHolder.MenuType.BAN_REASONS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text("Забанить: " + target.getName(), NamedTextColor.RED));
        holder.setInventory(inv);

        int slot = 9;
        for (BanReason reason : reasons) {
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text(reason.permanent() ? "Срок: навсегда" : "Срок: " + reason.days() + " дней", NamedTextColor.GRAY));
            if (reason.dramatic()) {
                lore.add(Component.text("⚡ Подкинет вверх, весь лут", NamedTextColor.GOLD));
                lore.add(Component.text("станет вращающимся кольцом", NamedTextColor.GOLD));
            }
            lore.add(Component.text("ЛКМ — забанить", NamedTextColor.DARK_GRAY));

            Material material = reason.dramatic() ? Material.NETHER_STAR
                    : reason.permanent() ? Material.BEDROCK
                    : Material.PAPER;

            ItemStack it = item(material, Component.text(reason.label(), NamedTextColor.YELLOW), lore);
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(BanKeys.CATEGORY_KEY, PersistentDataType.STRING, reason.key());
            it.setItemMeta(meta);
            inv.setItem(slot, it);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // не залезаем на край ряда
        }

        inv.setItem(31, item(Material.ARROW, Component.text("Назад", NamedTextColor.GRAY), List.of()));
        fillBorder(inv);
        return inv;
    }

    public static Inventory buildReport(Player target, double totalTrust, Map<String, Double> breakdown) {
        BanMenuHolder holder = new BanMenuHolder(BanMenuHolder.MenuType.REPORT, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text("Анти-чит отчёт: " + target.getName(), NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inv);

        NamedTextColor totalColor = totalTrust >= 1.0 ? NamedTextColor.RED
                : totalTrust >= 0.3 ? NamedTextColor.YELLOW
                : NamedTextColor.GREEN;

        ItemStack summary = item(Material.NETHER_STAR,
                Component.text("Суммарный уровень: " + String.format("%.2f", totalTrust), totalColor, TextDecoration.BOLD),
                List.of(Component.text("Чем выше — тем подозрительнее", NamedTextColor.GRAY)));
        inv.setItem(4, summary);

        List<Map.Entry<String, Double>> sorted = breakdown.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();

        int slot = 10;
        if (sorted.isEmpty()) {
            inv.setItem(13, item(Material.PAPER,
                    Component.text("Нарушений не зафиксировано", NamedTextColor.GRAY), List.of()));
        }
        for (Map.Entry<String, Double> entry : sorted) {
            Material icon = iconForCheck(entry.getKey());
            List<Component> lore = List.of(
                    Component.text("Накоплено: " + String.format("%.2f", entry.getValue()), NamedTextColor.GRAY)
            );
            inv.setItem(slot, item(icon, Component.text(entry.getKey(), NamedTextColor.YELLOW), lore));
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        inv.setItem(31, item(Material.ARROW, Component.text("Назад", NamedTextColor.GRAY), List.of()));
        fillBorder(inv);
        return inv;
    }

    private static Material iconForCheck(String checkName) {
        return switch (checkName) {
            case "Fly" -> Material.FEATHER;
            case "Speed" -> Material.SUGAR;
            case "NoFall" -> Material.ANVIL;
            case "Reach" -> Material.STICK;
            case "Aim" -> Material.SPECTRAL_ARROW;
            case "NoSwing" -> Material.LEATHER;
            case "Grim" -> Material.ENDER_EYE;
            default -> Material.BOOK;
        };
    }

    private static ItemStack playerHead(Player target, double trustLevel, boolean inReview) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Уровень подозрения: " + String.format("%.2f", trustLevel), NamedTextColor.GRAY),
                Component.text(inReview ? "Статус: на проверке" : "Статус: обычный", inReview ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
        ));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        it.setItemMeta(meta);
        return it;
    }

    private static void fillBorder(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }
}
