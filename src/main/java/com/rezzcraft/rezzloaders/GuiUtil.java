package com.rezzcraft.rezzloaders;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiUtil {
    private GuiUtil() {}

    public static void openInfoGui(Player p, LoaderRecord r, double tps1m) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Chunk Loader");

        ItemStack center = new ItemStack(Material.CLOCK);
        ItemMeta m = center.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.GREEN + "Loader Info");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + r.id.toString().substring(0, 8));
            lore.add(ChatColor.GRAY + "Size: " + ChatColor.WHITE + (r.size == LoaderSize.ONE_BY_ONE ? "1x1" : "5x5"));
            long remainingMs = Math.max(0L, r.expiresAtMs - System.currentTimeMillis());
            lore.add(ChatColor.GRAY + "Time left: " + ChatColor.WHITE + LoaderItems.formatDuration(remainingMs / 1000));
            lore.add(ChatColor.GRAY + "TPS (1m): " + ChatColor.WHITE + String.format("%.2f", tps1m));
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Right-click" + ChatColor.GRAY + " to view this again.");
            lore.add(ChatColor.YELLOW + "Sneak + Right-click" + ChatColor.GRAY + " to open this GUI.");
            m.setLore(lore);
            center.setItemMeta(m);
        }
        inv.setItem(13, center);

        ItemStack block = new ItemStack(Material.LODESTONE);
        ItemMeta b = block.getItemMeta();
        if (b != null) {
            b.setDisplayName(ChatColor.AQUA + "Location");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + r.world);
            lore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + r.x + ChatColor.GRAY + " Y: " + ChatColor.WHITE + r.y + ChatColor.GRAY + " Z: " + ChatColor.WHITE + r.z);
            b.setLore(lore);
            block.setItemMeta(b);
        }
        inv.setItem(11, block);

        ItemStack owner = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta o = owner.getItemMeta();
        if (o != null) {
            o.setDisplayName(ChatColor.LIGHT_PURPLE + "Owner");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + r.owner.toString());
            o.setLore(lore);
            owner.setItemMeta(o);
        }
        inv.setItem(15, owner);

        p.openInventory(inv);
    }
}
