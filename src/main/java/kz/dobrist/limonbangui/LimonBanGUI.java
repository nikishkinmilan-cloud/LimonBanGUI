package kz.dobrist.limonbangui;

import org.bukkit.plugin.java.JavaPlugin;

public final class LimonBanGUI extends JavaPlugin {

    private static LimonBanGUI instance;

    private BanManager banManager;
    private TrustDisplayManager trustDisplayManager;
    private AntiCheatBridge antiCheatBridge;
    private CheckRoomManager checkRoomManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.banManager = new BanManager(this);
        this.antiCheatBridge = new AntiCheatBridge(this);
        this.trustDisplayManager = new TrustDisplayManager(this, antiCheatBridge);
        this.checkRoomManager = new CheckRoomManager(this);

        getServer().getPluginManager().registerEvents(new LoginListener(banManager), this);
        getServer().getPluginManager().registerEvents(new BanMenuListener(this, banManager), this);
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
}
