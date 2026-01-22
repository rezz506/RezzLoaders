package com.rezzcraft.rezzloaders;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LoaderCommand implements CommandExecutor, TabCompleter {

    private final RezzLoadersPlugin plugin;
    private final LoaderManager manager;

    public LoaderCommand(RezzLoadersPlugin plugin, LoaderManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission("rezzloaders.give") && !sender.hasPermission("rezzloaders.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /loader give <player> <duration> <1x1|5x5> [amount]");
                    sender.sendMessage(ChatColor.GRAY + "Duration examples: 1h, 24h, 7d, 30m");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
                long durationSec = parseDurationSeconds(args[2]);
                if (durationSec <= 0) {
                    sender.sendMessage(ChatColor.RED + "Invalid duration. Examples: 1h, 24h, 7d, 30m");
                    return true;
                }
                LoaderSize size = LoaderSize.fromString(args[3]);
                if (size == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid size. Use 1x1 or 5x5.");
                    return true;
                }
                int amount = 1;
                if (args.length >= 5) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[4]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                ItemStack item = LoaderItems.createLoaderItem(plugin, durationSec, size);
                item.setAmount(Math.min(64, amount));
                Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Target inventory full; some items could not be added.");
                }
                sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a " + LoaderItems.sizeLabel(size) + " loader for " + LoaderItems.formatDuration(durationSec) + ".");
                target.sendMessage(ChatColor.GOLD + "You received a " + LoaderItems.sizeLabel(size) + " Chunk Loader (" + LoaderItems.formatDuration(durationSec) + "). Place it to activate!");
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("rezzloaders.list") && !sender.hasPermission("rezzloaders.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                UUID who = null;
                if (args.length >= 2) {
                    Player p = Bukkit.getPlayerExact(args[1]);
                    if (p != null) who = p.getUniqueId();
                } else if (sender instanceof Player p) {
                    who = p.getUniqueId();
                }
                if (who == null) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /loader list <player>");
                    return true;
                }
                List<LoaderRecord> list = manager.getLoadersForOwner(who);
                sender.sendMessage(ChatColor.AQUA + "Active loaders: " + list.size());
                for (LoaderRecord r : list) {
                    long remaining = Math.max(0L, (r.expiresAtMs - System.currentTimeMillis()) / 1000L);
                    sender.sendMessage(ChatColor.GRAY + "- " + r.id + " " + r.world + " " + r.x + "," + r.y + "," + r.z + " " + LoaderItems.sizeLabel(r.size) + " remaining " + LoaderItems.formatDuration(remaining));
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("rezzloaders.remove") && !sender.hasPermission("rezzloaders.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /loader remove <id>");
                    return true;
                }
                try {
                    UUID id = UUID.fromString(args[1]);
                    boolean ok = manager.removeById(id, sender.getName());
                    sender.sendMessage(ok ? ChatColor.GREEN + "Removed loader." : ChatColor.RED + "Loader not found.");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid UUID.");
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("rezzloaders.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                plugin.reloadConfig();
                manager.reloadFromConfig();
                sender.sendMessage(ChatColor.GREEN + "RezzLoaders reloaded.");
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "RezzLoaders commands:");
        sender.sendMessage(ChatColor.YELLOW + "/loader give <player> <duration> <1x1|5x5> [amount]" + ChatColor.GRAY + " - Give a timed loader item");
        sender.sendMessage(ChatColor.YELLOW + "/loader list [player]" + ChatColor.GRAY + " - List active loaders");
        sender.sendMessage(ChatColor.YELLOW + "/loader remove <id>" + ChatColor.GRAY + " - Remove a loader by id");
        sender.sendMessage(ChatColor.YELLOW + "/loader reload" + ChatColor.GRAY + " - Reload config (admin)");
        sender.sendMessage(ChatColor.GRAY + "Place the item to activate. Right-click to view info.");
    }

    static long parseDurationSeconds(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1));
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600L;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86400L;
            // default: hours
            return Long.parseLong(s) * 3600L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // formatDuration/sizeLabel live in LoaderItems now.

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], Arrays.asList("help", "give", "list", "remove", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // let Paper suggest online players
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return partial(args[2], Arrays.asList("1h", "24h", "7d", "30m"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return partial(args[3], Arrays.asList("1x1", "5x5"));
        }
        return Collections.emptyList();
    }

    private static List<String> partial(String token, List<String> options) {
        if (token == null) token = "";
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
