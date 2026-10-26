package com.sneakyreconnect;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

/** Wires {@link ReconnectSession}s to Velocity events, pings, and chat. */
final class ReconnectManager {

    static final String CANCEL_COMMAND = "/autoreconnect cancel";
    private static final PingOptions PING_OPTIONS = PingOptions.builder().timeout(Duration.ofSeconds(3)).build();

    private final ProxyServer proxy;
    private final Logger logger;
    private volatile ReconnectSettings settings;

    // Guarded by `this`. Events, ping callbacks and the ticker run on different threads.
    private final Map<UUID, Tracked> sessions = new HashMap<>();

    private static final class Tracked {
        final ReconnectSession session;
        long lastPingAt;
        boolean pingInFlight;

        Tracked(ReconnectSession session) { this.session = session; }
    }

    ReconnectManager(ProxyServer proxy, Logger logger, ReconnectSettings settings) {
        this.proxy = proxy;
        this.logger = logger;
        this.settings = settings;
    }

    void setSettings(ReconnectSettings settings) { this.settings = settings; }

    // Run last so we see the final result after other plugins have had their say.
    @Subscribe(priority = Short.MIN_VALUE)
    public void onKick(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) return;
        if (!(event.getResult() instanceof KickedFromServerEvent.RedirectPlayer)) return;

        String server = event.getServer().getServerInfo().getName();
        ReconnectSettings current = settings;
        if (!current.watches(server)) return;
        Player player = event.getPlayer();
        if (!current.permission().isEmpty() && !player.hasPermission(current.permission())) return;

