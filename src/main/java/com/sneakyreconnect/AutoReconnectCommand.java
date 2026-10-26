package com.sneakyreconnect;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** {@code /autoreconnect cancel} for players, {@code /autoreconnect reload} for admins. */
final class AutoReconnectCommand implements SimpleCommand {

    static final String RELOAD_PERMISSION = "sneakyreconnect.reload";

    private final SneakyReconnect plugin;

    AutoReconnectCommand(SneakyReconnect plugin) { this.plugin = plugin; }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        String sub = args.length == 0 ? "cancel" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cancel" -> {
                if (!(invocation.source() instanceof Player player)) {
                    invocation.source().sendMessage(Component.text("Only players can cancel a reconnect.", NamedTextColor.RED));
                    return;
                }
                boolean cancelled = plugin.manager().cancel(player);
                player.sendMessage(cancelled
                        ? Component.text("Auto-reconnect cancelled.", NamedTextColor.YELLOW)
                        : Component.text("No auto-reconnect is pending.", NamedTextColor.GRAY));
            }
            case "reload" -> {
                if (!invocation.source().hasPermission(RELOAD_PERMISSION)) {
                    invocation.source().sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.reloadSettings();
                invocation.source().sendMessage(ok
                        ? Component.text("SneakyReconnect config reloaded.", NamedTextColor.GREEN)
                        : Component.text("Config reload failed; see the proxy log.", NamedTextColor.RED));
            }
            default -> invocation.source().sendMessage(Component.text("Usage: /autoreconnect <cancel|reload>", NamedTextColor.RED));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1) return List.of();
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> options = invocation.source().hasPermission(RELOAD_PERMISSION)
                ? List.of("cancel", "reload")
                : List.of("cancel");
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
