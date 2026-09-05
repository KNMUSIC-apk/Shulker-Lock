package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ShulkerListener implements Listener {

    private final ShulkerLockPlugin plugin;

    public ShulkerListener(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isShulker(block.getType())) return;

        // Kiểm tra lock nếu shulker đã bị khóa và không phải chủ
        if (plugin.getLockManager().isLocked(block)) {
            UUID owner = plugin.getLockManager().getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                String msg = plugin.getPluginConfig().getString("not-owner-message", "&cRương shulker này bị khóa bởi {owner}");
                msg = msg.replace("{owner}", Bukkit.getOfflinePlayer(owner).getName());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // Nếu đang sneak, kiểm tra rìu (cả hai tay)
        if (player.isSneaking()) {
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();
            boolean hasAxe = isAxe(main) || isAxe(off);

            if (hasAxe) {
                // Chặn mở rương
                event.setUseInteractedBlock(Event.Result.DENY);
                // Gọi lock
                boolean success = plugin.getLockManager().lockShulker(player, block);
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

        if (plugin.getLockManager().isLocked(block)) {
            Player player = event.getPlayer();
            UUID owner = plugin.getLockManager().getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                String msg = plugin.getPluginConfig().getString("break-not-owner-message", "&cBạn không thể phá rương shulker đã bị khóa!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                // Cho phép phá, nhưng không thông báo unlock, chỉ xóa lock thầm lặng
                int slot = plugin.getLockManager().getSlot(block);
                if (slot != -1) {
                    plugin.getLockManager().unlockSilently(player, slot);
                }
                // không thông báo, không âm thanh (hoặc có thể phát âm thanh phá bình thường)
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
        return mat.name().contains("SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }
}
