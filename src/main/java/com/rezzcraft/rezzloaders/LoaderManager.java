package com.rezzcraft.rezzloaders;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LoaderManager {

    private final RezzLoadersPlugin plugin;
    private final ActionLogger actionLogger;

    private final Map<UUID, LoaderRecord> loaders = new HashMap<>();

    private File dataFile;
    private YamlConfiguration data;

    private BukkitTask tickTask;
    private BukkitTask hologramTask;
    private BukkitTask tpsTask;

    private volatile boolean suspended = false;

    public LoaderManager(RezzLoadersPlugin plugin, ActionLogger actionLogger) {
        this.plugin = plugin;
        this.actionLogger = actionLogger;

        this.dataFile = new File(plugin.getDataFolder(), "loaders.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public synchronized Collection<LoaderRecord> getAll() {
        return new ArrayList<>(loaders.values());
    }

    public synchronized Optional<LoaderRecord> getById(UUID id) {
        return Optional.ofNullable(loaders.get(id));
    }

    public synchronized Optional<LoaderRecord> getByBlock(Location loc) {
        for (LoaderRecord r : loaders.values()) {
            if (r.world.equals(loc.getWorld().getName()) && r.x == loc.getBlockX() && r.y == loc.getBlockY() && r.z == loc.getBlockZ()) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public synchronized int countForOwner(UUID owner) {
        int c = 0;
        for (LoaderRecord r : loaders.values()) if (r.owner.equals(owner)) c++;
        return c;
    }

    public synchronized int countForOwner(UUID owner, LoaderSize size) {
        int c = 0;
        for (LoaderRecord r : loaders.values()) if (r.owner.equals(owner) && r.size == size) c++;
        return c;
    }

    public boolean isWorldBlacklisted(String worldName) {
        List<String> list = plugin.getConfig().getStringList("world-blacklist");
        for (String w : list) {
            if (w != null && w.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public synchronized LoaderRecord addLoader(UUID owner, Location blockLoc, LoaderSize size, long durationSeconds) {
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        long expires = now + (durationSeconds * 1000L);
        LoaderRecord rec = new LoaderRecord(id, owner, blockLoc.getWorld().getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(), size, now, expires);
        loaders.put(id, rec);

        if (!suspended) {
            applyTickets(rec);
        }
        spawnOrUpdateHologram(rec, true);
        saveAll();
        return rec;
    }

    public synchronized void removeLoader(UUID id, String reason) {
        LoaderRecord rec = loaders.remove(id);
        if (rec == null) return;
        removeTickets(rec);
        despawnHologram(rec);
        removeFromData(rec.id);
        saveDataFile();
        actionLogger.log("LOADER_REMOVE", "id=" + rec.id + " owner=" + rec.owner + " world=" + rec.world + " xyz=" + rec.x + "," + rec.y + "," + rec.z + " size=" + rec.size + " reason=" + reason);
    }

    public synchronized void expireLoader(UUID id) {
        LoaderRecord rec = loaders.get(id);
        if (rec == null) return;
        // try to break the block naturally is optional; we just unregister and let player remove
        removeLoader(id, "expired");
    }

    public void startTasks() {
        if (tickTask != null) return;

        // expiry check every 2 seconds
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            List<UUID> toExpire = new ArrayList<>();
            synchronized (this) {
                for (LoaderRecord r : loaders.values()) {
                    if (now >= r.expiresAtMs) {
                        toExpire.add(r.id);
                    }
                }
            }
            for (UUID id : toExpire) {
                expireLoader(id);
            }
        }, 40L, 40L);

        int holoSeconds = Math.max(2, plugin.getConfig().getInt("holograms.update-seconds", 10));
        hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (this) {
                for (LoaderRecord r : loaders.values()) {
                    spawnOrUpdateHologram(r, false);
                }
            }
        }, 20L, holoSeconds * 20L);

        tpsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double minTps = plugin.getConfig().getDouble("tps.min-tps", 18.0);
            double hysteresis = plugin.getConfig().getDouble("tps.hysteresis", 0.5);
            double[] tps = Bukkit.getServer().getTPS();
            double tps1m = tps.length > 0 ? tps[0] : 20.0;

            if (!suspended && tps1m < minTps) {
                suspended = true;
                plugin.getLogger().warning("[RezzLoaders] TPS low (" + String.format("%.2f", tps1m) + ") < " + minTps + ". Suspending loaders.");
                actionLogger.log("TPS_SUSPEND", "tps1m=" + tps1m);
                synchronized (this) {
                    for (LoaderRecord r : loaders.values()) {
                        removeTickets(r);
                    }
                }
            } else if (suspended && tps1m >= (minTps + hysteresis)) {
                suspended = false;
                plugin.getLogger().info("[RezzLoaders] TPS recovered (" + String.format("%.2f", tps1m) + "). Resuming loaders.");
                actionLogger.log("TPS_RESUME", "tps1m=" + tps1m);
                synchronized (this) {
                    for (LoaderRecord r : loaders.values()) {
                        applyTickets(r);
                    }
                }
            }
        }, 200L, 200L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (hologramTask != null) hologramTask.cancel();
        if (tpsTask != null) tpsTask.cancel();

        synchronized (this) {
            for (LoaderRecord r : loaders.values()) {
                removeTickets(r);
                despawnHologram(r);
            }
            saveAll();
        }
    }

    public void loadAll() {
        if (!dataFile.exists()) {
            return;
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("loaders")) return;

        var sec = data.getConfigurationSection("loaders");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID owner = UUID.fromString(sec.getString(key + ".owner"));
                String world = sec.getString(key + ".world");
                int x = sec.getInt(key + ".x");
                int y = sec.getInt(key + ".y");
                int z = sec.getInt(key + ".z");
                String sizeStr = sec.getString(key + ".size");
                LoaderSize size = LoaderSize.fromString(sizeStr);
                if (size == null) {
                    // Backwards-compat / corruption guard: older data or manual edits may omit/invalid size.
                    size = LoaderSize.ONE_BY_ONE;
                    plugin.getLogger().warning("Loader " + id + " had missing/invalid size ('" + sizeStr + "'). Defaulting to 1x1.");
                }
                long created = sec.getLong(key + ".createdAtMs");
                long expires = sec.getLong(key + ".expiresAtMs");

                LoaderRecord rec = new LoaderRecord(id, owner, world, x, y, z, size, created, expires);
                loaders.put(id, rec);

                if (!suspended) {
                    applyTickets(rec);
                }
                spawnOrUpdateHologram(rec, true);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load loader entry: " + key + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + loaders.size() + " loaders from disk.");
    }

    private void applyTickets(LoaderRecord rec) {
        World w = Bukkit.getWorld(rec.world);
        if (w == null) return;
        if (rec.size == null) return; // extra safety; should not happen after load migration

        int cx = rec.x >> 4;
        int cz = rec.z >> 4;

        for (int dx = -rec.size.radius; dx <= rec.size.radius; dx++) {
            for (int dz = -rec.size.radius; dz <= rec.size.radius; dz++) {
                w.addPluginChunkTicket(cx + dx, cz + dz, plugin);
            }
        }

        // After (re)applying tickets, nudge tile entities so comparator/hopper filters don't get stuck
        // after chunk unload/reload or cancelled physics updates.
        if (plugin.getConfig().getBoolean("refresh.enabled", true)) {
            long delay = Math.max(1L, plugin.getConfig().getLong("refresh.delay-ticks", 1L));
            Bukkit.getScheduler().runTaskLater(plugin, () -> refreshTileEntitiesAround(rec), delay);
        }
    }

    private void refreshTileEntitiesAround(LoaderRecord rec) {
        // Best-effort only: never let this throw during tick.
        try {
            World w = Bukkit.getWorld(rec.world);
            if (w == null) return;
            if (rec.size == null) return;

            int cx = rec.x >> 4;
            int cz = rec.z >> 4;

            for (int dx = -rec.size.radius; dx <= rec.size.radius; dx++) {
                for (int dz = -rec.size.radius; dz <= rec.size.radius; dz++) {
                    Chunk c = w.getChunkAt(cx + dx, cz + dz);
                    if (!c.isLoaded()) c.load();

                    // 1) Refresh all tile entities (containers, hoppers, etc.)
                    for (BlockState bs : c.getTileEntities()) {
                        try {
                            bs.update(true, true);
                            Block b = bs.getBlock();
                            // 2) Nudge the container block itself (forces neighbor updates in many cases)
                            nudgeBlock(b);
                            // 3) Specifically resync comparators reading this container
                            nudgeComparatorsAdjacentTo(b);
                        } catch (Throwable ignored) {
                            // ignore per-block
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void nudgeBlock(Block b) {
        try {
            BlockData data = b.getBlockData();
            b.setBlockData(data, true); // applyPhysics=true
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void nudgeComparatorsAdjacentTo(Block container) {
        try {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Block nb = container.getRelative(face);
                if (nb.getType() == Material.COMPARATOR) {
                    nudgeBlock(nb);
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void removeTickets(LoaderRecord rec) {
        World w = Bukkit.getWorld(rec.world);
        if (w == null) return;
        if (rec.size == null) return; // extra safety; should not happen after load migration

        int cx = rec.x >> 4;
        int cz = rec.z >> 4;

        for (int dx = -rec.size.radius; dx <= rec.size.radius; dx++) {
            for (int dz = -rec.size.radius; dz <= rec.size.radius; dz++) {
                w.removePluginChunkTicket(cx + dx, cz + dz, plugin);
            }
        }
    }

    private void spawnOrUpdateHologram(LoaderRecord rec, boolean forceSpawn) {
        if (!plugin.getConfig().getBoolean("holograms.enabled", true)) return;
        if (rec.size == null) return; // extra safety; should not happen after load migration

        World w = Bukkit.getWorld(rec.world);
        if (w == null) return;

        Location base = new Location(w, rec.x + 0.5, rec.y + plugin.getConfig().getDouble("holograms.y-offset", 1.8), rec.z + 0.5);

        TextDisplay display = findExistingTextDisplayNear(base);
        if (display == null && !forceSpawn) {
            // no existing hologram; spawn
            display = w.spawn(base, TextDisplay.class, td -> {
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(false);
                td.setPersistent(false);
            });
        } else if (display == null) {
            display = w.spawn(base, TextDisplay.class, td -> {
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(false);
                td.setPersistent(false);
            });
        }

        if (display != null) {
            long now = System.currentTimeMillis();
            long remainingMs = Math.max(0L, rec.expiresAtMs - now);
            String remaining = TimeUtil.formatDuration(remainingMs);
            int chunkCount = rec.size == LoaderSize.ONE_BY_ONE ? 1 : 25;
            String text = "§6§lChunk Loader\n" +
                    "§e" + rec.size.size + "x" + rec.size.size + " §7(" + chunkCount + " chunks)\n" +
                    "§a" + remaining + "§7 remaining" + (suspended ? "\n§cSuspended (TPS)" : "");
            display.setText(text);
        }
    }

    private TextDisplay findExistingTextDisplayNear(Location loc) {
        double r = 1.5;
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, r, r, r);
        for (Entity e : nearby) {
            if (e instanceof TextDisplay td) {
                // best-effort heuristic: look for our title line
                String t = td.getText();
                if (t != null && t.contains("Chunk Loader")) {
                    return td;
                }
            }
        }
        return null;
    }

    private void despawnHologram(LoaderRecord rec) {
        World w = Bukkit.getWorld(rec.world);
        if (w == null) return;
        Location base = new Location(w, rec.x + 0.5, rec.y + plugin.getConfig().getDouble("holograms.y-offset", 1.8), rec.z + 0.5);
        TextDisplay td = findExistingTextDisplayNear(base);
        if (td != null) {
            td.remove();
        }
    }

    private void removeFromData(UUID id) {
        if (data.isConfigurationSection("loaders")) {
            data.set("loaders." + id.toString(), null);
        }
    }

    private synchronized void saveAll() {
        data.set("loaders", null);
        for (LoaderRecord r : loaders.values()) {
            String path = "loaders." + r.id;
            data.set(path + ".owner", r.owner.toString());
            data.set(path + ".world", r.world);
            data.set(path + ".x", r.x);
            data.set(path + ".y", r.y);
            data.set(path + ".z", r.z);
            // Store as user-friendly labels to avoid parsing issues across versions.
            LoaderSize sz = (r.size != null ? r.size : LoaderSize.ONE_BY_ONE);
            data.set(path + ".size", (sz == LoaderSize.FIVE_BY_FIVE ? "5x5" : "1x1"));
            data.set(path + ".createdAtMs", r.createdAtMs);
            data.set(path + ".expiresAtMs", r.expiresAtMs);
        }
        saveDataFile();
    }

    private void saveDataFile() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save loaders.yml: " + e.getMessage());
        }
    }


    public static class PlaceResult {
        public final boolean success;
        public final String message;
        public final java.util.UUID loaderId;
        public PlaceResult(boolean success, String message, java.util.UUID loaderId) {
            this.success = success;
            this.message = message;
            this.loaderId = loaderId;
        }
    }

    public synchronized java.util.List<LoaderRecord> getLoadersForOwner(java.util.UUID owner) {
        java.util.List<LoaderRecord> out = new java.util.ArrayList<>();
        for (LoaderRecord r : loaders.values()) {
            if (r.owner.equals(owner)) out.add(r);
        }
        return out;
    }

    public synchronized LoaderRecord getByLocation(Location loc) {
        return getByBlock(loc).orElse(null);
    }

    public PlaceResult placeLoader(org.bukkit.entity.Player player, Location loc, LoaderSize size, long durationSec) {
        if (durationSec <= 0) return new PlaceResult(false, "Invalid duration.", null);
        if (isWorldBlacklisted(loc.getWorld().getName())) {
            return new PlaceResult(false, "Chunk loaders are disabled in this world.", null);
        }

        int maxTotal = plugin.getConfig().getInt("limits.max-per-player", 2);
        int max1 = plugin.getConfig().getInt("limits.max-1x1-per-player", 2);
        int max5 = plugin.getConfig().getInt("limits.max-5x5-per-player", 1);

        synchronized (this) {
            int total = countForOwner(player.getUniqueId());
            int bySize = countForOwner(player.getUniqueId(), size);
            if (total >= maxTotal && !player.hasPermission("rezzloaders.admin")) {
                return new PlaceResult(false, "You are at the maximum active loaders limit (" + maxTotal + ").", null);
            }
            if (size == LoaderSize.ONE_BY_ONE && bySize >= max1 && !player.hasPermission("rezzloaders.admin")) {
                return new PlaceResult(false, "You are at the maximum 1x1 loaders limit (" + max1 + ").", null);
            }
            if (size == LoaderSize.FIVE_BY_FIVE && bySize >= max5 && !player.hasPermission("rezzloaders.admin")) {
                return new PlaceResult(false, "You are at the maximum 5x5 loaders limit (" + max5 + ").", null);
            }

            LoaderRecord rec = addLoader(player.getUniqueId(), loc, size, durationSec);
            actionLogger.log("LOADER_PLACE", "id=" + rec.id + " owner=" + rec.owner + " world=" + rec.world + " xyz=" + rec.x + "," + rec.y + "," + rec.z + " size=" + rec.size + " durationSec=" + durationSec);
            return new PlaceResult(true, "", rec.id);
        }
    }

    public synchronized boolean removeById(java.util.UUID id, String actor) {
        if (!loaders.containsKey(id)) return false;
        removeLoader(id, "removed_by_" + actor);
        return true;
    }

    public void reloadFromConfig() {
        // Currently config is read live; this exists for future expansion.
    }

    public void openInfoGui(org.bukkit.entity.Player player, LoaderRecord rec) {
        double tps1m = org.bukkit.Bukkit.getServer().getTPS().length>0 ? org.bukkit.Bukkit.getServer().getTPS()[0] : 20.0;
        GuiUtil.openInfoGui(player, rec, tps1m);
    }

    public String buildInfoLines(LoaderRecord rec) {
        long remainingMs = Math.max(0L, rec.expiresAtMs - System.currentTimeMillis());
        int chunks = rec.size == LoaderSize.ONE_BY_ONE ? 1 : 25;
        String sizeLabel = (rec.size == LoaderSize.ONE_BY_ONE) ? "1x1" : "5x5";
        return org.bukkit.ChatColor.GOLD + "Chunk Loader" + org.bukkit.ChatColor.GRAY + " (" + sizeLabel + ")\n" +
                org.bukkit.ChatColor.YELLOW + "World: " + org.bukkit.ChatColor.WHITE + rec.world + "\n" +
                org.bukkit.ChatColor.YELLOW + "Location: " + org.bukkit.ChatColor.WHITE + rec.x + "," + rec.y + "," + rec.z + "\n" +
                org.bukkit.ChatColor.YELLOW + "Chunks: " + org.bukkit.ChatColor.WHITE + chunks + "\n" +
                org.bukkit.ChatColor.YELLOW + "Remaining: " + org.bukkit.ChatColor.AQUA + TimeUtil.formatDuration(remainingMs) +
                (suspended ? org.bukkit.ChatColor.RED + " (suspended)" : "");
    }

    public static String locShort(Location loc) {
        return loc.getWorld().getName() + "@" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static String formatDuration(long seconds) {
        return TimeUtil.formatDuration(seconds * 1000L);
    }

}