package com.sneakyreconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sneakyreconnect.ReconnectSession.Phase;
import com.sneakyreconnect.ReconnectSession.Step;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReconnectSessionTest {

    private static final ReconnectSettings SETTINGS = ReconnectSettings.fromMap(Map.of(
            "countdown-seconds", 3,
            "shutdown-timeout-seconds", 60,
            "startup-timeout-seconds", 600));

    private ReconnectSession session() {
        return new ReconnectSession("dev", SETTINGS, 0);
    }

    @Test
    void fullRestartCountsDownThenConnects() {
        ReconnectSession s = session();
        assertEquals(Step.NONE, s.onPing(true, 1_000)); // still shutting down
        assertEquals(Step.SERVER_DOWN, s.onPing(false, 3_000));
        assertEquals(Step.NONE, s.onPing(false, 30_000));
        assertEquals(Step.COUNTDOWN_STARTED, s.onPing(true, 40_000));
        assertEquals(3, s.secondsLeft());
        assertFalse(s.wantsPing());
        assertEquals(Step.COUNTDOWN_TICK, s.onSecond(41_000));
        assertEquals(Step.COUNTDOWN_TICK, s.onSecond(42_000));
        assertEquals(Step.CONNECT_NOW, s.onSecond(43_000));
        assertEquals(Phase.CONNECTING, s.phase());
    }

    @Test
    void doesNotReconnectWhileServerIsStillShuttingDown() {
        ReconnectSession s = session();
        for (long t = 0; t < 60_000; t += 2_000) {
            assertEquals(Step.NONE, s.onPing(true, t));
        }
        assertEquals(Phase.AWAITING_SHUTDOWN, s.phase());
    }

    @Test
    void givesUpIfServerNeverGoesDown() {
        ReconnectSession s = session();
        assertEquals(Step.GAVE_UP_NO_RESTART, s.onPing(true, 60_000));
        assertTrue(s.isFinished());
    }

    @Test
    void givesUpIfServerNeverComesBack() {
        ReconnectSession s = session();
        s.onPing(false, 1_000);
        assertEquals(Step.NONE, s.onPing(false, 500_000));
        assertEquals(Step.GAVE_UP_TIMEOUT, s.onPing(false, 601_000));
        assertTrue(s.isFinished());
    }

    @Test
    void failedConnectGoesBackToWaiting() {
        ReconnectSession s = session();
        s.onPing(false, 1_000);
        s.onPing(true, 2_000);
        s.onSecond(3_000);
        s.onSecond(4_000);
        s.onSecond(5_000);
        s.onConnectFailed(5_500);
        assertEquals(Phase.AWAITING_STARTUP, s.phase());
        assertTrue(s.wantsPing());
        assertEquals(Step.COUNTDOWN_STARTED, s.onPing(true, 8_000));
    }

    @Test
    void zeroCountdownConnectsImmediately() {
        ReconnectSession s = new ReconnectSession("dev", ReconnectSettings.fromMap(Map.of("countdown-seconds", 0)), 0);
        s.onPing(false, 1_000);
        assertEquals(Step.CONNECT_NOW, s.onPing(true, 2_000));
    }

    @Test
    void firstConnectionIsTheFallbackLaterOnesAreManual() {
        ReconnectSession s = session();
        assertFalse(s.onConnectedElsewhere()); // proxy moved them to live
        assertTrue(s.onConnectedElsewhere()); // player used /server
    }

    @Test
    void countdownDoesNothingOutsideCountdownPhase() {
        ReconnectSession s = session();
        assertEquals(Step.NONE, s.onSecond(1_000));
        s.finish();
        assertEquals(Step.NONE, s.onPing(false, 2_000));
    }
}
