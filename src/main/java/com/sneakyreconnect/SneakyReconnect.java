package com.sneakyreconnect;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

@Plugin(
        id = "sneakyreconnect",
        name = "SneakyReconnect",
        version = BuildInfo.VERSION,
        description = "Sends players back to a server after it restarts",
        url = "https://github.com/Team-Sneakymouse/SneakyReconnect",
        authors = {"Team Sneakymouse"})
public final class SneakyReconnect {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private ReconnectManager manager;

    @Inject
    public SneakyReconnect(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        ReconnectSettings settings;
        try {
            settings = loadSettings();
        } catch (IOException | RuntimeException e) {
            logger.error("Couldn't load config.yml; using defaults", e);
            settings = ReconnectSettings.defaults();
        }
        manager = new ReconnectManager(proxy, logger, settings);
        proxy.getEventManager().register(this, manager);
        proxy.getScheduler().buildTask(this, manager::tick).repeat(Duration.ofSeconds(1)).schedule();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("autoreconnect").plugin(this).build(),
                new AutoReconnectCommand(this));
        logger.info("Watching servers for restarts: {}", settings.servers());
    }

    ReconnectManager manager() { return manager; }

    boolean reloadSettings() {
        try {
            ReconnectSettings settings = loadSettings();
            manager.setSettings(settings);
            logger.info("Reloaded; watching servers: {}", settings.servers());
            return true;
        } catch (IOException | RuntimeException e) {
            logger.error("Couldn't reload config.yml", e);
            return false;
        }
    }

    private ReconnectSettings loadSettings() throws IOException {
        Path file = dataDirectory.resolve("config.yml");
        if (Files.notExists(file)) {
            Files.createDirectories(dataDirectory);
            try (InputStream defaults = SneakyReconnect.class.getResourceAsStream("/config.yml")) {
                Files.copy(defaults, file);
            }
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> yaml = new Yaml().load(reader);
            return ReconnectSettings.fromMap(yaml == null ? Map.of() : yaml);
        }
    }
}
