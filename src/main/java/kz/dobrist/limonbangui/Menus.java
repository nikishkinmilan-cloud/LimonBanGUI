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

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Menus {

    // главное меню: слот -> действие
    public static final int SLOT_REVIEW = 11;      // "Вызвать на проверку"
    public static final int SLOT_ROOM = 12;        // "Отправить в комнату проверки"
    public static final int SLOT_ANTICHEAT = 14;   // -> открывает подменю
    public static final int SLOT_CLOSE = 22;

    public static Inventory buildMain(Player target, double trustLevel) {
        BanMenuHolder holder = new BanMenuHolder(BanMenuHolder.MenuType.MAIN, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Бан: " + target.getName(), NamedTextColor.GOLD));
        holder.setInventory(inv);

        inv.setItem(4, playerHead(target, trustLevel));
        inv.setItem(SLOT_REVIEW, item(Material.COMPASS,
                Component.text("Вызвать на проверку", NamedTextColor.AQUA),
                List.of(Component.text("Отправить игрока на проверку анти-читом", NamedTextColor.GRAY))));
        boolean roomReady = LimonBanGUI.getInstance().getCheckRoomManager().hasRoom();
        inv.setItem(SLOT_ROOM, item(Material.IRON_BARS,
                Component.text("Отправить в комнату проверки", NamedTextColor.LIGHT_PURPLE),
                List.of(roomReady
                        ? Component.text("Телепортирует игрока в комнату для проверки", NamedTextColor.GRAY)
                        : Component.text("Комната не настроена: /limonban room set", NamedTextColor.RED))));

        inv.setItem(SLOT_ANTICHEAT, item(Material.NETHERITE_SWORD,
                Component.text("Забанить — Античит", NamedTextColor.RED),
                List.of(Component.text("Выбрать пункт правил и забанить", NamedTextColor.GRAY),
                        Component.text("на " + LimonBanGUI.getInstance().getConfig().getInt("ban-days", 25) + " дней", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, Component.text("Закрыть", NamedTextColor.GRAY), List.of()));

        fillBorder(inv);
        return inv;
    }

    public static Inventory buildAnticheatSub(Player target) {
        BanMenuHolder holder = new BanMenuHolder(BanMenuHolder.MenuType.ANTICHEAT_SUB, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Античит: " + target.getName(), NamedTextColor.RED));
        holder.setInventory(inv);

        List<Map<?, ?>> categories = LimonBanGUI.getInstance().getConfig().getMapList("anticheat-categories");
        int slot = 10;
        for (Map<?, ?> cat : categories) {
            String key = String.valueOf(cat.get("key"));
            String label = String.valueOf(cat.get("label"));
            ItemStack it = item(Material.PAPER, Component.text(label, NamedTextColor.YELLOW), List.of(
                    Component.text("ЛКМ — забанить на " + LimonBanGUI.getInstance().getConfig().getInt("ban-days", 25) + " дней", NamedTextColor.GRAY)
            ));
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(BanKeys.CATEGORY_KEY, org.bukkit.persistence.PersistentDataType.STRING, key);
            it.setItemMeta(meta);
            inv.setItem(slot, it);
            slot++;
            if (slot == 17) slot = 19; // переход на след. ряд, минуя край
        }

        inv.setItem(22, item(Material.ARROW, Component.text("Назад", NamedTextColor.GRAY), List.of()));
        fillBorder(inv);
        return inv;
    }

    private static ItemStack playerHead(Player target, double trustLevel) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Уровень подозрения: " + String.format("%.2f", trustLevel), NamedTextColor.GRAY)
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
