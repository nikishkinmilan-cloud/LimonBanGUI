package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Комната для проверки на читы: одна точка телепорта, задаётся командой
 * на месте, где стоит админ. При отправке игрока в комнату сохраняется
 * его прежняя точка, чтобы потом вернуть обратно.
 */
public class CheckRoomManager {

    private final LimonBanGUI plugin;
    private final File file;

    private Location room;
    private final Map<UUID, Location> returnPoints = new HashMap<>();

    public CheckRoomManager(LimonBanGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "checkroom.yml");
        load();
    }

    public boolean hasRoom() {
        return room != null;
    }

    public void setRoom(Location location) {
        this.room = location.clone();
        save();
    }

    public boolean sendToRoom(Player target) {
        if (room == null) return false;
        returnPoints.put(target.getUniqueId(), target.getLocation().clone());
        target.teleport(room);
        target.sendMessage(Component.text("Вас перенесли в комнату проверки на читы.", NamedTextColor.YELLOW));
        save();
        return true;
    }

    /** true, если для игрока была точка возврата и телепорт удался. */
    public boolean returnFromRoom(Player target) {
        Location back = returnPoints.remove(target.getUniqueId());
        if (back == null) return false;
        target.teleport(back);
        target.sendMessage(Component.text("Проверка окончена, вы возвращены обратно.", NamedTextColor.GREEN));
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (yaml.contains("room.world")) {
            World world = Bukkit.getWorld(yaml.getString("room.world"));
            if (world != null) {
                room = new Location(world,
                        yaml.getDouble("room.x"), yaml.getDouble("room.y"), yaml.getDouble("room.z"),
                        (float) yaml.getDouble("room.yaw"), (float) yaml.getDouble("room.pitch"));
            }
        }

        if (yaml.isConfigurationSection("returns")) {
            for (String key : yaml.getConfigurationSection("returns").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    World world = Bukkit.getWorld(yaml.getString("returns." + key + ".world"));
                    if (world == null) continue;
                    Location loc = new Location(world,
                            yaml.getDouble("returns." + key + ".x"),
                            yaml.getDouble("returns." + key + ".y"),
                            yaml.getDouble("returns." + key + ".z"),
                            (float) yaml.getDouble("returns." + key + ".yaw"),
                            (float) yaml.getDouble("returns." + key + ".pitch"));
                    returnPoints.put(uuid, loc);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (room != null) {
            yaml.set("room.world", room.getWorld().getName());
            yaml.set("room.x", room.getX());
            yaml.set("room.y", room.getY());
            yaml.set("room.z", room.getZ());
            yaml.set("room.yaw", (double) room.getYaw());
            yaml.set("room.pitch", (double) room.getPitch());
        }
        for (Map.Entry<UUID, Location> e : returnPoints.entrySet()) {
            String key = "returns." + e.getKey();
            Location l = e.getValue();
            yaml.set(key + ".world", l.getWorld().getName());
            yaml.set(key + ".x", l.getX());
            yaml.set(key + ".y", l.getY());
            yaml.set(key + ".z", l.getZ());
            yaml.set(key + ".yaw", (double) l.getYaw());
            yaml.set(key + ".pitch", (double) l.getPitch());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить checkroom.yml", ex);
        }
    }
}
