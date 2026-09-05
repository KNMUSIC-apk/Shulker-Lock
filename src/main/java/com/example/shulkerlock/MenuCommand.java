package com.example.shulkerlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        // Lấy tiêu đề từ config
        String title = plugin.getPluginConfig().getString("menu-title", "&6&lShulker Lock &f&lMenu");
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', title));

        // Lấy vật liệu viền từ config
        String borderMat = plugin.getPluginConfig().getString("menu-border-material", "BLUE_STAINED_GLASS_PANE");
        String borderMat2 = plugin.getPluginConfig().getString("menu-border-material-secondary", "CYAN_STAINED_GLASS_PANE");
        Material borderMaterial = Material.getMaterial(borderMat);
        Material borderMaterial2 = Material.getMaterial(borderMat2);
        if (borderMaterial == null) borderMaterial = Material.BLUE_STAINED_GLASS_PANE;
        if (borderMaterial2 == null) borderMaterial2 = Material.CYAN_STAINED_GLASS_PANE;

        ItemStack border1 = createItem(borderMaterial, " ");
        ItemStack border2 = createItem(borderMaterial2, " ");

        // Trang trí viền
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border1);
            inv.setItem(i + 18, border1);
        }
        inv.setItem(9, border2);
        inv.setItem(17, border2);

        // Lấy vật liệu cho slot
        String lockedMatName = plugin.getPluginConfig().getString("locked-slot-material", "YELLOW_SHULKER_BOX");
        String emptyMatName = plugin.getPluginConfig().getString("empty-slot-material", "LIGHT_GRAY_SHULKER_BOX");
        Material lockedMat = Material.getMaterial(lockedMatName);
        Material emptyMat = Material.getMaterial(emptyMatName);
        if (lockedMat == null) lockedMat = Material.YELLOW_SHULKER_BOX;
        if (emptyMat == null) emptyMat = Material.LIGHT_GRAY_SHULKER_BOX;

        // Lấy danh sách lock của player (Map<Integer, UUID>)
        Map<Integer, UUID> slots = plugin.getLockManager().getPlayerSlots(player);

        // Vị trí 3 slot giữa: 11, 12, 13
        int[] guiSlots = {11, 12, 13};
        boolean showLocation = plugin.getPluginConfig().getBoolean("show-location-in-menu", true);

        for (int i = 0; i < 3; i++) {
            int slotNum = i + 1;
            boolean locked = slots.containsKey(slotNum);
            UUID lockId = locked ? slots.get(slotNum) : null;
            LockManager.LockInfo info = (lockId != null) ? plugin.getLockManager().getLockInfo(lockId) : null;

            Material mat = locked ? lockedMat : emptyMat;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String displayName;
            if (locked && info != null) {
                // Lấy tên người chơi từ owner
                String ownerName = Bukkit.getOfflinePlayer(info.owner).getName();
                if (ownerName == null) ownerName = "Unknown";
                displayName = ChatColor.translateAlternateColorCodes('&',
                        "&6&lShulker Lock #" + slotNum + " &7(" + ownerName + ")");
            } else {
                displayName = ChatColor.translateAlternateColorCodes('&',
                        "&7&lEmpty Slot #" + slotNum);
            }
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();
            if (locked && info != null) {
                // Thêm enchant và ẩn
                String enchantName = plugin.getPluginConfig().getString("locked-enchant", "UNBREAKING");
                Enchantment enchant = Enchantment.getByName(enchantName);
                if (enchant == null) enchant = Enchantment.UNBREAKING;
                meta.addEnchant(enchant, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                lore.add("§a✔ Locked");
                if (showLocation && info.location != null) {
                    org.bukkit.block.Block block = info.location.toBlock();
                    if (block != null) {
                        lore.add("§7Location: " + block.getWorld().getName() + " at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    } else {
                        lore.add("§7Location: Unknown (block not loaded)");
                    }
                } else {
                    lore.add("§7Location: Unknown");
                }
                lore.add("§eClick to unlock this shulker");
            } else {
                lore.add("§7No shulker locked in this slot.");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(guiSlots[i], item);
        }

        // Thêm item hướng dẫn
        String infoMatName = plugin.getPluginConfig().getString("info-item-material", "ENDER_EYE");
        Material infoMat = Material.getMaterial(infoMatName);
        if (infoMat == null) infoMat = Material.ENDER_EYE;
        String infoName = plugin.getPluginConfig().getString("info-item-name", "&b&lHow to lock?");
        List<String> infoLore = plugin.getPluginConfig().getStringList("info-item-lore");
        if (infoLore.isEmpty()) {
            infoLore.add("&7Sneak + Right-click with an axe");
            infoLore.add("&7on a shulker box to lock it.");
        }
        ItemStack infoItem = createItem(infoMat, ChatColor.translateAlternateColorCodes('&', infoName));
        ItemMeta infoMeta = infoItem.getItemMeta();
        List<String> coloredLore = new ArrayList<>();
        for (String line : infoLore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        infoMeta.setLore(coloredLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(15, infoItem);

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(displayName);
        }
        item.setItemMeta(meta);
        return item;
    }
}
