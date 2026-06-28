package org.pinnaclesmp.pinnaclestats;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PStatsCommand implements CommandExecutor, TabCompleter {
    private final PinnacleStatsPlugin plugin;

    public PStatsCommand(PinnacleStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pinnaclestats.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadEverything();
                sender.sendMessage("§aPinnacleStats config reloaded and API restarted.");
                return true;
            }
            case "refresh" -> {
                if (args.length >= 2) {
                    plugin.refreshOneAsync(args[1]);
                    sender.sendMessage("§aRefreshing stats for §f" + args[1] + "§a.");
                } else {
                    plugin.refreshAsync();
                    sender.sendMessage("§aRefreshing all cached player stats.");
                }
                return true;
            }
            case "export", "publish" -> {
                StatsExporter.ExportResult result = plugin.exportNow();
                if (result.success()) {
                    sender.sendMessage("§aPinnacleStats export complete. Files: §f" + result.fileCount() + "§a. " + result.message());
                } else {
                    sender.sendMessage("§cPinnacleStats export failed: " + result.message());
                }
                return true;
            }
            case "debug" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /pstats debug <player|uuid>");
                    return true;
                }
                sendDebug(sender, args[1]);
                return true;
            }
            default -> {
                sender.sendMessage("§eUsage: /pstats <status|reload|refresh|export|publish|debug>");
                return true;
            }
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§6PinnacleStats Status");
        sender.sendMessage("§7API running: §f" + plugin.apiServer().isRunning());
        sender.sendMessage("§7API port: §f" + plugin.settings().apiPort());
        sender.sendMessage("§7Stats folder: §f" + plugin.statsCache().statsFolder().getPath());
        sender.sendMessage("§7Loaded players: §f" + plugin.statsCache().size());
        sender.sendMessage("§7Last refresh: §f" + plugin.statsCache().lastRefresh());
        if (plugin.statsExporter() != null) {
            sender.sendMessage("§7Last export: §f" + plugin.statsExporter().lastExport());
            sender.sendMessage("§7GitHub publish: §f" + plugin.statsExporter().lastPublishResult());
            if (!plugin.statsExporter().lastExportError().isBlank()) {
                sender.sendMessage("§cLast export error: " + plugin.statsExporter().lastExportError());
            }
        }
        if (!plugin.statsCache().lastError().isBlank()) {
            sender.sendMessage("§cLast error: " + plugin.statsCache().lastError());
        }
    }

    private void sendDebug(CommandSender sender, String id) {
        var uuid = tryUuid(id);
        var profile = uuid == null ? plugin.statsCache().findByName(id) : plugin.statsCache().findByUuid(uuid);
        if (profile.isEmpty()) {
            sender.sendMessage("§cNo cached stats found for " + id + ". Try /pstats refresh first.");
            return;
        }
        StatsProfile p = profile.get();
        sender.sendMessage("§6PinnacleStats Debug");
        sender.sendMessage("§7Name: §f" + p.name());
        sender.sendMessage("§7UUID: §f" + p.uuid());
        sender.sendMessage("§7Updated: §f" + p.lastUpdated());
        sender.sendMessage("§7API URL: §f/api/player/" + p.name());
        sender.sendMessage("§7Static JSON: §fassets/player-stats/players/" + p.name().replaceAll("[^A-Za-z0-9_\\-]", "_") + ".json");
    }

    private UUID tryUuid(String text) {
        try { return UUID.fromString(text); } catch (Exception ignored) { return null; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pinnaclestats.admin")) return List.of();
        if (args.length == 1) {
            return filter(Arrays.asList("status", "reload", "refresh", "export", "publish", "debug"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("refresh") || args[0].equalsIgnoreCase("debug"))) {
            List<String> names = new ArrayList<>();
            for (StatsProfile profile : plugin.statsCache().allProfiles()) names.add(profile.name());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(value);
        }
        return out;
    }
}
