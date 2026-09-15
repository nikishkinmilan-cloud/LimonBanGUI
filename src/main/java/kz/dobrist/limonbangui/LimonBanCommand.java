package kz.dobrist.limonbangui;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class LimonBanCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "Использование: /limonban reload | unban <ник> | mute | unmute <ник> | room set | room tp <ник> | room return <ник>";

    private final LimonBanGUI plugin;
    private final BanManager banManager;
    private final CheckRoomManager checkRoomManager;
    private final MuteMenuListener muteMenuListener;

    public LimonBanCommand(LimonBanGUI plugin, BanManager banManager, CheckRoomManager checkRoomManager, MuteMenuListener muteMenuListener) {
        this.plugin = plugin;
        this.banManager = banManager;
        this.checkRoomManager = checkRoomManager;
        this.muteMenuListener = muteMenuListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("LimonBanGUI: конфиг перезагружен.");
            }
            case "unban" -> {
                if (args.length < 2) {
                    sender.sendMessage("Использование: /limonban unban <ник>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                banManager.unban(target.getUniqueId());
                sender.sendMessage("Снят бан с " + args[1] + " (если он был).");
            }
            case "mute" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Команда доступна только в игре.");
                    return true;
                }
                if (!p.hasPermission("limonban.admin")) {
                    sender.sendMessage("Недостаточно прав.");
                    return true;
                }
                muteMenuListener.openPicker(p);
            }
            case "unmute" -> {
                if (args.length < 2) {
                    sender.sendMessage("Использование: /limonban unmute <ник>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                plugin.getMuteManager().unmute(target.getUniqueId());
                sender.sendMessage("Снят мут с " + args[1] + " (если он был).");
            }
            case "room" -> handleRoom(sender, args);
            default -> sender.sendMessage(USAGE);
        }
        return true;
    }

    private void handleRoom(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Использование: /limonban room set | room tp <ник> | room return <ник>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Команду можно выполнить только находясь в игре, на месте будущей комнаты.");
                    return;
                }
                checkRoomManager.setRoom(p.getLocation());
                sender.sendMessage("Комната проверки установлена в текущей точке.");
            }
            case "tp" -> {
                if (args.length < 3) {
                    sender.sendMessage("Использование: /limonban room tp <ник>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("Игрок не в сети.");
                    return;
                }
                if (!checkRoomManager.hasRoom()) {
                    sender.sendMessage("Комната ещё не установлена. Сначала /limonban room set.");
                    return;
                }
                checkRoomManager.sendToRoom(target);
                sender.sendMessage(target.getName() + " отправлен(а) в комнату проверки.");
            }
            case "return" -> {
                if (args.length < 3) {
                    sender.sendMessage("Использование: /limonban room return <ник>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("Игрок не в сети.");
                    return;
                }
                boolean ok = checkRoomManager.returnFromRoom(target);
                sender.sendMessage(ok ? (target.getName() + " возвращён(а) обратно.") : "Для этого игрока нет сохранённой точки возврата.");
            }
            default -> sender.sendMessage("Использование: /limonban room set | room tp <ник> | room return <ник>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "unban", "mute", "unmute", "room");
        if (args.length == 2 && (args[0].equalsIgnoreCase("unban") || args[0].equalsIgnoreCase("unmute"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("room")) {
            return List.of("set", "tp", "return");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("room")
                && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("return"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
