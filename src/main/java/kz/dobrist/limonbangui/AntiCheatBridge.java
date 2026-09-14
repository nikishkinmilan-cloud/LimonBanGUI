package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Достаёт "уровень подозрения" (0.00 = чист, выше = больше нарушений)
 * из плагина LimonAntiCheat через reflection, чтобы не тянуть его как
 * compile-зависимость.
 *
 * ЧТО НУЖНО СДЕЛАТЬ В LimonAntiCheat (один раз):
 * добавить в его главный класс публичный метод, например:
 *
 *   public double getViolationLevel(UUID playerUuid) { ... }
 *
 * и прописать ниже реальное имя главного класса/метода вместо заглушки.
 * Пока метод не найден — используется безопасная заглушка (всегда 0.00),
 * плагин не упадёт, просто цифры не будут "живыми".
 */
public class AntiCheatBridge {

    private final LimonBanGUI plugin;
    private Plugin antiCheatPlugin;
    private Method violationMethod;
    private boolean resolved = false;

    // TODO: поправь под реальные имена в LimonAntiCheat
    private static final String AC_PLUGIN_NAME = "LimonAntiCheat";
    private static final String AC_MAIN_CLASS = "kz.dobrist.limonanticheat.LimonAntiCheat"; // TODO
    private static final String AC_METHOD_NAME = "getViolationLevel"; // TODO, сигнатура: double getViolationLevel(Player)

    public AntiCheatBridge(LimonBanGUI plugin) {
        this.plugin = plugin;
    }

    private void resolve() {
        if (resolved) return;
        resolved = true;
        antiCheatPlugin = Bukkit.getPluginManager().getPlugin(AC_PLUGIN_NAME);
        if (antiCheatPlugin == null) {
            plugin.getLogger().warning(AC_PLUGIN_NAME + " не найден — цифры над головой будут показывать 0.00");
            return;
        }
        try {
            Class<?> clazz = Class.forName(AC_MAIN_CLASS);
            violationMethod = clazz.getMethod(AC_METHOD_NAME, Player.class);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Не удалось привязаться к " + AC_MAIN_CLASS + "#" + AC_METHOD_NAME
                    + " — проверь TODO в AntiCheatBridge.java. Причина: " + ex);
        }
    }

    /** Возвращает уровень подозрения 0.0+ либо 0.0, если мост не настроен. */
    public double getViolationLevel(Player player) {
        resolve();
        if (violationMethod == null || antiCheatPlugin == null) return 0.0;
        try {
            Object result = violationMethod.invoke(antiCheatPlugin, player);
            if (result instanceof Number n) return n.doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return 0.0;
    }
}
