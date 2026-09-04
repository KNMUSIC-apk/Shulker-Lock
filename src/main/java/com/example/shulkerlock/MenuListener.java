package com.example.shulkerlock;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuListener implements Listener {

    private final ShulkerLockPlugin plugin;

    public MenuListener(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.equals("§6§lShulker Lock §f§lMenu")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        // Chỉ cho phép click vào 3 slot giữa: 11, 12, 13
        if (slot < 11 || slot > 13) return;

        int slotNum = slot - 10; // 11->1, 12->2, 13->3
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        // Kiểm tra xem có enchant không (tức là đã lock)
        if (!meta.hasEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING)) {
            player.sendMessage("§cThis slot is empty or not locked.");
            return;
        }

        // Unlock
        plugin.getLockManager().unlockShulker(player, slotNum);
        // Đóng và mở lại menu để cập nhật
        player.closeInventory();
        player.performCommand("shulkermenu");
    }
}
