package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Достаёт "уровень подозрения" (0.00 = чист, выше = больше нарушений)
 * из плагина LimonAntiCheat через reflection (чтобы не тянуть его как
 * compile-зависимость — плагины остаются независимыми и работают
 * по отдельности, просто цифры "оживают", когда оба стоят вместе).
 */
public class AntiCheatBridge {

    private final LimonBanGUI plugin;
    private Plugin antiCheatPlugin;
    private Method violationMethod;
    private boolean resolved = false;

    private static final String AC_PLUGIN_NAME = "LimonAntiCheat";
    private static final String AC_MAIN_CLASS = "kz.dobrist.limonanticheat.LimonAntiCheat";
    private static final String AC_METHOD_NAME = "getViolationLevel"; // double getViolationLevel(Player)

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
                    + ". Причина: " + ex);
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