        synchronized (this) {
            sessions.put(player.getUniqueId(),
                    new Tracked(new ReconnectSession(server, current, System.currentTimeMillis())));
        }
        logger.info("{} was moved off {}; waiting for it to restart", player.getUsername(), server);
    }

    @Subscribe
    public void onConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        Optional<String> current = player.getCurrentServer().map(c -> c.getServerInfo().getName());
        synchronized (this) {
            Tracked tracked = sessions.get(player.getUniqueId());
            if (tracked == null) return;
            ReconnectSession session = tracked.session;
            if (current.isPresent() && current.get().equalsIgnoreCase(session.server())) {
                // Back where they wanted to be, through us or by hand.
                end(player.getUniqueId(), tracked);
            } else if (session.onConnectedElsewhere()) {
                end(player.getUniqueId(), tracked);
                player.sendMessage(Component.text("You switched servers, so auto-reconnect to "
                        + session.server() + " was cancelled.", NamedTextColor.GRAY));
            } else {
                // Sent after landing on the fallback: chat sent mid-transfer can get lost.
                player.sendMessage(cancellable(Component.text("You'll be reconnected to "
                        + session.server() + " once it's back up. ", NamedTextColor.YELLOW)));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        synchronized (this) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    /** @return true if the player had a pending reconnect. */
    synchronized boolean cancel(Player player) {
        Tracked tracked = sessions.remove(player.getUniqueId());
        if (tracked == null) return false;
        tracked.session.finish();
        return true;
    }

    /** Runs every second. */
    synchronized void tick() {
        long now = System.currentTimeMillis();
        long pollInterval = Math.max(1000L, settings.pollIntervalMillis());
        for (Map.Entry<UUID, Tracked> entry : List.copyOf(sessions.entrySet())) {
            UUID id = entry.getKey();
            Tracked tracked = entry.getValue();
            Optional<Player> player = proxy.getPlayer(id);
            if (player.isEmpty()) {
                sessions.remove(id);
                continue;
            }
            ReconnectSession session = tracked.session;
            if (session.wantsPing() && !tracked.pingInFlight && now - tracked.lastPingAt >= pollInterval) {
                ping(player.get(), tracked);
            }
            handle(player.get(), tracked, session.onSecond(now));
        }
    }

    // Caller holds the lock.
    private void ping(Player player, Tracked tracked) {
        Optional<RegisteredServer> server = proxy.getServer(tracked.session.server());
        if (server.isEmpty()) {
            end(player.getUniqueId(), tracked);
            return;
        }
        tracked.pingInFlight = true;
        tracked.lastPingAt = System.currentTimeMillis();
        server.get().ping(PING_OPTIONS).whenComplete((ping, error) -> {
            synchronized (this) {
                tracked.pingInFlight = false;
                if (sessions.get(player.getUniqueId()) != tracked) return;
                handle(player, tracked, tracked.session.onPing(error == null, System.currentTimeMillis()));
            }
        });
    }

    // Caller holds the lock.
    private void handle(Player player, Tracked tracked, ReconnectSession.Step step) {
        ReconnectSession session = tracked.session;
        String server = session.server();
        switch (step) {
            case NONE -> { }
            case SERVER_DOWN -> player.sendMessage(cancellable(Component.text(
                    server + " is offline. Waiting for it to come back... ", NamedTextColor.GRAY)));
            case COUNTDOWN_STARTED -> {
                player.sendMessage(cancellable(Component.text(server + " is back up! Reconnecting in "
                        + session.secondsLeft() + "s. ", NamedTextColor.GREEN)));
                player.sendActionBar(countdown(server, session.secondsLeft()));
            }
            case COUNTDOWN_TICK -> player.sendActionBar(countdown(server, session.secondsLeft()));
            case CONNECT_NOW -> connect(player, tracked);
            case GAVE_UP_NO_RESTART -> {
                end(player.getUniqueId(), tracked);
                player.sendMessage(Component.text(server
                        + " didn't go offline, so it wasn't a restart. Auto-reconnect stopped.", NamedTextColor.GRAY));
            }
            case GAVE_UP_TIMEOUT -> {
                end(player.getUniqueId(), tracked);
                player.sendMessage(Component.text(server + " hasn't come back. Auto-reconnect stopped.",
                        NamedTextColor.RED));
            }
        }
    }

    // Caller holds the lock.
    private void connect(Player player, Tracked tracked) {
        String name = tracked.session.server();
        Optional<RegisteredServer> server = proxy.getServer(name);
        if (server.isEmpty()) {
            end(player.getUniqueId(), tracked);
            return;
        }
        player.sendActionBar(Component.text("Reconnecting to " + name + "...", NamedTextColor.GREEN));
        player.createConnectionRequest(server.get()).connect().whenComplete((result, error) -> {
            synchronized (this) {
                if (sessions.get(player.getUniqueId()) != tracked) return;
                if (error == null && (result.isSuccessful()
                        || result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED)) {
                    end(player.getUniqueId(), tracked);
                    return;
                }
                // Usually the server is up but still refusing joins. Go back to waiting.
                tracked.session.onConnectFailed(System.currentTimeMillis());
                tracked.lastPingAt = System.currentTimeMillis();
                Component reason = error != null
                        ? Component.text(String.valueOf(error.getMessage()))
                        : result.getReasonComponent().orElse(Component.text(result.getStatus().name()));
                player.sendMessage(cancellable(Component.text("Couldn't join " + name + " yet (", NamedTextColor.GRAY)
                        .append(reason)
                        .append(Component.text("). Retrying... "))));
            }
        });
    }

    private void end(UUID id, Tracked tracked) {
        tracked.session.finish();
        sessions.remove(id, tracked);
    }

    private static Component countdown(String server, int seconds) {
        return Component.text("Reconnecting to " + server + " in " + seconds + "...", NamedTextColor.GREEN);
    }

    /** Makes the whole line clickable to cancel, ending with a visible [Cancel] tag. */
    private static Component cancellable(Component text) {
        return text.append(Component.text("[Cancel]", NamedTextColor.RED))
                .clickEvent(ClickEvent.runCommand(CANCEL_COMMAND))
                .hoverEvent(HoverEvent.showText(Component.text("Click to stay on this server")));
    }
}
