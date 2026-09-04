package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
        // Kiểm tra action click phải vào block
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        if (!isShulker(type)) return;

        // Lấy item đang cầm trên tay (ưu tiên tay phải, nếu không thì tay trái)
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item)) {
            // Thử kiểm tra tay trái
            item = player.getInventory().getItemInOffHand();
            if (!isAxe(item)) {
                return; // Không cầm rìu ở cả hai tay
            }
        }

        // Kiểm tra sneak (Shift)
        if (!player.isSneaking()) {
            // Nếu không sneak nhưng có rìu và click phải shulker – có thể là mở bình thường, không can thiệp
            return;
        }

        // Đã đủ điều kiện: đang sneak + cầm rìu + click phải shulker
        event.setCancelled(true); // Chặn hành động mở rương

        // Gửi thông báo debug (có thể xóa sau)
        player.sendMessage(ChatColor.GREEN + "Đang thử khóa shulker...");

        boolean success = plugin.getLockManager().lockShulker(player, block);
        if (!success) {
            // lockShulker đã tự gửi thông báo lỗi
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
            } else {
                int slot = plugin.getLockManager().getSlot(block);
                if (slot != -1) {
                    plugin.getLockManager().unlockShulker(player, slot);
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
            }
        }
    }

    private boolean isShulker(Material mat) {
        String name = mat.name();
        return name.endsWith("_SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_AXE");
    }
}
