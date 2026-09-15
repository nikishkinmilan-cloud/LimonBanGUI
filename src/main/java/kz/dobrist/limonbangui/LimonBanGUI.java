package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LimonBanGUI extends JavaPlugin {

    private static LimonBanGUI instance;

    private BanManager banManager;
    private TrustDisplayManager trustDisplayManager;
    private AntiCheatBridge antiCheatBridge;
    private CheckRoomManager checkRoomManager;
    private ReviewManager reviewManager;
    private BanService banService;
    private MuteManager muteManager;
    private MuteService muteService;
    private MuteMenuListener muteMenuListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true); // подтягивает новые ключи из jar в уже существующий config.yml
        saveConfig();

        this.banManager = new BanManager(this);
        this.antiCheatBridge = new AntiCheatBridge(this);
        this.trustDisplayManager = new TrustDisplayManager(this, antiCheatBridge);
        this.checkRoomManager = new CheckRoomManager(this);
        this.reviewManager = new ReviewManager(this, checkRoomManager);
        this.banService = new BanService(this, banManager);
        this.muteManager = new MuteManager(this);
        this.muteService = new MuteService(this, muteManager);
        this.muteMenuListener = new MuteMenuListener(muteService);

        getServer().getPluginManager().registerEvents(new LoginListener(this, banManager), this);
        getServer().getPluginManager().registerEvents(new BanMenuListener(this, banManager, banService), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(reviewManager), this);
        getServer().getPluginManager().registerEvents(new MuteListener(muteManager), this);
        getServer().getPluginManager().registerEvents(new BanAnimationFreezeListener(banService), this);
        getServer().getPluginManager().registerEvents(muteMenuListener, this);
        getServer().getPluginManager().registerEvents(trustDisplayManager, this);

        LimonBanCommand command = new LimonBanCommand(this, banManager, checkRoomManager, muteMenuListener);
        getCommand("limonban").setExecutor(command);
        getCommand("limonban").setTabCompleter(command);

        trustDisplayManager.start();
        reviewManager.start();

        getLogger().info("LimonBanGUI включен. Право доступа: limonban.admin");
    }

    @Override
    public void onDisable() {
        if (trustDisplayManager != null) {
            trustDisplayManager.stopAndRemoveAll();
        }
        if (banManager != null) {
            banManager.save();
        }
        if (checkRoomManager != null) {
            checkRoomManager.save();
        }
        if (reviewManager != null) {
            reviewManager.stop();
        }
        if (muteManager != null) {
            muteManager.save();
        }
    }

    /** Игрок вышел прямо во время проверки — автоматический бан (по UUID и по IP). */
    public void handleLeaveDuringReview(Player target) {
        String reason = getConfig().getString("leave-review-ban.reason", "Лив с проверки");
        int days = getConfig().getInt("leave-review-ban.days", 7);
        banManager.ban(target.getUniqueId(), target.getName(), reason, days);
        if (target.getAddress() != null) {
            banManager.banIp(target.getAddress().getAddress().getHostAddress(), target.getName(), reason, days);
        }
        Bukkit.broadcast(BanMessages.publicBanAnnouncement(target.getName(), reason, days + " дн."));
    }

    public static LimonBanGUI getInstance() {
        return instance;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public TrustDisplayManager getTrustDisplayManager() {
        return trustDisplayManager;
    }

    public AntiCheatBridge getAntiCheatBridge() {
        return antiCheatBridge;
    }

    public CheckRoomManager getCheckRoomManager() {
        return checkRoomManager;
    }

    public ReviewManager getReviewManager() {
        return reviewManager;
    }

    public BanService getBanService() {
        return banService;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public MuteService getMuteService() {
        return muteService;
    }

    public MuteMenuListener getMuteMenuListener() {
        return muteMenuListener;
    }
}
