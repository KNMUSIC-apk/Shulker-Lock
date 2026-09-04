package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {

    private final ShulkerLockPlugin plugin;
    private final File dataFile;
    private FileConfiguration config;

    // player UUID -> slot (1-3) -> BlockKey
    private final Map<UUID, Map<Integer, BlockKey>> playerLocks = new ConcurrentHashMap<>();
    // BlockKey -> LockData (owner, slot)
    private final Map<BlockKey, LockData> lockLookup = new ConcurrentHashMap<>();

    public LockManager(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "locks.yml");
        // Tạo thư mục và file nếu chưa có
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create locks.yml: " + e.getMessage());
            }
        }
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(dataFile);
        playerLocks.clear();
        lockLookup.clear();

        if (config.contains("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<Integer, BlockKey> slots = new HashMap<>();
                for (String slotStr : config.getConfigurationSection("players." + uuidStr).getKeys(false)) {
                    int slot = Integer.parseInt(slotStr);
                    String locStr = config.getString("players." + uuidStr + "." + slotStr);
                    if (locStr == null) continue;
                    BlockKey key = BlockKey.fromString(locStr);
                    if (key != null) {
                        slots.put(slot, key);
                        lockLookup.put(key, new LockData(uuid, slot));
                    }
                }
                if (!slots.isEmpty()) {
                    playerLocks.put(uuid, slots);
                }
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<Integer, BlockKey>> entry : playerLocks.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<Integer, BlockKey> slotEntry : entry.getValue().entrySet()) {
                config.set("players." + uuidStr + "." + slotEntry.getKey(), slotEntry.getValue().toString());
            }
        }
        try {
            config.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save lock data: " + e.getMessage());
        }
    }

    // Đồng bộ hóa để tránh xung đột
    public synchronized boolean lockShulker(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        BlockKey key = new BlockKey(block.getLocation());
        if (lockLookup.containsKey(key)) {
            String msg = plugin.getPluginConfig().getString("already-locked-message", "&cThis shulker is already locked!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        Map<Integer, BlockKey> slots = playerLocks.computeIfAbsent(uuid, k -> new HashMap<>());
        int maxLocks = plugin.getPluginConfig().getInt("max-locks", 3);
        if (slots.size() >= maxLocks) {
            String msg = plugin.getPluginConfig().getString("max-locks-message", "&cYou already locked {max} shulkers! Unlock one first.");
            msg = msg.replace("{max}", String.valueOf(maxLocks));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        int slot = 1;
        while (slots.containsKey(slot)) slot++;

        slots.put(slot, key);
        lockLookup.put(key, new LockData(uuid, slot));

        if (block.getState() instanceof ShulkerBox shulker) {
            String format = plugin.getPluginConfig().getString("lock-display-name", "&6Shulker Lock &f[{player}] &e#{slot}");
            String name = ChatColor.translateAlternateColorCodes('&',
                    format.replace("{player}", player.getName()).replace("{slot}", String.valueOf(slot)));
            shulker.setCustomName(name);
            shulker.update();
        }

        save();

        String successMsg = plugin.getPluginConfig().getString("lock-success-message", "&aShulker locked successfully! (#{slot})");
        successMsg = successMsg.replace("{slot}", String.valueOf(slot));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
        return true;
    }

    public synchronized boolean unlockShulker(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        Map<Integer, BlockKey> slots = playerLocks.get(uuid);
        if (slots == null || !slots.containsKey(slot)) {
            String msg = plugin.getPluginConfig().getString("empty-slot-message", "&cYou don't have a lock in slot #" + slot);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        BlockKey key = slots.remove(slot);
        lockLookup.remove(key);

        Block block = key.toBlock();
        if (block != null && block.getState() instanceof ShulkerBox shulker) {
            shulker.setCustomName(null);
            shulker.update();
        }

        if (slots.isEmpty()) {
            playerLocks.remove(uuid);
        }

        save();

        String successMsg = plugin.getPluginConfig().getString("unlock-success-message", "&aShulker #{slot} unlocked!");
        successMsg = successMsg.replace("{slot}", String.valueOf(slot));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
        return true;
    }

    public boolean isLocked(Block block) {
        return lockLookup.containsKey(new BlockKey(block.getLocation()));
    }

    public UUID getOwner(Block block) {
        LockData data = lockLookup.get(new BlockKey(block.getLocation()));
        return data != null ? data.owner : null;
    }

    public int getSlot(Block block) {
        LockData data = lockLookup.get(new BlockKey(block.getLocation()));
        return data != null ? data.slot : -1;
    }

    public Map<Integer, BlockKey> getPlayerSlots(Player player) {
        return playerLocks.getOrDefault(player.getUniqueId(), new HashMap<>());
    }

    // Lấy thông tin vị trí của một slot để hiển thị trong menu
    public String getSlotLocation(Player player, int slot) {
        Map<Integer, BlockKey> slots = playerLocks.get(player.getUniqueId());
        if (slots == null || !slots.containsKey(slot)) return null;
        BlockKey key = slots.get(slot);
        Block block = key.toBlock();
        if (block == null) return null;
        return block.getWorld().getName() + " at " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    }

    private static class LockData {
        UUID owner;
        int slot;
        LockData(UUID owner, int slot) {
            this.owner = owner;
            this.slot = slot;
        }
    }

    public static class BlockKey {
        private final String world;
        private final int x, y, z;

        public BlockKey(Location loc) {
            this.world = loc.getWorld().getName();
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
        }

        public BlockKey(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Block toBlock() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return w.getBlockAt(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey)) return false;
            BlockKey that = (BlockKey) o;
            return x == that.x && y == that.y && z == that.z && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }

        @Override
        public String toString() {
            return world + "," + x + "," + y + "," + z;
        }

        public static BlockKey fromString(String s) {
            String[] parts = s.split(",");
            if (parts.length != 4) return null;
            try {
                String world = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                return new BlockKey(world, x, y, z);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
