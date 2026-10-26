package com.sneakyreconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ReconnectSettingsTest {

    @Test
    void bundledConfigMatchesDefaults() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            Map<String, Object> yaml = new Yaml().load(in);
            assertEquals(ReconnectSettings.defaults(), ReconnectSettings.fromMap(yaml));
        }
    }

    @Test
    void serverNamesAreCaseInsensitive() {
        ReconnectSettings s = ReconnectSettings.fromMap(Map.of("servers", List.of("Dev", "build")));
        assertTrue(s.watches("dev"));
        assertTrue(s.watches("BUILD"));
        assertFalse(s.watches("live"));
    }

    @Test
    void secondsBecomeMillis() {
        ReconnectSettings s = ReconnectSettings.fromMap(Map.of("poll-interval-seconds", 3, "startup-timeout-seconds", 10));
        assertEquals(3_000, s.pollIntervalMillis());
        assertEquals(10_000, s.startupTimeoutMillis());
    }
}
