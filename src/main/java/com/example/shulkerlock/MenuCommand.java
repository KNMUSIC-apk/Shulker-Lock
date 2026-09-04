package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MenuCommand implements CommandExecutor {

    private final ShulkerLockPlugin plugin;

    public MenuCommand(ShulkerLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lShulker Lock §f§lMenu");

        // Vật phẩm trang trí viền
        ItemStack borderBlue = createItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        ItemStack borderCyan = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");

        // Hàng trên và dưới
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderBlue);
            inv.setItem(i + 18, borderBlue);
        }
        // Hàng giữa: viền trái và phải
        inv.setItem(9, borderCyan);
        inv.setItem(17, borderCyan);

        // Lấy thông tin lock của người chơi
        Map<Integer, LockManager.BlockKey> slots = plugin.getLockManager().getPlayerSlots(player);

        // Vị trí 3 slot ở giữa: 10, 11, 12? Thực tế hàng giữa là từ 9 đến 17, ta muốn đặt ở 11, 12, 13? Hãy đặt ở 11, 12, 13 cho đẹp.
        int[] guiSlots = {11, 12, 13};
        for (int i = 0; i < 3; i++) {
            int slotNum = i + 1;
            boolean locked = slots.containsKey(slotNum);

            Material material = locked ? Material.YELLOW_SHULKER_BOX : Material.LIGHT_GRAY_SHULKER_BOX;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(locked ? "§6§lShulker Lock #" + slotNum : "§7§lEmpty Slot #" + slotNum);

            List<String> lore = new ArrayList<>();
            if (locked) {
                // Thêm enchant và ẩn hiệu ứng
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                // Lấy vị trí shulker
                String location = plugin.getLockManager().getSlotLocation(player, slotNum);
                lore.add("§a✔ Locked");
                if (location != null) {
                    lore.add("§7Location: " + location);
                }
                lore.add("§eClick to unlock this shulker");
            } else {
                lore.add("§7No shulker locked in this slot.");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(guiSlots[i], item);
        }

        // Thêm một số vật phẩm trang trí khác
        ItemStack info = createItem(Material.ENDER_EYE, "§b§lHow to lock?");
        info.getItemMeta().setLore(Arrays.asList("§7Sneak + Right-click with an axe", "§7on a shulker box to lock it."));
        inv.setItem(15, info);

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }
}
