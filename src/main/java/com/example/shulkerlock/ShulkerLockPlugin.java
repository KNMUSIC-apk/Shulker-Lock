package com.example.shulkerlock;

import org.bukkit.plugin.java.JavaPlugin;

public class ShulkerLockPlugin extends JavaPlugin {

    private static ShulkerLockPlugin instance;
    private LockManager lockManager;

    @Override
    public void onEnable() {
        instance = this;
        lockManager = new LockManager(this);
        lockManager.load();

        getServer().getPluginManager().registerEvents(new ShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        getCommand("shulkermenu").setExecutor(new MenuCommand(this));
        getCommand("sm").setExecutor(new MenuCommand(this));
    }

    @Override
    public void onDisable() {
        if (lockManager != null) lockManager.save();
    }

    public static ShulkerLockPlugin getInstance() {
        return instance;
    }

    public LockManager getLockManager() {
        return lockManager;
    }
}
