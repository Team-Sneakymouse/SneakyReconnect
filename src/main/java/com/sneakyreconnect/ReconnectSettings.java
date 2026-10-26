package com.sneakyreconnect;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Values from {@code config.yml}. */
public record ReconnectSettings(
        Set<String> servers,
        String permission,
        int countdownSeconds,
        long pollIntervalMillis,
        long shutdownTimeoutMillis,
        long startupTimeoutMillis) {

    public static ReconnectSettings defaults() {
        return fromMap(Map.of());
    }

    public boolean watches(String server) {
        return servers.contains(server.toLowerCase(Locale.ROOT));
    }

    /** Builds settings from parsed YAML, falling back to defaults for missing keys. */
    public static ReconnectSettings fromMap(Map<String, Object> yaml) {
        Object rawServers = yaml.getOrDefault("servers", List.of("dev"));
        Set<String> servers = rawServers instanceof List<?> list
                ? list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet())
                : Set.of(String.valueOf(rawServers).toLowerCase(Locale.ROOT));
        Object permission = yaml.get("permission");
        return new ReconnectSettings(
                servers,
                permission == null ? "" : permission.toString().trim(),
                intValue(yaml, "countdown-seconds", 5),
                intValue(yaml, "poll-interval-seconds", 2) * 1000L,
                intValue(yaml, "shutdown-timeout-seconds", 60) * 1000L,
                intValue(yaml, "startup-timeout-seconds", 600) * 1000L);
    }

    private static int intValue(Map<String, Object> yaml, String key, int fallback) {
        return yaml.get(key) instanceof Number n ? n.intValue() : fallback;
    }
}
