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

        // Kiểm tra có phải shulker không (đã sửa)
        if (!isShulker(block.getType())) {
            return;
        }

        // ===== DEBUG =====
        player.sendMessage(ChatColor.GRAY + "[ShulkerLock] Bạn vừa click vào shulker!");
        Bukkit.getLogger().info("[ShulkerLock] " + player.getName() + " click shulker tại " + block.getLocation());

        // Kiểm tra sneak
        if (!player.isSneaking()) {
            player.sendMessage(ChatColor.GRAY + "[ShulkerLock] Bạn không ngồi (sneak), bỏ qua khóa.");
            // Vẫn kiểm tra lock nếu không phải chủ sở hữu
            if (plugin.getLockManager().isLocked(block)) {
                UUID owner = plugin.getLockManager().getOwner(block);
                if (!player.getUniqueId().equals(owner)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                    String msg = plugin.getPluginConfig().getString("not-owner-message", "&cRương shulker này bị khóa bởi {owner}");
                    msg = msg.replace("{owner}", Bukkit.getOfflinePlayer(owner).getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            return;
        }

        // Đang sneak: kiểm tra rìu
        ItemStack item = null;
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.HAND) {
            item = player.getInventory().getItemInMainHand();
        } else if (hand == EquipmentSlot.OFF_HAND) {
            item = player.getInventory().getItemInOffHand();
        }

        if (!isAxe(item)) {
            player.sendMessage(ChatColor.GRAY + "[ShulkerLock] Bạn không cầm rìu! (tay: " + hand + ")");
            return;
        }

        // ---- Đủ điều kiện: sneak + rìu ----
        event.setUseInteractedBlock(Event.Result.DENY); // Ngăn mở rương

        Bukkit.getLogger().info("[ShulkerLock] " + player.getName() + " ĐANG KHÓA shulker tại " + block.getLocation());
        player.sendMessage(ChatColor.GREEN + "[ShulkerLock] Đang khóa...");

        boolean success = plugin.getLockManager().lockShulker(player, block);

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
        // Bao gồm cả SHULKER_BOX (không màu) và các biến thể màu
        return mat.name().contains("SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }
}
