package com.rezzcraft.rezzloaders;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ActionLogger {

    private final JavaPlugin plugin;
    private final boolean fileEnabled;
    private final File logFile;
    private BufferedWriter writer;

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public ActionLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fileEnabled = plugin.getConfig().getBoolean("logging.file-enabled", true);
        String fname = plugin.getConfig().getString("logging.file-name", "actions.log");

        File dir = new File(plugin.getDataFolder(), "logs");
        if (!dir.exists()) dir.mkdirs();

        this.logFile = new File(dir, fname);

        if (fileEnabled) {
            try {
                this.writer = new BufferedWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to open action log file: " + e.getMessage());
            }
        }
    }

    public synchronized void log(String message) {
        log("INFO", message);
    }

    public synchronized void log(String event, String details) {
        String line = "[" + TS.format(Instant.now()) + "] [" + event + "] " + details;
        plugin.getLogger().info(line);

        if (!fileEnabled || writer == null) return;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write action log: " + e.getMessage());
        }
    }

    public synchronized void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }
}
