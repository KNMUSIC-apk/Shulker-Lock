package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();

        if (!isShulker(type)) return;

        // Sneak + axe = lock
        if (player.isSneaking() && isAxe(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            plugin.getLockManager().lockShulker(player, block);
            return;
        }

        // Normal right-click: prevent if locked and not owner
        if (plugin.getLockManager().isLocked(block)) {
            UUID owner = plugin.getLockManager().getOwner(block);
            if (!player.getUniqueId().equals(owner)) {
                event.setCancelled(true);
                player.sendMessage("§cThis shulker is locked by " + Bukkit.getOfflinePlayer(owner).getName());
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
                event.setCancelled(true);
                player.sendMessage("§cYou cannot break this locked shulker!");
            } else {
                int slot = plugin.getLockManager().getSlot(block);
                if (slot != -1) {
                    plugin.getLockManager().unlockShulker(player, slot);
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Ngăn không cho đặt shulker nếu nó đang bị khóa? Thực tế item không lưu trạng thái, nên không cần.
        // Nhưng nếu có mod cho phép copy block, có thể bổ sung sau.
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
                event.setCancelled(true);
                player.sendMessage("§cThis shulker is locked!");
            }
        }
    }

    private boolean isShulker(Material mat) {
        return mat.name().endsWith("_SHULKER_BOX");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat.name().endsWith("_AXE");
    }
}
