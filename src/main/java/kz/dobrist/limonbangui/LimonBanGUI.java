package kz.dobrist.limonbangui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.banManager = new BanManager(this);
        this.antiCheatBridge = new AntiCheatBridge(this);
        this.trustDisplayManager = new TrustDisplayManager(this, antiCheatBridge);
        this.checkRoomManager = new CheckRoomManager(this);
        this.reviewManager = new ReviewManager(this, checkRoomManager);
        this.banService = new BanService(this, banManager);

        getServer().getPluginManager().registerEvents(new LoginListener(this, banManager), this);
        getServer().getPluginManager().registerEvents(new BanMenuListener(this, banManager, banService), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(reviewManager), this);
        getServer().getPluginManager().registerEvents(trustDisplayManager, this);

        LimonBanCommand command = new LimonBanCommand(this, banManager, checkRoomManager);
        getCommand("limonban").setExecutor(command);
        getCommand("limonban").setTabCompleter(command);

        trustDisplayManager.start();

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
    }

    /** Игрок вышел прямо во время проверки — автоматический бан. */
    public void handleLeaveDuringReview(Player target) {
        String reason = getConfig().getString("leave-review-ban.reason", "Лив с проверки");
        int days = getConfig().getInt("leave-review-ban.days", 7);
        banManager.ban(target.getUniqueId(), target.getName(), reason, days);
        Bukkit.broadcast(Component.text("[LimonBanGUI] " + target.getName()
                + " покинул(а) сервер во время проверки — автобан на " + days + " дней", NamedTextColor.RED));
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
}
