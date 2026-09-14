package kz.dobrist.limonbangui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Свой лёгкий temp-ban на случай, если на сервере нет плагина
 * с поддержкой временных банов (AdvancedBan / LibertyBans и т.п.).
 * Хранит записи в bans.yml: uuid -> {name, reason, expiresAt}.
 */
public class BanManager {

    public record BanEntry(String name, String reason, long expiresAtEpochMillis) {
        public boolean isExpired() {
            return Instant.now().toEpochMilli() >= expiresAtEpochMillis;
        }
    }

    private final LimonBanGUI plugin;
    private final File file;
    private final Map<UUID, BanEntry> bans = new HashMap<>();

    public BanManager(LimonBanGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        load();
    }

    public void ban(UUID uuid, String name, String reason, int days) {
        long expiresAt = Instant.now().plusSeconds(days * 24L * 3600L).toEpochMilli();
        bans.put(uuid, new BanEntry(name, reason, expiresAt));
        save();
    }

    public void unban(UUID uuid) {
        bans.remove(uuid);
        save();
    }

    /** null = не забанен / бан истёк (запись автоматически чистится). */
    public BanEntry getActiveBan(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        if (entry == null) return null;
        if (entry.isExpired()) {
            bans.remove(uuid);
            save();
            return null;
        }
        return entry;
    }

    public String formatRemaining(BanEntry entry) {
        long ms = entry.expiresAtEpochMillis() - Instant.now().toEpochMilli();
        long days = ms / (1000 * 60 * 60 * 24);
        long hours = (ms / (1000 * 60 * 60)) % 24;
        return days + " дн. " + hours + " ч.";
    }

    public String formatExpiry(BanEntry entry) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entry.expiresAtEpochMillis()));
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
                bans.put(uuid, new BanEntry(name, reason, expiresAt));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Пропущена некорректная запись бана: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, BanEntry> e : bans.entrySet()) {
            String key = e.getKey().toString();
            yaml.set(key + ".name", e.getValue().name());
            yaml.set(key + ".reason", e.getValue().reason());
            yaml.set(key + ".expiresAt", e.getValue().expiresAtEpochMillis());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить bans.yml", ex);
        }
    }
}
