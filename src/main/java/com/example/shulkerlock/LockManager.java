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

    // player -> slot -> lockId
    private final Map<UUID, Map<Integer, UUID>> playerLocks = new ConcurrentHashMap<>();
    // lockId -> LockInfo
    private final Map<UUID, LockInfo> lockInfoMap = new ConcurrentHashMap<>();
    
    // ==========================================
    // ĐÃ SỬA: Đổi tên thành lockKey và chuyển thành public
    // Để ShulkerLister có thể truy cập trực tiếp (hết lỗi private access)
    // ==========================================
    public final Map<BlockKey, UUID> lockKey = new ConcurrentHashMap<>(); 

    // Keys cho PersistentDataContainer
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
                    // Đọc thêm thông tin vị trí
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
                // Lưu thông tin lock
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

    // Khóa một shulker block
    public synchronized boolean lockShulker(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        // Kiểm tra xem block đã có lock chưa
        BlockKey key = new BlockKey(block.getLocation());
        if (lockKey.containsKey(key)) {
            String msg = plugin.getPluginConfig().getString("already-locked-message", "&cThis shulker is already locked!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        // Kiểm tra giới hạn slot
        Map<Integer, UUID> slots = playerLocks.computeIfAbsent(uuid, k -> new HashMap<>());
        int maxLocks = plugin.getPluginConfig().getInt("max-locks", 3);
        if (slots.size() >= maxLocks) {
            String msg = plugin.getPluginConfig().getString("max-locks-message", "&cYou already locked {max} shulkers! Unlock one first.");
            msg = msg.replace("{max}", String.valueOf(maxLocks));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        // Tìm slot trống
        int slot = 1;
        while (slots.containsKey(slot)) slot++;

        // Tạo lockId
        UUID lockId = UUID.randomUUID();
        slots.put(slot, lockId);

        // Lưu thông tin lock
        LockInfo info = new LockInfo(uuid, slot, key);
        lockInfoMap.put(lockId, info);
        lockKey.put(key, lockId);

        // Ghi persistent data vào block state và item (nếu có thể)
        if (block.getState() instanceof ShulkerBox shulker) {
            PersistentDataContainer pdc = shulker.getPersistentDataContainer();
            pdc.set(lockIdKey, PersistentDataType.STRING, lockId.toString());
            pdc.set(ownerKey, PersistentDataType.STRING, uuid.toString());
            pdc.set(slotKey, PersistentDataType.INTEGER, slot);
            shulker.update();

            // Đổi tên
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

    // Mở khóa (qua menu)
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
            // Xóa persistent data trên block
            Block block = info.location.toBlock();
            if (block != null && block.getState() instanceof ShulkerBox shulker) {
                PersistentDataContainer pdc = shulker.getPersistentDataContainer();
                pdc.remove(lockIdKey);
                pdc.remove(ownerKey);
                pdc.remove(slotKey);
                shulker.setCustomName(null); // trả về tên gốc
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

    // Khi block bị phá: xóa khỏi lockKey nhưng giữ trong playerLocks
    public synchronized void onBlockBreak(Block block) {
        BlockKey key = new BlockKey(block.getLocation());
        UUID lockId = lockKey.remove(key);
        if (lockId != null) {
            LockInfo info = lockInfoMap.get(lockId);
            if (info != null) {
                info.location = null; // vị trí không còn hợp lệ
            }
            // Lưu lại để cập nhật file (loại bỏ location)
            save();
        }
    }

    // Khi block được đặt: khôi phục lock từ item
    public synchronized void onBlockPlace(Block block, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(lockIdKey, PersistentDataType.STRING)) return;

        String lockIdStr = pdc.get(lockIdKey, PersistentDataType.STRING);
        UUID lockId = UUID.fromString(lockIdStr);
        LockInfo info = lockInfoMap.get(lockId);
        if (info == null) {
            // Nếu không tìm thấy trong memory, có thể đã bị xóa khỏi file do bug, bỏ qua
            return;
        }

        // Cập nhật vị trí mới
        BlockKey newKey = new BlockKey(block.getLocation());
        info.location = newKey;
        lockKey.put(newKey, lockId);

        // Cập nhật lại block state
        if (block.getState() instanceof ShulkerBox shulker) {
            PersistentDataContainer blockPdc = shulker.getPersistentDataContainer();
            blockPdc.set(lockIdKey, PersistentDataType.STRING, lockId.toString());
            blockPdc.set(ownerKey, PersistentDataType.STRING, info.owner.toString());
            blockPdc.set(slotKey, PersistentDataType.INTEGER, info.slot);

            // Đặt lại tên
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

    // Kiểm tra block có lock không
    public boolean isLocked(Block block) {
        return lockKey.containsKey(new BlockKey(block.getLocation()));
    }

    // Lấy owner của block
    public UUID getOwner(Block block) {
        UUID lockId = lockKey.get(new BlockKey(block.getLocation()));
        if (lockId == null) return null;
        LockInfo info = lockInfoMap.get(lockId);
        return info != null ? info.owner : null;
    }

    // Lấy slot của block
    public int getSlot(Block block) {
        UUID lockId = lockKey.get(new BlockKey(block.getLocation()));
        if (lockId == null) return -1;
        LockInfo info = lockInfoMap.get(lockId);
        return info != null ? info.slot : -1;
    }

    // Lấy danh sách slot của player
    public Map<Integer, UUID> getPlayerSlots(Player player) {
        return playerLocks.getOrDefault(player.getUniqueId(), new HashMap<>());
    }

    // Lấy thông tin lock để hiển thị
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

    // BlockKey giữ nguyên như cũ
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
