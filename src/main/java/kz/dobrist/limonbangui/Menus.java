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

public class Menus {

    // главное меню: слот -> действие
    public static final int SLOT_REVIEW = 10;        // "Вызвать на проверку"
    public static final int SLOT_END_REVIEW = 12;     // "Завершить проверку"
    public static final int SLOT_BAN = 14;             // -> открывает подменю причин бана
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

        inv.setItem(SLOT_BAN, item(Material.NETHERITE_SWORD,
                Component.text("Забанить", NamedTextColor.RED),
                List.of(Component.text("Выбрать причину и забанить", NamedTextColor.GRAY))));

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
                lore.add(Component.text("⚡ Подкинет на 5 блоков и уронит ресурсы", NamedTextColor.GOLD));
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
