package jfbdev.jcmddelay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JCMDDelay extends JavaPlugin {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smh])");

    private final ConsoleCommandSender console = Bukkit.getConsoleSender();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getLogger().info("JCMDDelay успешно загружен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("JCMDDelay выгружен.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (label.equalsIgnoreCase("jcmddreload")) {
            if (!sender.hasPermission("jcmddelay.reload")) {
                sendMsg(sender, "messages.no-permission");
                return true;
            }

            reloadConfig();
            config = getConfig();
            sendMsg(sender, "messages.reload-success");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String timeStr = args[args.length - 1];
        long delayTicks = parseTimeToTicks(timeStr);
        if (delayTicks <= 0) {
            sendMsg(sender, "messages.invalid-time");
            return true;
        }

        String commandToExecute = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)).trim();

        if (commandToExecute.isEmpty()) {
            sendMsg(sender, "messages.empty-command");
            return true;
        }

        String formattedTime = formatTimeDisplay(timeStr);
        sendMsg(sender, "messages.scheduled", "%command%", commandToExecute, "%time%", formattedTime);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean executed = false;
            try {
                CommandMap commandMap = Bukkit.getServer().getCommandMap();
                executed = commandMap.dispatch(console, commandToExecute);
            } catch (Exception e) {
                getLogger().warning("Ошибка при выполнении отложенной команды: " + commandToExecute);
                getLogger().warning(e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            if (!executed) {
                getLogger().warning("Отложенная команда не была обработана плагином: " + commandToExecute);
            }

            String executedMsg = getLegacyMsg("messages.executed", "%command%", commandToExecute);
            if (sender instanceof Player player && player.isOnline()) {
                player.sendMessage(executedMsg);
            } else {
                getLogger().info(ChatColor.stripColor(executedMsg));
            }
        }, delayTicks);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        for (String line : config.getStringList("usage")) {
            if (line.isEmpty()) {
                sender.sendMessage("");
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
    }

    private void sendMsg(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(getLegacyMsg(path, replacements));
    }

    private String getLegacyMsg(String path, String... replacements) {
        String raw = config.getString(path, "&cСообщение не найдено: " + path);
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private long parseTimeToTicks(String input) {
        long totalSeconds = 0;
        String lower = input.toLowerCase();
        Matcher matcher = TIME_PATTERN.matcher(lower);

        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            totalSeconds += switch (unit) {
                case "s" -> value;
                case "m" -> value * 60;
                case "h" -> value * 3600;
                default -> 0;
            };
        }

        if (totalSeconds == 0) {
            try {
                totalSeconds = Long.parseLong(lower.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        if (totalSeconds <= 0) {
            return 0;
        }

        return totalSeconds * 20L;
    }

    private String formatTimeDisplay(String input) {
        long totalSeconds = parseTimeToTicks(input) / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append(" ").append(getTimeUnit(hours, "hours")).append(" ");
        if (minutes > 0) sb.append(minutes).append(" ").append(getTimeUnit(minutes, "minutes")).append(" ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append(" ").append(getTimeUnit(seconds, "seconds"));
        return sb.toString().trim();
    }

    private String getTimeUnit(long value, String unitKey) {
        String one = config.getString("time-units." + unitKey + ".one", "");
        String few = config.getString("time-units." + unitKey + ".few", "");
        String many = config.getString("time-units." + unitKey + ".many", "");

        if (value % 10 == 1 && value % 100 != 11) return one;
        if (value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20)) return few;
        return many;
    }
}