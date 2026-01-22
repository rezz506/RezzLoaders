package com.rezzcraft.rezzloaders;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class LoaderListener implements Listener {

    private final RezzLoadersPlugin plugin;
    private final LoaderManager manager;
    private final ActionLogger log;

    public LoaderListener(RezzLoadersPlugin plugin, LoaderManager manager, ActionLogger log) {
        this.plugin = plugin;
        this.manager = manager;
        this.log = log;
    }

    private boolean isLoaderBlock(Block b) {
        if (b == null) return false;
        String matName = plugin.getConfig().getString("item.material", "LODESTONE");
        Material mat;
        try { mat = Material.valueOf(matName.toUpperCase()); } catch (Exception e) { mat = Material.LODESTONE; }
        return b.getType() == mat;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte marker = pdc.get(plugin.KEY_ITEM, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return;

        Player player = event.getPlayer();
        if (!(player.hasPermission("rezzloaders.use") || player.hasPermission("rezzloaders.admin"))) {
            player.sendMessage(ChatColor.RED + "You don't have permission to place chunk loaders.");
            event.setCancelled(true);
            return;
        }

        long durationSec = pdc.getOrDefault(plugin.KEY_DURATION_SEC, PersistentDataType.LONG, 0L);
        int sizeInt = pdc.getOrDefault(plugin.KEY_SIZE, PersistentDataType.INTEGER, 1);
        LoaderSize size = (sizeInt == 5) ? LoaderSize.FIVE_BY_FIVE : LoaderSize.ONE_BY_ONE;

        Location loc = event.getBlockPlaced().getLocation();
        LoaderManager.PlaceResult result = manager.placeLoader(player, loc, size, durationSec);

        if (!result.success) {
            player.sendMessage(ChatColor.RED + result.message);
            event.setCancelled(true);
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Chunk loader placed! " + ChatColor.GRAY + "(" + size.size + "x" + size.size + ", " + LoaderManager.formatDuration(durationSec) + ")");
        log.log("PLACE", player.getName() + " placed loader " + result.loaderId + " at " + LoaderManager.locShort(loc) + " size=" + size.size + " durationSec=" + durationSec);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isLoaderBlock(block)) return;

        Location loc = block.getLocation();
        LoaderRecord rec = manager.getByLocation(loc);
        if (rec == null) return;

        Player player = event.getPlayer();
        boolean isOwner = rec.owner.equals(player.getUniqueId());
        if (!isOwner && !(player.hasPermission("rezzloaders.admin"))) {
            player.sendMessage(ChatColor.RED + "Only the owner can break this chunk loader.");
            event.setCancelled(true);
            return;
        }

        // Prevent the vanilla lodestone from dropping; we'll refund the proper timed loader item.
        event.setDropItems(false);
        event.setExpToDrop(0);

        long remainingSeconds = Math.max(0L, (rec.expiresAtMs - System.currentTimeMillis()) / 1000L);
        if (remainingSeconds > 0) {
            ItemStack refund = LoaderItems.createLoaderItem(plugin, remainingSeconds, rec.size);
            var leftover = player.getInventory().addItem(refund);
            if (!leftover.isEmpty()) {
                // If inventory is full, drop the item naturally at the block location.
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), refund);
            }
        }

        manager.removeLoader(rec.id, "BROKEN");
        player.sendMessage(ChatColor.YELLOW + "Chunk loader removed." + ChatColor.GRAY + (remainingSeconds > 0 ? " Refunded with " + LoaderItems.formatDuration(remainingSeconds) + " remaining." : ""));
        log.log("REMOVE", player.getName() + " removed loader " + rec.id + " at " + LoaderManager.locShort(loc));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!isLoaderBlock(event.getClickedBlock())) return;

        Location loc = event.getClickedBlock().getLocation();
        LoaderRecord rec = manager.getByLocation(loc);
        if (rec == null) return;

        Player player = event.getPlayer();
        if (event.getAction().name().contains("RIGHT")) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                manager.openInfoGui(player, rec);
            } else {
                player.sendMessage(manager.buildInfoLines(rec));
                player.sendMessage(ChatColor.DARK_GRAY + "Tip: Shift-right-click to open the info GUI.");
            }
        }
    }
}
