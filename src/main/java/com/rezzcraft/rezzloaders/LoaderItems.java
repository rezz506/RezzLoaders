package com.rezzcraft.rezzloaders;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized helper for creating RezzLoaders items.
 */
public final class LoaderItems {

    private LoaderItems() {}

    public static ItemStack createLoaderItem(RezzLoadersPlugin plugin, long durationSeconds, LoaderSize size) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("item.material", "LODESTONE"));
        if (mat == null) mat = Material.LODESTONE;

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + sizeLabel(size) + " Chunk Loader" + ChatColor.GRAY + " (" + formatDuration(durationSeconds) + ")");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Timed chunk loader.");
            lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDuration(durationSeconds));
            lore.add(ChatColor.GRAY + "Size: " + ChatColor.WHITE + sizeLabel(size));
            lore.add(ChatColor.DARK_GRAY + "Place to activate. Right-click for info.");
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(plugin.KEY_ITEM, PersistentDataType.BYTE, (byte) 1);
            pdc.set(plugin.KEY_DURATION_SEC, PersistentDataType.LONG, durationSeconds);
            pdc.set(plugin.KEY_SIZE, PersistentDataType.INTEGER, size == LoaderSize.ONE_BY_ONE ? 1 : 5);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;
        Duration d = Duration.ofSeconds(seconds);
        long days = d.toDays();
        d = d.minusDays(days);
        long hours = d.toHours();
        d = d.minusHours(hours);
        long mins = d.toMinutes();
        d = d.minusMinutes(mins);
        long secs = d.getSeconds();

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (days == 0 && hours == 0 && mins == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static String sizeLabel(LoaderSize size) {
        return size == LoaderSize.ONE_BY_ONE ? "1x1" : "5x5";
    }
}
