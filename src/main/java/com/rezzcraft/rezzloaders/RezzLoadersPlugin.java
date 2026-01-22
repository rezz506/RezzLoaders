package com.rezzcraft.rezzloaders;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class RezzLoadersPlugin extends JavaPlugin {

    public NamespacedKey KEY_ITEM;
    public NamespacedKey KEY_DURATION_SEC;
    public NamespacedKey KEY_SIZE;

    private LoaderManager loaderManager;
    private ActionLogger actionLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        KEY_ITEM = new NamespacedKey(this, "loader_item");
        KEY_DURATION_SEC = new NamespacedKey(this, "duration_sec");
        KEY_SIZE = new NamespacedKey(this, "size");

        this.actionLogger = new ActionLogger(this);
        this.loaderManager = new LoaderManager(this, actionLogger);

        getServer().getPluginManager().registerEvents(new LoaderListener(this, loaderManager, actionLogger), this);
        LoaderCommand cmd = new LoaderCommand(this, loaderManager);
        if (getCommand("loader") != null) {
            getCommand("loader").setExecutor(cmd);
            getCommand("loader").setTabCompleter(cmd);
        }

        loaderManager.loadAll();
        loaderManager.startTasks();

        getLogger().info("RezzLoaders enabled.");
    }

    @Override
    public void onDisable() {
        try {
            if (loaderManager != null) loaderManager.shutdown();
        } finally {
            if (actionLogger != null) actionLogger.close();
        }
        getLogger().info("RezzLoaders disabled.");
    }
}
