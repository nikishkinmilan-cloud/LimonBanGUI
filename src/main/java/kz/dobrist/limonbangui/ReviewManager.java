package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Держит множество игроков, которые сейчас "на проверке" (заморожены,
 * не могут двигаться/действовать). FreezeListener сверяется с этим
 * классом, чтобы гасить любые попытки что-то сделать.
 */
public class ReviewManager {

    private final LimonBanGUI plugin;
    private final CheckRoomManager checkRoomManager;
    private final Set<UUID> inReview = new HashSet<>();

    public ReviewManager(LimonBanGUI plugin, CheckRoomManager checkRoomManager) {
        this.plugin = plugin;
        this.checkRoomManager = checkRoomManager;
    }

    public boolean isInReview(UUID uuid) {
        return inReview.contains(uuid);
    }

    /** Вызвать игрока на проверку: заморозить + телепорт в комнату (если задана). */
    public void startReview(Player admin, Player target) {
        inReview.add(target.getUniqueId());
        if (checkRoomManager.hasRoom()) {
            checkRoomManager.sendToRoom(target);
        } else {
            target.sendMessage(Component.text("Вы вызваны на проверку анти-читом. Оставайтесь на месте.", NamedTextColor.YELLOW));
        }
        Bukkit.broadcast(Component.text("[LimonBanGUI] " + target.getName() + " вызван(а) на проверку — "
                + admin.getName(), NamedTextColor.YELLOW), "limonban.admin");
    }

    /** Снять с проверки: разморозить + вернуть на прежнее место. Ничего не делает, если игрок не был на проверке. */
    public boolean endReview(Player target) {
        if (!inReview.remove(target.getUniqueId())) return false;
        checkRoomManager.returnFromRoom(target);
        return true;
    }

    /** Игрок вышел, пока был на проверке — снимаем флаг заморозки (без возврата, он офлайн). */
    public void clearOnQuit(UUID uuid) {
        inReview.remove(uuid);
    }
}
