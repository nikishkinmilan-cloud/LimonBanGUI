package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Достаёт "уровень подозрения" и разбивку по чекам из плагина LimonAntiCheat
 * через reflection (чтобы не тянуть его как compile-зависимость — плагины
 * остаются независимыми и работают по отдельности, просто цифры "оживают",
 * когда оба стоят вместе).
 */
public class AntiCheatBridge {

    private final LimonBanGUI plugin;
    private Plugin antiCheatPlugin;
    private Method violationMethod;
    private Method breakdownMethod;
    private boolean resolved = false;

    private static final String AC_PLUGIN_NAME = "LimonAntiCheat";
    private static final String AC_MAIN_CLASS = "kz.dobrist.limonanticheat.LimonAntiCheat";
    private static final String AC_VIOLATION_METHOD = "getViolationLevel"; // double getViolationLevel(Player)
    private static final String AC_BREAKDOWN_METHOD = "getViolationBreakdown"; // Map<String,Double> getViolationBreakdown(Player)

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
            violationMethod = clazz.getMethod(AC_VIOLATION_METHOD, Player.class);
            breakdownMethod = clazz.getMethod(AC_BREAKDOWN_METHOD, Player.class);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Не удалось привязаться к " + AC_MAIN_CLASS
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

    /** Разбивка нарушений по чекам (Fly/Speed/Reach/Aim/NoSwing/Grim -> сумма). Пусто, если мост не настроен. */
    public Map<String, Double> getViolationBreakdown(Player player) {
        resolve();
        Map<String, Double> out = new LinkedHashMap<>();
        if (breakdownMethod == null || antiCheatPlugin == null) return out;
        try {
            Object result = breakdownMethod.invoke(antiCheatPlugin, player);
            if (result instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        out.put(String.valueOf(e.getKey()), n.doubleValue());
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return out;
    }
}
