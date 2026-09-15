package kz.dobrist.limonbangui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MuteManager {

    public record MuteEntry(String name, String reason, long expiresAtEpochMillis) {
        public boolean isExpired() {
            return Instant.now().toEpochMilli() >= expiresAtEpochMillis;
        }
    }

    private final LimonBanGUI plugin;
    private final File file;
    private final Map<UUID, MuteEntry> mutes = new HashMap<>();

    public MuteManager(LimonBanGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
        load();
    }

    public void mute(UUID uuid, String name, String reason, int minutes) {
        long expiresAt = Instant.now().plusSeconds(minutes * 60L).toEpochMilli();
        mutes.put(uuid, new MuteEntry(name, reason, expiresAt));
        save();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        save();
    }

    /** null = не в муте / мут истёк (запись автоматически чистится). */
    public MuteEntry getActiveMute(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) return null;
        if (entry.isExpired()) {
            mutes.remove(uuid);
            save();
            return null;
        }
        return entry;
    }

    public String formatRemaining(MuteEntry entry) {
        long ms = entry.expiresAtEpochMillis() - Instant.now().toEpochMilli();
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms / (1000 * 60)) % 60;
        return hours + " ч. " + minutes + " мин.";
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = yaml.getString(key + ".name", "unknown");
                String reason = yaml.getString(key + ".reason", "");
                long expiresAt = yaml.getLong(key + ".expiresAt");
                mutes.put(uuid, new MuteEntry(name, reason, expiresAt));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Пропущена некорректная запись мута: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : mutes.entrySet()) {
            String key = e.getKey().toString();
            yaml.set(key + ".name", e.getValue().name());
            yaml.set(key + ".reason", e.getValue().reason());
            yaml.set(key + ".expiresAt", e.getValue().expiresAtEpochMillis());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить mutes.yml", ex);
        }
    }
}
