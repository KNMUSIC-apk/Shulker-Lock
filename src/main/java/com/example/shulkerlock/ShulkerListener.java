package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class ShulkerListener implements Listener {

    private final ShulkerLockPlugin plugin;
    private final LockManager lockManager;

    public ShulkerListener(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
        this.lockManager = plugin.getLockManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isShulker(block.getType())) return;

        // Nếu shulker đã khóa và không phải chủ sở hữu → chặn
        if (lockManager.isLocked(block)) {
            UUID owner = lockManager.getOwner(block);
            if (owner != null && !player.getUniqueId().equals(owner)) {
                event.setCancelled(true);
                String msg = plugin.getPluginConfig().getString("not-owner-message", "&cRương shulker này bị khóa bởi {owner}");
                msg = msg.replace("{owner}", Bukkit.getOfflinePlayer(owner).getName());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            // Nếu là chủ sở hữu, cho mở bình thường
        }

        // Nếu đang sneak, kiểm tra rìu (cả hai tay)
        if (player.isSneaking()) {
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();
            boolean hasAxe = isAxe(main) || isAxe(off);

            if (hasAxe) {
                event.setCancelled(true); // chặn mở rương
                boolean success = lockManager.lockShulker(player, block);
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isShulker(block.getType())) return;

        if (lockManager.isLocked(block)) {
            Player player = event.getPlayer();
            UUID owner = lockManager.getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                String msg = plugin.getPluginConfig().getString("break-not-owner-message", "&cBạn không thể phá rương shulker đã bị khóa!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Chủ sở hữu phá: xóa khỏi blockLockMap nhưng giữ lock
            lockManager.onBlockBreak(block);

            // Đảm bảo item rơi ra có persistent data
            // Sự kiện BlockBreak không cho phép sửa item drop dễ dàng, nhưng ta có thể lắng nghe BlockDropItemEvent (Paper)
            // Thay vào đó, ta sẽ đặt persistent data vào item sau khi nó được drop? Không thể.
            // Cách khác: dùng BlockDropItemEvent hoặc thay thế bằng cách tự tạo drop.
            // Giải pháp: Sử dụng BlockDropItemEvent (Paper) để set persistent data vào item.
            // Ta sẽ thêm listener sau.
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isShulker(block.getType())) return;

        ItemStack item = event.getItemInHand();
        lockManager.onBlockPlace(block, item);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (!isShulker(stack.getType())) return;
        if (!stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(plugin.getLockManager().lockIdKey, PersistentDataType.STRING)) return;

        String lockIdStr = pdc.get(plugin.getLockManager().lockIdKey, PersistentDataType.STRING);
        UUID lockId = UUID.fromString(lockIdStr);
        LockManager.LockInfo info = lockManager.getLockInfo(lockId);
        if (info == null) return;

        // Chỉ chủ nhân mới được nhặt
        if (!player.getUniqueId().equals(info.owner)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bạn không thể nhặt shulker đã khóa của người khác!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // Sự kiện drop item để thêm persistent data (Paper)
    @EventHandler
    public void onBlockDropItem(org.bukkit.event.block.BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (!isShulker(block.getType())) return;
        if (!lockManager.isLocked(block)) return;

        // Lấy lockId từ block state
        if (block.getState() instanceof ShulkerBox shulker) {
            PersistentDataContainer pdc = shulker.getPersistentDataContainer();
            if (!pdc.has(plugin.getLockManager().lockIdKey, PersistentDataType.STRING)) return;
            String lockIdStr = pdc.get(plugin.getLockManager().lockIdKey, PersistentDataType.STRING);
            // Thêm persistent data vào các item drop
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                ItemMeta meta = stack.getItemMeta();
                if (meta == null) continue;
                PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
                itemPdc.set(plugin.getLockManager().lockIdKey, PersistentDataType.STRING, lockIdStr);
                // Cũng copy owner và slot nếu cần
                if (pdc.has(plugin.getLockManager().ownerKey, PersistentDataType.STRING)) {
                    String ownerStr = pdc.get(plugin.getLockManager().ownerKey, PersistentDataType.STRING);
                    itemPdc.set(plugin.getLockManager().ownerKey, PersistentDataType.STRING, ownerStr);
                }
                if (pdc.has(plugin.getLockManager().slotKey, PersistentDataType.INTEGER)) {
                    Integer slot = pdc.get(plugin.getLockManager().slotKey, PersistentDataType.INTEGER);
                    itemPdc.set(plugin.getLockManager().slotKey, PersistentDataType.INTEGER, slot);
                }
                stack.setItemMeta(meta);
                item.setItemStack(stack);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShulkerBox)) return;
        ShulkerBox shulker = (ShulkerBox) event.getInventory().getHolder();
        Block block = shulker.getBlock();
        if (lockManager.isLocked(block)) {
            Player player = (Player) event.getPlayer();
            UUID owner = lockManager.getOwner(block);
            if (owner != null && !player.getUniqueId().equals(owner)) {
                String msg = plugin.getPluginConfig().getString("not-owner-message", "&cRương shulker này bị khóa bởi {owner}");
                msg = msg.replace("{owner}", Bukkit.getOfflinePlayer(owner).getName());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    private boolean isShulker(Material mat) {
        return mat.name().contains("SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }
}
