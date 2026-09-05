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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {

    private final ShulkerLockPlugin plugin;
    private final File dataFile;
    private FileConfiguration config;

    private final Map<UUID, Map<Integer, UUID>> playerLocks = new ConcurrentHashMap<>();
    private final Map<UUID, LockInfo> lockInfoMap = new ConcurrentHashMap<>();
    
    // Đã chuyển thành public để ShulkerLister truy cập được (đã hết lỗi private)
    public Map<BlockKey, UUID> lockKey = new ConcurrentHashMap<>(); 

    private final NamespacedKey lockIdKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey slotKey;

    public LockManager(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "locks.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create locks.yml: " + e.getMessage());
            }
        }
        this.lockIdKey = new NamespacedKey(plugin, "lock_id");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.slotKey = new NamespacedKey(plugin, "slot");
    }

    // Hàm Getter để lấy lockKey
    public Map<BlockKey, UUID> getLockKey() {
        return lockKey;
    }

    // =================================================================
    // HÀM MỚI ĐƯỢC THÊM VÀO ĐỂ SỬA LỖI "cannot find symbol"
    // =================================================================
    
    // Lấy vị trí (Block) của một ổ khóa theo Player và Slot
    public Block getSlotLocation(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        Map<Integer, UUID> slots = playerLocks.get(uuid);
        if (slots == null) return null;
        
        UUID lockId = slots.get(slot);
        if (lockId == null) return null;
        
        LockInfo info = lockInfoMap.get(lockId);
        if (info != null && info.location != null) {
            return info.location.toBlock();
        }
        return null;
    }

    // Mở khóa thầm lặng (Không gửi tin nhắn cho người chơi)
    public synchronized boolean unlockSilently(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        Map<Integer, UUID> slots = playerLocks.get(uuid);
        if (slots == null || !slots.containsKey(slot)) {
            return false;
        }

        UUID lockId = slots.remove(slot);
        LockInfo info = lockInfoMap.remove(lockId);
        if (info != null && info.location != null) {
            lockKey.remove(info.location);
            Block block = info.location.toBlock();
            if (block != null && block.getState() instanceof ShulkerBox shulker) {
                PersistentDataContainer pdc = shulker.getPersistentDataContainer();
                pdc.remove(lockIdKey);
                pdc.remove(ownerKey);
                pdc.remove(slotKey);
                shulker.setCustomName(null);
                shulker.update();
            }
        }

        if (slots.isEmpty()) {
            playerLocks.remove(uuid);
        }

        save();
        return true;
    }
    // =================================================================

    public void load() {
        config = YamlConfiguration.loadConfiguration(dataFile);
        playerLocks.clear();
        lockInfoMap.clear();
        lockKey.clear();

        if (config.contains("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<Integer, UUID> slots = new HashMap<>();
                for (String slotStr : config.getConfigurationSection("players." + uuidStr).getKeys(false)) {
                    int slot = Integer.parseInt(slotStr);
                    String lockIdStr = config.getString("players." + uuidStr + "." + slotStr);
                    if (lockIdStr == null) continue;
                    UUID lockId = UUID.fromString(lockIdStr);
                    String locStr = config.getString("locks." + lockIdStr + ".location");
                    BlockKey key = null;
                    if (locStr != null) {
                        key = BlockKey.fromString(locStr);
                    }
                    String ownerStr = config.getString("locks." + lockIdStr + ".owner");
                    UUID owner = (ownerStr != null) ? UUID.fromString(ownerStr) : uuid;
                    slots.put(slot, lockId);
                    LockInfo info = new LockInfo(owner, slot, key);
                    lockInfoMap.put(lockId, info);
                    if (key != null) {
                        lockKey.put(key, lockId);
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
        for (Map.Entry<UUID, Map<Integer, UUID>> entry : playerLocks.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<Integer, UUID> slotEntry : entry.getValue().entrySet()) {
                config.set("players." + uuidStr + "." + slotEntry.getKey(), slotEntry.getValue().toString());
                UUID lockId = slotEntry.getValue();
                LockInfo info = lockInfoMap.get(lockId);
                if (info != null) {
                    config.set("locks." + lockId.toString() + ".owner", info.owner.toString());
                    if (info.location != null) {
                        config.set("locks." + lockId.toString() + ".location", info.location.toString());
                    }
                }
            }
        }
        try {
            config.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save lock data: " + e.getMessage());
        }
    }

    public synchronized boolean lockShulker(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        BlockKey key = new BlockKey(block.getLocation());
        if (lockKey.containsKey(key)) {
            String msg = plugin.getPluginConfig().getString("already-locked-message", "&cThis shulker is already locked!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        Map<Integer, UUID> slots = playerLocks.computeIfAbsent(uuid, k -> new HashMap<>());
        int maxLocks = plugin.getPluginConfig().getInt("max-locks", 3);
        if (slots.size() >= maxLocks) {
            String msg = plugin.getPluginConfig().getString("max-locks-message", "&cYou already locked {max} shulkers! Unlock one first.");
            msg = msg.replace("{max}", String.valueOf(maxLocks));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        int slot = 1;
        while (slots.containsKey(slot)) slot++;

        UUID lockId = UUID.randomUUID();
        slots.put(slot, lockId);

        LockInfo info = new LockInfo(uuid, slot, key);
        lockInfoMap.put(lockId, info);
        lockKey.put(key, lockId);

        if (block.getState() instanceof ShulkerBox shulker) {
            PersistentDataContainer pdc = shulker.getPersistentDataContainer();
            pdc.set(lockIdKey, PersistentDataType.STRING, lockId.toString());
            pdc.set(ownerKey, PersistentDataType.STRING, uuid.toString());
            pdc.set(slotKey, PersistentDataType.INTEGER, slot);
            shulker.update();

            String format = plugin.getPluginConfig().getString("lock-display-name", "&6[shulker lock &f{player} &e#{slot}&6]");
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
        Map<Integer, UUID> slots = playerLocks.get(uuid);
        if (slots == null || !slots.containsKey(slot)) {
            String msg = plugin.getPluginConfig().getString("empty-slot-message", "&cYou don't have a lock in slot #" + slot);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        UUID lockId = slots.remove(slot);
        LockInfo info = lockInfoMap.remove(lockId);
        if (info != null && info.location != null) {
            lockKey.remove(info.location);
            Block block = info.location.toBlock();
            if (block != null && block.getState() instanceof ShulkerBox shulker) {
                PersistentDataContainer pdc = shulker.getPersistentDataContainer();
                pdc.remove(lockIdKey);
                pdc.remove(ownerKey);
                pdc.remove(slotKey);
                shulker.setCustomName(null);
                shulker.update();
            }
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

    public synchronized void onBlockBreak(Block block) {
        BlockKey key = new BlockKey(block.getLocation());
        UUID lockId = lockKey.remove(key);
        if (lockId != null) {
            LockInfo info = lockInfoMap.get(lockId);
            if (info != null) {
                info.location = null;
            }
            save();
        }
    }

    public synchronized void onBlockPlace(Block block, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(lockIdKey, PersistentDataType.STRING)) return;

        String lockIdStr = pdc.get(lockIdKey, PersistentDataType.STRING);
        UUID lockId = UUID.fromString(lockIdStr);
        LockInfo info = lockInfoMap.get(lockId);
        if (info == null) {
            return;
        }

        BlockKey newKey = new BlockKey(block.getLocation());
        info.location = newKey;
        lockKey.put(newKey, lockId);

        if (block.getState() instanceof ShulkerBox shulker) {
            PersistentDataContainer blockPdc = shulker.getPersistentDataContainer();
            blockPdc.set(lockIdKey, PersistentDataType.STRING, lockId.toString());
            blockPdc.set(ownerKey, PersistentDataType.STRING, info.owner.toString());
            blockPdc.set(slotKey, PersistentDataType.INTEGER, info.slot);

            Player owner = Bukkit.getPlayer(info.owner);
            String ownerName = (owner != null) ? owner.getName() : Bukkit.getOfflinePlayer(info.owner).getName();
            String format = plugin.getPluginConfig().getString("lock-display-name", "&6[shulker lock &f{player} &e#{slot}&6]");
            String name = ChatColor.translateAlternateColorCodes('&',
                    format.replace("{player}", ownerName).replace("{slot}", String.valueOf(info.slot)));
            shulker.setCustomName(name);
            shulker.update();
        }

        save();
    }

    public boolean isLocked(Block block) {
        return lockKey.containsKey(new BlockKey(block.getLocation()));
    }

    public UUID getOwner(Block block) {
        UUID lockId = lockKey.get(new BlockKey(block.getLocation()));
        if (lockId == null) return null;
        LockInfo info = lockInfoMap.get(lockId);
        return info != null ? info.owner : null;
    }

    // Hàm này đang trả về int. Nếu NewCommand gán cho UUID thì sẽ bị lỗi.
    public int getSlot(Block block) {
        UUID lockId = lockKey.get(new BlockKey(block.getLocation()));
        if (lockId == null) return -1;
        LockInfo info = lockInfoMap.get(lockId);
        return info != null ? info.slot : -1;
    }

    public Map<Integer, UUID> getPlayerSlots(Player player) {
        return playerLocks.getOrDefault(player.getUniqueId(), new HashMap<>());
    }

    public LockInfo getLockInfo(UUID lockId) {
        return lockInfoMap.get(lockId);
    }

    public class LockInfo {
        public UUID owner;
        public int slot;
        public BlockKey location;

        public LockInfo(UUID owner, int slot, BlockKey location) {
            this.owner = owner;
            this.slot = slot;
            this.location = location;
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
