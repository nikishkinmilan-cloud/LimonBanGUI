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

public class MuteMenus {

    public static final int SLOT_BACK = 22;

    /** Список всех онлайн-игроков (кроме самого смотрящего) — кликом выбираешь, кого мутить. */
    public static Inventory buildPicker(Player viewer) {
        MuteMenuHolder holder = new MuteMenuHolder(MuteMenuHolder.MenuType.PICKER, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Кого замутить?", NamedTextColor.GOLD));
        holder.setInventory(inv);

        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(viewer)) continue;
            if (slot >= 54) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.displayName(Component.text(p.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(BanKeys.MUTE_TARGET_KEY, PersistentDataType.STRING, p.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }
        return inv;
    }

    public static Inventory buildReasons(Player target, List<MuteReason> reasons) {
        MuteMenuHolder holder = new MuteMenuHolder(MuteMenuHolder.MenuType.REASONS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Замутить: " + target.getName(), NamedTextColor.GOLD));
        holder.setInventory(inv);

        int slot = 10;
        for (MuteReason reason : reasons) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(reason.label(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Срок: " + MuteService.formatMinutes(reason.minutes()), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("ЛКМ — замутить", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(BanKeys.CATEGORY_KEY, PersistentDataType.STRING, reason.key());
            it.setItemMeta(meta);
            inv.setItem(slot, it);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Назад", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        return inv;
    }
}
