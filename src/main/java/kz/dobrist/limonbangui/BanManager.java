package kz.dobrist.limonbangui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Свой лёгкий temp/perm-ban на случай, если на сервере нет плагина
 * с поддержкой временных банов. Хранит записи в bans.yml (по UUID)
 * и ipbans.yml (по IP-адресу — ловит альты на том же устройстве/сети).
 */
public class BanManager {

    public record BanEntry(String name, String reason, long expiresAtEpochMillis, boolean permanent) {
        public boolean isExpired() {
            if (permanent) return false;
            return Instant.now().toEpochMilli() >= expiresAtEpochMillis;
        }
    }

    private final LimonBanGUI plugin;
    private final File file;
    private final File ipFile;
    private final Map<UUID, BanEntry> bans = new HashMap<>();
    private final Map<String, BanEntry> ipBans = new HashMap<>();

    public BanManager(LimonBanGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        this.ipFile = new File(plugin.getDataFolder(), "ipbans.yml");
        load();
        loadIp();
    }

    public void ban(UUID uuid, String name, String reason, int days) {
        long expiresAt = Instant.now().plusSeconds(days * 24L * 3600L).toEpochMilli();
        bans.put(uuid, new BanEntry(name, reason, expiresAt, false));
        save();
    }

    public void banPermanent(UUID uuid, String name, String reason) {
        bans.put(uuid, new BanEntry(name, reason, -1, true));
        save();
    }

    public void unban(UUID uuid) {
        bans.remove(uuid);
        save();
    }

    /** Все IP, забаненные под этим именем (без учёта регистра) — чтобы unban мог сразу снять и их тоже. */
    public List<String> findIpsByName(String name) {
        List<String> ips = new ArrayList<>();
        for (Map.Entry<String, BanEntry> e : ipBans.entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(name)) {
                ips.add(e.getKey());
            }
        }
        return ips;
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

    public void banIp(String ip, String name, String reason, int days) {
        if (ip == null) return;
        long expiresAt = Instant.now().plusSeconds(days * 24L * 3600L).toEpochMilli();
        ipBans.put(ip, new BanEntry(name, reason, expiresAt, false));
        saveIp();
    }

    public void banIpPermanent(String ip, String name, String reason) {
        if (ip == null) return;
        ipBans.put(ip, new BanEntry(name, reason, -1, true));
        saveIp();
    }

    public void unbanIp(String ip) {
        ipBans.remove(ip);
        saveIp();
    }

    /** null = этот IP не забанен / бан истёк. */
    public BanEntry getActiveIpBan(String ip) {
        if (ip == null) return null;
        BanEntry entry = ipBans.get(ip);
        if (entry == null) return null;
        if (entry.isExpired()) {
            ipBans.remove(ip);
            saveIp();
            return null;
        }
        return entry;
    }

    public String formatRemaining(BanEntry entry) {
        if (entry.permanent()) return "навсегда";
        long ms = entry.expiresAtEpochMillis() - Instant.now().toEpochMilli();
        long days = ms / (1000 * 60 * 60 * 24);
        long hours = (ms / (1000 * 60 * 60)) % 24;
        return days + " дн. " + hours + " ч.";
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = yaml.getString(key + ".name", "unknown");
                String reason = yaml.getString(key + ".reason", "");
                boolean permanent = yaml.getBoolean(key + ".permanent", false);
                long expiresAt = yaml.getLong(key + ".expiresAt");
                bans.put(uuid, new BanEntry(name, reason, expiresAt, permanent));
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
            yaml.set(key + ".permanent", e.getValue().permanent());
            yaml.set(key + ".expiresAt", e.getValue().expiresAtEpochMillis());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить bans.yml", ex);
        }
    }

    // IP храним списком записей (не секциями по ключу) — точки в IP-адресе
    // ломают YAML-пути, если использовать их как ключи секций напрямую.
    private void loadIp() {
        if (!ipFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ipFile);
        for (Map<?, ?> raw : yaml.getMapList("entries")) {
            String ip = String.valueOf(raw.get("ip"));
            String name = String.valueOf(raw.get("name"));
            String reason = String.valueOf(raw.get("reason"));
            boolean permanent = raw.get("permanent") != null && (boolean) raw.get("permanent");
            long expiresAt = raw.get("expiresAt") != null ? ((Number) raw.get("expiresAt")).longValue() : 0L;
            ipBans.put(ip, new BanEntry(name, reason, expiresAt, permanent));
        }
    }

    public void saveIp() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, BanEntry> e : ipBans.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ip", e.getKey());
            m.put("name", e.getValue().name());
            m.put("reason", e.getValue().reason());
            m.put("permanent", e.getValue().permanent());
            m.put("expiresAt", e.getValue().expiresAtEpochMillis());
            list.add(m);
        }
        yaml.set("entries", list);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(ipFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить ipbans.yml", ex);
        }
    }
}
