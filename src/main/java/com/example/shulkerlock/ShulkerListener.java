package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ShulkerListener implements Listener {

    private final ShulkerLockPlugin plugin;

    public ShulkerListener(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Chỉ xử lý click phải vào block
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Kiểm tra có phải shulker không
        if (!isShulker(block.getType())) return;

        // Kiểm tra người chơi có đang ngồi (sneak) không
        if (!player.isSneaking()) {
            return; // Không sneak -> không khóa, nhưng vẫn cho mở bình thường
        }

        // Lấy item ở tay đang click (tay phải hoặc trái)
        EquipmentSlot hand = event.getHand();
        ItemStack item = null;
        if (hand == EquipmentSlot.HAND) {
            item = player.getInventory().getItemInMainHand();
        } else if (hand == EquipmentSlot.OFF_HAND) {
            item = player.getInventory().getItemInOffHand();
        }

        // Nếu không có item hoặc không phải rìu -> thoát
        if (!isAxe(item)) {
            return;
        }

        // Đã đủ điều kiện: sneak + rìu + click phải shulker
        event.setCancelled(true); // Chặn mở rương

        // Debug
        Bukkit.getLogger().info("[ShulkerLock] " + player.getName() + " đang cố khóa shulker tại " + block.getLocation());

        // Thực hiện khóa
        boolean success = plugin.getLockManager().lockShulker(player, block);

        // Phát âm thanh
        if (success) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isShulker(block.getType())) return;

        if (plugin.getLockManager().isLocked(block)) {
            Player player = event.getPlayer();
            UUID owner = plugin.getLockManager().getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                String msg = plugin.getPluginConfig().getString("break-not-owner-message", "&cBạn không thể phá rương shulker đã bị khóa!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                int slot = plugin.getLockManager().getSlot(block);
                if (slot != -1) {
                    plugin.getLockManager().unlockShulker(player, slot);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShulkerBox)) return;
        ShulkerBox shulker = (ShulkerBox) event.getInventory().getHolder();
        Block block = shulker.getBlock();
        if (plugin.getLockManager().isLocked(block)) {
            Player player = (Player) event.getPlayer();
            UUID owner = plugin.getLockManager().getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                String msg = plugin.getPluginConfig().getString("not-owner-message", "&cRương shulker này bị khóa bởi {owner}");
                msg = msg.replace("{owner}", Bukkit.getOfflinePlayer(owner).getName());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    private boolean isShulker(Material mat) {
        return mat.name().endsWith("_SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }
}
