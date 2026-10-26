package com.sneakyreconnect;

/**
 * Tracks one player's wait for a server restart. No Velocity types, so it can be unit tested.
 *
 * <p>Flow: the player is kicked from the server and moved to a fallback. We wait until the server
 * stops answering pings (so we don't bounce back into a server that's still shutting down), then
 * until it answers again, then wait a short delay (so the player can cancel) and reconnect.
 */
public final class ReconnectSession {

    public enum Phase { AWAITING_SHUTDOWN, AWAITING_STARTUP, DELAY, CONNECTING, FINISHED }

    /** What the caller should tell the player or do next. */
    public enum Step {
        NONE,
        SERVER_DOWN,
        SERVER_UP,
        CONNECT_NOW,
        GAVE_UP_NO_RESTART,
        GAVE_UP_TIMEOUT,
    }

    private final String server;
    private final ReconnectSettings settings;
    private Phase phase = Phase.AWAITING_SHUTDOWN;
    private long phaseStartedAt;
    private boolean landedOnFallback;

    public ReconnectSession(String server, ReconnectSettings settings, long nowMillis) {
        this.server = server;
        this.settings = settings;
        this.phaseStartedAt = nowMillis;
    }

    public String server() { return server; }

    public Phase phase() { return phase; }

    /** Seconds between the "back up" message and the reconnect. */
    public int delaySeconds() { return settings.reconnectDelaySeconds(); }

    public boolean isFinished() { return phase == Phase.FINISHED; }

    public boolean wantsPing() {
        return phase == Phase.AWAITING_SHUTDOWN || phase == Phase.AWAITING_STARTUP;
    }

    /**
     * Called when the player finishes connecting somewhere. The first connection after the kick
     * is the proxy's own fallback; any later one means the player moved on purpose.
     *
     * @return true if the player switched servers themselves and the session should end
     */
    public boolean onConnectedElsewhere() {
        if (!landedOnFallback) {
            landedOnFallback = true;
            return false;
        }
        return phase != Phase.CONNECTING;
    }

    public Step onPing(boolean online, long nowMillis) {
        switch (phase) {
            case AWAITING_SHUTDOWN -> {
                if (!online) {
                    enter(Phase.AWAITING_STARTUP, nowMillis);
                    return Step.SERVER_DOWN;
                }
                if (nowMillis - phaseStartedAt >= settings.shutdownTimeoutMillis()) {
                    enter(Phase.FINISHED, nowMillis);
                    return Step.GAVE_UP_NO_RESTART;
                }
            }
            case AWAITING_STARTUP -> {
                if (online) return serverUp(nowMillis);
                if (nowMillis - phaseStartedAt >= settings.startupTimeoutMillis()) {
                    enter(Phase.FINISHED, nowMillis);
                    return Step.GAVE_UP_TIMEOUT;
                }
            }
            default -> { }
        }
        return Step.NONE;
    }

    /** Called regularly (once per second); returns CONNECT_NOW once the delay has passed. */
    public Step onTick(long nowMillis) {
        if (phase != Phase.DELAY) return Step.NONE;
        if (nowMillis - phaseStartedAt < settings.reconnectDelaySeconds() * 1000L) return Step.NONE;
        enter(Phase.CONNECTING, nowMillis);
        return Step.CONNECT_NOW;
    }

    /** The reconnect attempt failed (server probably still starting); keep waiting. */
    public void onConnectFailed(long nowMillis) {
        if (phase == Phase.CONNECTING) enter(Phase.AWAITING_STARTUP, nowMillis);
    }

    public void finish() {
        phase = Phase.FINISHED;
    }

    private Step serverUp(long nowMillis) {
        if (settings.reconnectDelaySeconds() <= 0) {
            enter(Phase.CONNECTING, nowMillis);
            return Step.CONNECT_NOW;
        }
        enter(Phase.DELAY, nowMillis);
        return Step.SERVER_UP;
    }

    private void enter(Phase next, long nowMillis) {
        phase = next;
        phaseStartedAt = nowMillis;
    }
}
