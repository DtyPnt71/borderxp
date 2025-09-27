package com.brnsvr.borderxp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BorderXPPlugin extends JavaPlugin implements Listener {

    // --- pre config default values ---
    private double multiplier = 2.0;
    private double minDiameter = 2.0;
    private double maxDiameter = 10000000.0;
    private double damageBufferBlocks = 1.0;
    private double damagePerSecond = 1.0;
    private double deathRetainMultiplier = 0.5;
    private long globalTimerSeconds = 0L;
    private int globalXpLevel = 0;
    private float globalXpProgress = 0.0f; // 0.0 .. 1.0
    private boolean syncExpProgress = true; // sync EXP bar globally
    private boolean useFractionalLevel = false;
    private String globalMode = "max"; // max, avg, sum, leader, manual
    private String leaderPermission = "borderxp.leader"; // use (level + progress) for diameter
    private boolean showTimerDefault = true;
    private String centerMode = "spawn";

    // countdown settings // Sound = Bukkit enum name
    private boolean announceBorderChange = true;
    private boolean countdownOnShrink = true;
    private int countdownSeconds = 5;
    private int countdownGraceWindowSeconds = 10;
    private String countdownSound = "BLOCK_NOTE_BLOCK_PLING";
    private String growSound = "BLOCK_NOTE_BLOCK_CHIME";
    private String shrinkSound = "BLOCK_BEACON_DEACTIVATE"; 
    private float countdownPitchStart = 0.9f;
    private float countdownPitchStep = 0.05f;

    //
    private String formatActionbar = " &f&l| &e&l⏱ &r{time} &f&l| &bLvl:&e {max} &f&l| &cSize:&f {target}&r";
    private String prefixColored = "&6&lBorderXP&r &8&7by &b&oBrnSvr&r";
    private String formatJoin = " &av{version}";
    private String formatConsole = " &av{version} loaded sucessfully";

    // countdown chat
    private String msgGrow = "&a&lBorderXP - &7Border has &aextended &7to &a{target} &7(+{amount})";
    private String msgShrink = "&c&lBorderXP &7Border has &cshrunk &7to &c{target} &7(-{amount})";
    private String msgOutsideStart = "&c&lBorderXP &7You are outside the border! &6Go back quickly! &7- You'll take damage in &e{seconds} &7seconds";
    private String msgCountdownNumber = "&e{seconds}";

    //
    private final ConcurrentMap<String, Double> lastDiameter = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, org.bukkit.util.Vector> lastCenter = new ConcurrentHashMap<>();

// Custom centers per world (optional)
private final ConcurrentMap<String, org.bukkit.util.Vector> customCenters = new ConcurrentHashMap<>();
// Respawn invulnerability seconds and storage
private int respawnInvulnSeconds = 5;
private final ConcurrentMap<java.util.UUID, Long> respawnInvulnUntil = new ConcurrentHashMap<>();
private final Map<java.util.UUID, BukkitTask> respawnInvulnTasks = new HashMap<>();
// Buffer before countdown triggers when outside (in blocks)
private double countdownBufferBlocks = 0.5;

    private final Set<UUID> timerEnabled = new HashSet<>();
    private final Map<UUID, Integer> pendingRetainedTotal = new HashMap<>();
    private final Map<UUID, BukkitTask> shrinkCountdownTasks = new HashMap<>();
    private final java.util.Set<UUID> notifiedThisShrink = new java.util.HashSet<>();
    private final java.util.Set<UUID> completedCountdownThisShrink = new java.util.HashSet<>();

    private long lastTickMs = System.currentTimeMillis();
    private BukkitTask task;
    private boolean isSyncingLevels = false;

    private int lastTargetDiameter = -1;
    private long lastShrinkMs = 0L;

    //
    private String version() { return getDescription().getVersion(); }
    private String fmtTime(long sec) {
        long h = sec / 3600; sec %= 3600;
        long m = sec / 60; long s = sec % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }
    private Component render(String fmt, long timeSec, int maxLevel, int targetInt) {
        if (fmt == null) fmt = "";
        String txt = fmt
                .replace("{time}", fmtTime(timeSec))
                .replace("{max}", Integer.toString(maxLevel))
                .replace("{target}", Integer.toString(targetInt))
                .replace("{version}", version());
        String full = prefixColored + txt;
        return LegacyComponentSerializer.legacyAmpersand().deserialize(full);
    }
    private void sendColored(Player p, String txt) {
        Component c = LegacyComponentSerializer.legacyAmpersand().deserialize(txt);
        p.sendMessage(c);
    }

    //
    private static int totalXpForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int)Math.floor(2.5 * level * level - 40.5 * level + 360);
        return (int)Math.floor(4.5 * level * level - 162.5 * level + 2220);
    }
    private static int levelForTotalXp(int total) {
        if (total <= 0) return 0;
        int lvl = 0;
        while (totalXpForLevel(lvl + 1) <= total) lvl++;
        return lvl;
    }
    private static int expToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    @Override
    public void onEnable() {
        loadProps();
        getServer().getPluginManager().registerEvents(this, this);
        if (showTimerDefault) {
            for (Player p : Bukkit.getOnlinePlayers()) timerEnabled.add(p.getUniqueId());
        }
        // Keep persisted globalXpLevel (do not recompute on empty server)
        syncAllPlayersToGlobal();
        tick();
        try { Bukkit.getConsoleSender().sendMessage(render(this.formatConsole, this.globalTimerSeconds, this.globalXpLevel, 0)); } catch (Throwable ignored) {}
        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        for (BukkitTask t : shrinkCountdownTasks.values()) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        shrinkCountdownTasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setWorldBorder(null);
        }
        saveProps();
    }

    private void loadProps() {
        try {
            File f = new File(getDataFolder(), "BorderXP.properties");
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            Properties p = new Properties();
            if (f.exists()) try (FileInputStream in = new FileInputStream(f)) { p.load(in); }

            this.multiplier = Double.parseDouble(p.getProperty("multiplier", Double.toString(this.multiplier)));
            this.minDiameter = Double.parseDouble(p.getProperty("minDiameter", Double.toString(this.minDiameter)));
            this.maxDiameter = Double.parseDouble(p.getProperty("maxDiameter", Double.toString(this.maxDiameter)));
            this.damageBufferBlocks = Double.parseDouble(p.getProperty("damageBufferBlocks", Double.toString(this.damageBufferBlocks)));
            this.damagePerSecond = Double.parseDouble(p.getProperty("damagePerSecond", Double.toString(this.damagePerSecond)));
            this.deathRetainMultiplier = Double.parseDouble(p.getProperty("deathRetainMultiplier", Double.toString(this.deathRetainMultiplier)));
            this.globalTimerSeconds = Long.parseLong(p.getProperty("globalTimerSeconds", Long.toString(this.globalTimerSeconds)));
            this.globalXpLevel = Integer.parseInt(p.getProperty("globalXpLevel", Integer.toString(this.globalXpLevel)));
            try { this.globalXpProgress = Float.parseFloat(p.getProperty("globalXpProgress", Float.toString(this.globalXpProgress))); } catch (Exception ignored) {}
            this.syncExpProgress = Boolean.parseBoolean(p.getProperty("syncExpProgress", Boolean.toString(this.syncExpProgress)));
            this.useFractionalLevel = Boolean.parseBoolean(p.getProperty("useFractionalLevel", Boolean.toString(this.useFractionalLevel)));
            this.globalMode = p.getProperty("globalMode", this.globalMode).toLowerCase();
            this.leaderPermission = p.getProperty("leaderPermission", this.leaderPermission);
            try { this.globalXpProgress = Float.parseFloat(p.getProperty("globalXpProgress", Float.toString(this.globalXpProgress))); } catch (Exception ignored) {}
            this.centerMode = p.getProperty("center", this.centerMode).trim();
            this.showTimerDefault = Boolean.parseBoolean(p.getProperty("showTimerDefault", Boolean.toString(this.showTimerDefault)));
            this.respawnInvulnSeconds = Integer.parseInt(p.getProperty("respawnInvulnSeconds", Integer.toString(this.respawnInvulnSeconds)));
            this.countdownBufferBlocks = Double.parseDouble(p.getProperty("countdownBufferBlocks", Double.toString(this.countdownBufferBlocks)));
            this.centerMode = p.getProperty("center", this.centerMode);
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("center.")) {
                    String id = key.substring("center.".length());
                    String[] parts = p.getProperty(key, "").split(",");
                    if (parts.length >= 2) {
                        try {
                            double x = Double.parseDouble(parts[0].trim());
                            double z = Double.parseDouble(parts[1].trim());
                            customCenters.put(id, new org.bukkit.util.Vector(x, 0.0, z));
                        } catch (Exception ignored) {}
                    }
                }
            }

            //
            this.announceBorderChange = Boolean.parseBoolean(p.getProperty("announceBorderChange", Boolean.toString(this.announceBorderChange)));
            this.countdownOnShrink = Boolean.parseBoolean(p.getProperty("countdownOnShrink", Boolean.toString(this.countdownOnShrink)));
            this.countdownSeconds = Integer.parseInt(p.getProperty("countdownSeconds", Integer.toString(this.countdownSeconds)));
            this.countdownGraceWindowSeconds = Integer.parseInt(p.getProperty("countdownGraceWindowSeconds", Integer.toString(this.countdownGraceWindowSeconds)));
            this.countdownSound = p.getProperty("countdownSound", this.countdownSound);
            this.growSound = p.getProperty("growSound", this.growSound);
            this.shrinkSound = p.getProperty("shrinkSound", this.shrinkSound);
            try { this.countdownPitchStart = Float.parseFloat(p.getProperty("countdownPitchStart", Float.toString(this.countdownPitchStart))); } catch (Exception ignore) {}
            try { this.countdownPitchStep = Float.parseFloat(p.getProperty("countdownPitchStep", Float.toString(this.countdownPitchStep))); } catch (Exception ignore) {}

            this.msgGrow = p.getProperty("msgGrow", this.msgGrow);
            this.msgShrink = p.getProperty("msgShrink", this.msgShrink);
            this.msgOutsideStart = p.getProperty("msgOutsideStart", this.msgOutsideStart);
            this.msgCountdownNumber = p.getProperty("msgCountdownNumber", this.msgCountdownNumber);

            saveProps();
        } catch (Exception ignored) {}
    }

    
private void saveProps() {
    try {
        File f = new File(getDataFolder(), "BorderXP.properties");
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();

        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("# =============================================").append(nl);
        sb.append("#  BorderXP Config").append(nl);
        sb.append("#  by BrnSvr").append(nl);
        sb.append("# =============================================").append(nl).append(nl);

        // General
        sb.append("# GENERAL").append(nl);
        sb.append("# Target diameter = (XP level) * multiplier").append(nl);
        sb.append("multiplier=").append(Double.toString(this.multiplier)).append(nl);
        sb.append("# Synchronize EXP bar across all players").append(nl);
        sb.append("syncExpProgress=").append(Boolean.toString(this.syncExpProgress)).append(nl);
        sb.append("# Use (level + fractional progress) for diameter").append(nl);
        sb.append("useFractionalLevel=").append(Boolean.toString(this.useFractionalLevel)).append(nl);
        sb.append("# How to compute the global XP baseline: max|avg|sum|leader|manual").append(nl);
        sb.append("globalMode=").append(this.globalMode).append(nl);
        sb.append("# Permission used to identify leaders when globalMode=leader").append(nl);
        sb.append("leaderPermission=").append(this.leaderPermission).append(nl);
        //
		sb.append("# MIN/MAX DIAMETER").append(nl);
        sb.append("minDiameter=").append(Double.toString(this.minDiameter)).append(nl);
        sb.append("maxDiameter=").append(Double.toString(this.maxDiameter)).append(nl);
        //
		sb.append("# SHOW TIMER BY DEFAULT").append(nl);
        sb.append("showTimerDefault=").append(Boolean.toString(this.showTimerDefault)).append(nl);
        sb.append(nl);
		// Geometry
        sb.append("# BORDER LOCATION").append(nl);
        sb.append("# center: 'spawn' to use world spawn, 'custom' by use '/borderxp center' command ").append(nl);
        sb.append("center=").append(this.centerMode).append(nl);
        //
		sb.append("# BLOCKS FAR AFTER COUNTDOWN START").append(nl);
        sb.append("countdownBufferBlocks=").append(Double.toString(this.countdownBufferBlocks)).append(nl);
        sb.append(nl);
		// Outside/Damage
        sb.append("# DAMAGE").append(nl);
        sb.append("# Only apply damage if outside distance exceeds this many blocks").append(nl);
        sb.append("damageBufferBlocks=").append(Double.toString(this.damageBufferBlocks)).append(nl);
        sb.append("# Damage per second when outside").append(nl);
        sb.append("damagePerSecond=").append(Double.toString(this.damagePerSecond)).append(nl);
        sb.append(nl);
		// Death/Respawn
        sb.append("# DEATH & RESPAWN").append(nl);
        sb.append("# Fraction of total XP to retain on death (0.0-1.0)").append(nl);
        sb.append("deathRetainMultiplier=").append(Double.toString(this.deathRetainMultiplier)).append(nl);
        sb.append("# Protection after respawn").append(nl);
        sb.append("respawnInvulnSeconds=").append(Integer.toString(this.respawnInvulnSeconds)).append(nl);
        sb.append(nl);
        // UI / Announcements
        sb.append("# UI").append(nl);
        sb.append("# Announce growth/shrink in chat").append(nl);
        sb.append("announceBorderChange=").append(Boolean.toString(this.announceBorderChange)).append(nl);
        sb.append("# When border shrinks and player is outside, start countdown").append(nl);
        sb.append("countdownOnShrink=").append(Boolean.toString(this.countdownOnShrink)).append(nl);
        sb.append("# Seconds before damage starts after a shrink").append(nl);
        sb.append("countdownSeconds=").append(Integer.toString(this.countdownSeconds)).append(nl);
        sb.append("# Within this window after a shrink, outside players get a countdown instead of instant damage").append(nl);
        sb.append("countdownGraceWindowSeconds=").append(Integer.toString(this.countdownGraceWindowSeconds)).append(nl);
        //
		sb.append("# ACTIONBAR").append(nl);
        sb.append("msgGrow=").append(this.msgGrow).append(nl);
        sb.append("msgShrink=").append(this.msgShrink).append(nl);
        sb.append("msgOutsideStart=").append(this.msgOutsideStart).append(nl);
        sb.append("msgCountdownNumber=").append(this.msgCountdownNumber).append(nl);
        sb.append(nl);
        // Audio
        sb.append("# SOUNDS").append(nl);
        sb.append("# Sound each second during countdown").append(nl);
        sb.append("countdownSound=").append(this.countdownSound).append(nl);
        sb.append("# Sound when the border grows/shrinks").append(nl);
        sb.append("growSound=").append(this.growSound).append(nl);
        sb.append("shrinkSound=").append(this.shrinkSound).append(nl);
        sb.append("# Pitch").append(nl);
        sb.append("countdownPitchStart=").append(Float.toString(this.countdownPitchStart)).append(nl);
        sb.append("countdownPitchStep=").append(Float.toString(this.countdownPitchStep)).append(nl);
        sb.append(nl);
        // 
        sb.append("# Live data").append(nl);
        sb.append("globalTimerSeconds=").append(Long.toString(this.globalTimerSeconds)).append(nl);
        sb.append("globalXpLevel=").append(Integer.toString(this.globalXpLevel)).append(nl);
        sb.append("globalXpProgress=").append(Float.toString(this.globalXpProgress)).append(nl);
        sb.append(nl);
        // Per-world custom centers
        sb.append("# Per-world Custom Centers (used when center=custom)").append(nl);
        sb.append("# Format: center.<worldUUID>=<x>,<z>").append(nl);
        for (Map.Entry<String, org.bukkit.util.Vector> e : customCenters.entrySet()) {
            org.bukkit.util.Vector v = e.getValue();
            sb.append("center.").append(e.getKey()).append("=")
              .append(Double.toString(v.getX())).append(",")
              .append(Double.toString(v.getZ())).append(nl);
        }
        sb.append(nl);

        //
        try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    } catch (Exception ignored) {}
}


    
private org.bukkit.util.Vector getCenter(World world) {
    if ("custom".equalsIgnoreCase(this.centerMode)) {
        org.bukkit.util.Vector v = customCenters.get(world.getUID().toString());
        if (v != null) {
            lastCenter.put(world.getUID().toString(), v);
            return v;
        }
    }
    Location spawn = world.getSpawnLocation();
    org.bukkit.util.Vector v = new org.bukkit.util.Vector(spawn.getX() + 0.5, 0.0, spawn.getZ() + 0.5);
    lastCenter.put(world.getUID().toString(), v);
    return v;
}


    private void applyBorderVisual(Player p, double targetDiameter) {
        World w = p.getWorld();
        org.bukkit.util.Vector c = lastCenter.getOrDefault(w.getUID().toString(), getCenter(w));
        WorldBorder wb = p.getWorldBorder();
        if (wb == null) wb = Bukkit.createWorldBorder();
        wb.setCenter(c.getX(), c.getZ());
        wb.setSize(targetDiameter);
        wb.setWarningDistance(Math.max(1, (int) Math.ceil(this.countdownBufferBlocks)));
        p.setWorldBorder(wb);
    }

    private static int clampToInt(double val, double min, double max) {
        if (val < min) val = min;
        if (val > max) val = max;
        return (int)Math.round(val);
    }

    

private boolean isOutside(Player p, WorldBorder wb) {
    if (wb == null) return false;
    Location cLoc = wb.getCenter();
    double half = wb.getSize() / 2.0;
    Location pos = p.getLocation();
    double dx = Math.abs(pos.getX() - cLoc.getX());
    double dz = Math.abs(pos.getZ() - cLoc.getZ());
    return (dx > (half + this.countdownBufferBlocks)) || (dz > (half + this.countdownBufferBlocks));
}



    private void startShrinkCountdownIfNeeded(Player p) {
        if (!this.countdownOnShrink) return;
        if (System.currentTimeMillis() - lastShrinkMs > countdownGraceWindowSeconds * 1000L) return;

        //
        if (shrinkCountdownTasks.containsKey(p.getUniqueId())) return;
        // 
        if (completedCountdownThisShrink.contains(p.getUniqueId())) return;

        WorldBorder wb = p.getWorldBorder();
        if (wb == null || !isOutside(p, wb)) return;

        // 
        final int[] left = new int[] { Math.max(1, this.countdownSeconds) };
        if (!notifiedThisShrink.contains(p.getUniqueId())) {
            sendColored(p, this.msgOutsideStart.replace("{seconds}", Integer.toString(left[0])));
            notifiedThisShrink.add(p.getUniqueId());
        }

        BukkitTask t = Bukkit.getScheduler().runTaskTimer(this, () -> {
            // 
            if (!p.isOnline() || !isOutside(p, p.getWorldBorder())) {
                BukkitTask old = shrinkCountdownTasks.remove(p.getUniqueId());
                if (old != null) old.cancel();
                return;
            }

            // 
            int tickIndex = Math.max(0, this.countdownSeconds - left[0]);
            float pitch = this.countdownPitchStart + this.countdownPitchStep * tickIndex;
            if (pitch < 0.5f) pitch = 0.5f;
            if (pitch > 2.0f) pitch = 2.0f;

            // 
            try {
                Sound s = Sound.valueOf(this.countdownSound);
                p.playSound(p.getLocation(), s, 1.0f, pitch);
            } catch (IllegalArgumentException ignore) {}

            //
            sendColored(p, this.msgCountdownNumber.replace("{seconds}", Integer.toString(left[0])));

            left[0]--;
            if (left[0] <= 0) {
                // 
                completedCountdownThisShrink.add(p.getUniqueId());
                BukkitTask old = shrinkCountdownTasks.remove(p.getUniqueId());
                if (old != null) old.cancel();
            }
        }, 20L, 20L);

        shrinkCountdownTasks.put(p.getUniqueId(), t);
    }

    private void applyOutsideDamage(Player p) {
        // 
        if (shrinkCountdownTasks.containsKey(p.getUniqueId())) return;

        WorldBorder wb = p.getWorldBorder();
        if (wb == null) return;
        Location cLoc = wb.getCenter();
double half = wb.getSize() / 2.0;
Location pos = p.getLocation();
double dx = Math.abs(pos.getX() - cLoc.getX()) - half;
double dz = Math.abs(pos.getZ() - cLoc.getZ()) - half;
double outside = Math.max(dx, dz); // >0 => outside by that many blocks (square)
        if (outside > this.damageBufferBlocks) {
            p.damage(this.damagePerSecond);
        }
    }

    

private void playGrowShrinkSound(Player p, boolean grow) {
    try {
        String name = grow ? this.growSound : this.shrinkSound;
        Sound s = Sound.valueOf(name);
        p.playSound(p.getLocation(), s, 1.0f, 1.0f);
    } catch (IllegalArgumentException ex) {
        // when wrong enum is entered, this function will get ignored
    }
}

private double getEffectiveLevel() { return this.useFractionalLevel ? (this.globalXpLevel + (double)this.globalXpProgress) : (double)this.globalXpLevel; }

    

private int totalXpForLP(int level, float progress) {
    return totalXpForLevel(level) + Math.round(Math.max(0f, Math.min(1f, progress)) * expToNextLevel(level));
}


private void recomputeGlobalFromOnline() {
    if (isSyncingLevels) return;
    if ("manual".equalsIgnoreCase(this.globalMode)) return;

    java.util.List<Player> candidates = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
    if ("leader".equalsIgnoreCase(this.globalMode)) {
        java.util.List<Player> leaders = new java.util.ArrayList<>();
        for (Player p : candidates) {
            if (p.hasPermission(this.leaderPermission)) leaders.add(p);
        }
        if (!leaders.isEmpty()) candidates = leaders;
    }

    int aggrTotal = ("sum".equalsIgnoreCase(this.globalMode) || "avg".equalsIgnoreCase(this.globalMode)) ? 0 : -1;
    int count = candidates.size();
    if (count == 0) return;

    if ("max".equalsIgnoreCase(this.globalMode) || "leader".equalsIgnoreCase(this.globalMode)) {
        int bestTotal = -1; int bestLevel = 0; float bestProg = 0f;
        for (Player p : candidates) {
            int lvl = Math.max(0, p.getLevel());
            float prog = Math.max(0f, Math.min(1f, p.getExp()));
            int total = totalXpForLevel(lvl) + Math.round(prog * expToNextLevel(lvl));
            if (total > bestTotal) { bestTotal = total; bestLevel = lvl; bestProg = prog; }
        }
        if (bestTotal >= 0 && (bestLevel != this.globalXpLevel || Math.abs(bestProg - this.globalXpProgress) > 1e-6)) {
            this.globalXpLevel = bestLevel;
            this.globalXpProgress = bestProg;
            if (this.syncExpProgress) syncAllPlayersToGlobal();
            else {
                this.isSyncingLevels = true;
                try { for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getLevel() != this.globalXpLevel) pl.setLevel(this.globalXpLevel); }
                finally { this.isSyncingLevels = false; }
            }
        }
        
        //
        if (!isSyncingLevels) {
            boolean needsSync = false;
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getLevel() != this.globalXpLevel) { needsSync = true; break; }
                if (this.syncExpProgress) {
                    float progNow = Math.max(0f, Math.min(1f, pl.getExp()));
                    if (Math.abs(progNow - this.globalXpProgress) > 1e-6) { needsSync = true; break; }
                }
            }
            if (needsSync) {
                syncAllPlayersToGlobal();
            }
        }
return;
    }

    //
    for (Player p : candidates) {
        int lvl = Math.max(0, p.getLevel());
        float prog = Math.max(0f, Math.min(1f, p.getExp()));
        int total = totalXpForLevel(lvl) + Math.round(prog * expToNextLevel(lvl));
        aggrTotal += total;
    }
    if ("avg".equalsIgnoreCase(this.globalMode)) {
        aggrTotal = Math.round(aggrTotal / (float) count);
    }

    //
    int rem = aggrTotal;
    int lvl = 0;
    while (true) {
        int req = expToNextLevel(lvl);
        if (rem < req) break;
        rem -= req;
        lvl++;
        if (lvl > 1000) break; // safety
    }
    float prog = 0f;
    int req = expToNextLevel(lvl);
    if (req > 0) prog = Math.max(0f, Math.min(1f, rem / (float) req));

    if (lvl != this.globalXpLevel || Math.abs(prog - this.globalXpProgress) > 1e-6) {
        this.globalXpLevel = lvl;
        this.globalXpProgress = prog;
        if (this.syncExpProgress) syncAllPlayersToGlobal();
        else {
            this.isSyncingLevels = true;
            try { for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getLevel() != this.globalXpLevel) pl.setLevel(this.globalXpLevel); }
            finally { this.isSyncingLevels = false; }
        }
    }

        //
        if (!isSyncingLevels) {
            boolean needsSync = false;
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getLevel() != this.globalXpLevel) { needsSync = true; break; }
                if (this.syncExpProgress) {
                    float progNow = Math.max(0f, Math.min(1f, pl.getExp()));
                    if (Math.abs(progNow - this.globalXpProgress) > 1e-6) { needsSync = true; break; }
                }
            }
            if (needsSync) {
                syncAllPlayersToGlobal();
            }
        }
}


private void tick() {
        recomputeGlobalFromOnline();
        //
        recomputeGlobalFromOnline();
        long now = System.currentTimeMillis();
        long deltaSec = Math.max(0, (now - lastTickMs) / 1000);
        if (deltaSec > 0) {
            this.globalTimerSeconds += deltaSec;
            lastTickMs = now - ((now - lastTickMs) % 1000);
        }
        double maxLevel = getEffectiveLevel();
        int target = clampToInt((int)Math.round(maxLevel * this.multiplier), this.minDiameter, this.maxDiameter);

        // 
        if (announceBorderChange && lastTargetDiameter != -1 && target != lastTargetDiameter) {
            int diff = Math.abs(target - lastTargetDiameter);
            if (target > lastTargetDiameter) {
                String m = msgGrow.replace("{amount}", Integer.toString(diff)).replace("{target}", Integer.toString(target));
                for (Player p : Bukkit.getOnlinePlayers()) { sendColored(p, m); playGrowShrinkSound(p, true); }
            } else {
                String m = msgShrink.replace("{amount}", Integer.toString(diff)).replace("{target}", Integer.toString(target));
                for (Player p : Bukkit.getOnlinePlayers()) { sendColored(p, m); playGrowShrinkSound(p, false); }
                lastShrinkMs = now;
                notifiedThisShrink.clear();
                completedCountdownThisShrink.clear();
            }
        }
        lastTargetDiameter = target;

        for (World w : Bukkit.getWorlds()) {
            lastDiameter.put(w.getUID().toString(), (double)target);
            org.bukkit.util.Vector c = getCenter(w);
            lastCenter.put(w.getUID().toString(), c);
            for (Player p : w.getPlayers()) {
                applyBorderVisual(p, target);
                //
                startShrinkCountdownIfNeeded(p);
                //
                applyOutsideDamage(p);
                Long invUntil = respawnInvulnUntil.get(p.getUniqueId());
                if (invUntil != null) {
                    long nowMs = System.currentTimeMillis();
                    if (nowMs < invUntil) {
                        int secs = (int) Math.ceil((invUntil - nowMs) / 1000.0);
                        Component shield = LegacyComponentSerializer.legacyAmpersand().deserialize("&a&l🛡 Respawn-Protection: &f" + secs + "s");
                        p.sendActionBar(shield);
                        continue;
                    } else {
                        respawnInvulnUntil.remove(p.getUniqueId());
                    }
                }
                if (timerEnabled.contains(p.getUniqueId())) {
                    Component line = render(this.formatActionbar, this.globalTimerSeconds, (int)Math.round(maxLevel), target);
                    p.sendActionBar(line);
                }
            }
        }
    }

    //
    
private void syncAllPlayersToGlobal() {
        this.isSyncingLevels = true;
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getLevel() != this.globalXpLevel) {
                    p.setLevel(this.globalXpLevel);
                }
                if (this.syncExpProgress) {
                    //
                    float prog = Math.max(0f, Math.min(1f, this.globalXpProgress));
                    p.setExp(prog);
                    int total = totalXpForLevel(this.globalXpLevel) + Math.round(prog * expToNextLevel(this.globalXpLevel));
                    p.setTotalExperience(total);
                }
            }
        } finally {
            this.isSyncingLevels = false;
        }
    }


    //
    

@EventHandler
public void onDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player)) return;
    Player p = (Player) e.getEntity();
    Long until = respawnInvulnUntil.get(p.getUniqueId());
    if (until != null && System.currentTimeMillis() < until) {
        e.setCancelled(true);
    }
}
@EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (showTimerDefault) timerEnabled.add(e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = e.getPlayer().getWorld();
            double d = lastDiameter.getOrDefault(w.getUID().toString(), 256.0);
            applyBorderVisual(e.getPlayer(), d);
            if (e.getPlayer().getLevel() != this.globalXpLevel) e.getPlayer().setLevel(this.globalXpLevel);
            if (this.syncExpProgress) {
                float prog = Math.max(0f, Math.min(1f, this.globalXpProgress));
                e.getPlayer().setExp(prog);
                int total = totalXpForLevel(this.globalXpLevel) + Math.round(prog * expToNextLevel(this.globalXpLevel));
                e.getPlayer().setTotalExperience(total);
            }
            try { e.getPlayer().sendMessage(render(this.formatJoin, this.globalTimerSeconds, this.globalXpLevel, 0)); } catch (Throwable ignored) {}
        }, 1L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        boolean posChanged = (
            e.getFrom().getX() != e.getTo().getX() ||
            e.getFrom().getY() != e.getTo().getY() ||
            e.getFrom().getZ() != e.getTo().getZ()
        );
        if (!posChanged) return;
        //
        Player p = e.getPlayer();
        if (shrinkCountdownTasks.containsKey(p.getUniqueId())) {
            if (!isOutside(p, p.getWorldBorder())) {
                BukkitTask t = shrinkCountdownTasks.remove(p.getUniqueId());
                if (t != null) t.cancel();
            }
        }
    }

    @EventHandler
public void onExpChange(org.bukkit.event.player.PlayerExpChangeEvent e) {
    if (isSyncingLevels) return;
    Bukkit.getScheduler().runTask(this, () -> {
        if (isSyncingLevels) return;
        recomputeGlobalFromOnline();
        saveProps();
    });
}

@EventHandler
public void onLevelChange(PlayerLevelChangeEvent e) {
    if (isSyncingLevels) return;
    Bukkit.getScheduler().runTask(this, () -> {
        if (isSyncingLevels) return;
        recomputeGlobalFromOnline();
        saveProps();
    });
}
@EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        int lvl = Math.max(0, p.getLevel());
        float frac = Math.max(0f, Math.min(0.999999f, p.getExp())); // 0..1
        int total = totalXpForLevel(lvl) + Math.round(frac * expToNextLevel(lvl));
        double m = Math.max(0.0, Math.min(1.0, this.deathRetainMultiplier));
        int retain = (int)Math.floor(total * m);
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        pendingRetainedTotal.put(p.getUniqueId(), retain);
    }

    

@EventHandler
public void onQuit(PlayerQuitEvent e) {
    Player p = e.getPlayer();
    BukkitTask t = respawnInvulnTasks.remove(p.getUniqueId());
    if (t != null) t.cancel();
    respawnInvulnUntil.remove(p.getUniqueId());
}

@EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        World w = e.getPlayer().getWorld();
        org.bukkit.util.Vector c = lastCenter.getOrDefault(w.getUID().toString(), getCenter(w));
        Location spawn = w.getSpawnLocation();
        Location centerLoc = new Location(w, c.getX(), spawn.getY(), c.getZ());
        e.setRespawnLocation(centerLoc);
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = e.getPlayer();
            Integer retain = pendingRetainedTotal.remove(p.getUniqueId());
            if (retain != null) {
                int lvl = levelForTotalXp(retain);
                p.setLevel(lvl);
                p.setExp(0.0f);
                int progress = retain - totalXpForLevel(lvl);
                if (progress > 0) p.giveExp(progress);
                if (!isSyncingLevels && this.globalXpLevel != lvl) {
                    this.globalXpLevel = lvl;
                    syncAllPlayersToGlobal();
                    saveProps();
                }
            } else {
                if (p.getLevel() != this.globalXpLevel) p.setLevel(this.globalXpLevel);
            }
            double d = lastDiameter.getOrDefault(w.getUID().toString(), 256.0);
            applyBorderVisual(p, d);
            //
            long until = System.currentTimeMillis() + (long) (this.respawnInvulnSeconds * 1000L);
            respawnInvulnUntil.put(p.getUniqueId(), until);
            BukkitTask old = respawnInvulnTasks.remove(p.getUniqueId()); if (old != null) old.cancel();
            BukkitTask t = Bukkit.getScheduler().runTaskTimer(this, () -> {
                long now = System.currentTimeMillis();
                if (now >= until) {
                    respawnInvulnUntil.remove(p.getUniqueId());
                    BukkitTask me = respawnInvulnTasks.remove(p.getUniqueId()); if (me != null) me.cancel();
                    return;
                }
                int secs = (int) Math.ceil((until - now) / 1000.0);
                Component line = LegacyComponentSerializer.legacyAmpersand().deserialize("&a&l🛡 Respawn-Protection: &f" + secs + "s");
                p.sendActionBar(line);
            }, 0L, 20L);
            respawnInvulnTasks.put(p.getUniqueId(), t);
        });
    }

    //

private boolean hasPerm(CommandSender sender, String node) {
    if (sender == null) return false;
    if (sender.hasPermission("borderxp.admin")) return true;
    return sender.hasPermission(node);
}

public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("borderxp")) return false;
        if (args.length == 0) {
            sender.sendMessage("/borderxp center here");
            sender.sendMessage("/borderxp center <x> <z> [world]");
            sender.sendMessage("/borderxp center reset [world|all]");
            sender.sendMessage("/borderxp set size <diameter>");
            sender.sendMessage("/borderxp set multiplier <value>");
            sender.sendMessage("/borderxp timer");
            sender.sendMessage("/borderxp reload");
            sender.sendMessage("/borderxp setglobal <level> [progress]");
            sender.sendMessage("/borderxp globalmode <max|avg|sum|leader|manual>");
            sender.sendMessage("/borderxp capturefrom <player>");
            return true;
        }

//
if (args[0].equalsIgnoreCase("setglobal")) {
    if (!hasPerm(sender, "borderxp.setglobal")) { sender.sendMessage("No permission."); return true; }
    if (args.length < 2) { sender.sendMessage("Usage: /borderxp setglobal <level> [progress 0..1]"); return true; }
    int lvl;
    try { lvl = Math.max(0, Integer.parseInt(args[1])); } catch (Exception ex) { sender.sendMessage("Invalid level."); return true; }
    float prog = this.globalXpProgress;
    if (args.length >= 3) {
        try { prog = Float.parseFloat(args[2]); } catch (Exception ex) { sender.sendMessage("Invalid progress."); return true; }
    }
    this.globalXpLevel = lvl;
    this.globalXpProgress = Math.max(0f, Math.min(1f, prog));
    // Switch to manual so auto-recompute doesn't override admin choice
    this.globalMode = "manual";
    saveProps();
    syncAllPlayersToGlobal();
    sender.sendMessage("Global XP set to level " + lvl + " (progress " + this.globalXpProgress + "), mode=manual.");
    return true;
}

//
if (args[0].equalsIgnoreCase("globalmode")) {
    if (!hasPerm(sender, "borderxp.globalmode")) { sender.sendMessage("No permission."); return true; }
    if (args.length < 2) { sender.sendMessage("Usage: /borderxp globalmode <max|avg|sum|leader|manual>"); return true; }
    String m = args[1].toLowerCase();
    if (!m.equals("max") && !m.equals("avg") && !m.equals("sum") && !m.equals("leader") && !m.equals("manual")) {
        sender.sendMessage("Invalid mode. Use: max|avg|sum|leader|manual");
        return true;
    }
    this.globalMode = m;
    saveProps();
    //
    if (!"manual".equals(this.globalMode)) recomputeGlobalFromOnline();
    sender.sendMessage("Global mode set to " + this.globalMode + ".");
    return true;
}

//
if (args[0].equalsIgnoreCase("capturefrom")) {
    if (!hasPerm(sender, "borderxp.setglobal")) { sender.sendMessage("No permission."); return true; }
    if (args.length < 2) { sender.sendMessage("Usage: /borderxp capturefrom <player>"); return true; }
    Player target = Bukkit.getPlayerExact(args[1]);
    if (target == null) { sender.sendMessage("Player not found."); return true; }
    int lvl = Math.max(0, target.getLevel());
    float prog = Math.max(0f, Math.min(1f, target.getExp()));
    this.globalXpLevel = lvl;
    this.globalXpProgress = prog;
    this.globalMode = "manual";
    saveProps();
    syncAllPlayersToGlobal();
    sender.sendMessage("Captured global XP from " + target.getName() + ": level " + lvl + " (progress " + prog + "), mode=manual.");
    return true;
}
        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasPerm(sender, "borderxp.reload")) { sender.sendMessage("No permission."); return true; }
            loadProps();
            for (World w : Bukkit.getWorlds()) {
                double maxLevel = getEffectiveLevel();
                int target = clampToInt((int)Math.round(maxLevel * this.multiplier), this.minDiameter, this.maxDiameter);
                for (Player p : w.getPlayers()) {
                    applyBorderVisual(p, target);
                }
            }
            sender.sendMessage("BorderXP reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 3) {
            if (!hasPerm(sender, "borderxp.set") && !hasPerm(sender, "borderxp.set."+args[1].toLowerCase())) { sender.sendMessage("No permission."); return true; }
            if (args[1].equalsIgnoreCase("size")) {
                try {
                    double dia = Double.parseDouble(args[2]);
                    dia = Math.max(this.minDiameter, Math.min(this.maxDiameter, dia));
                    for (World w : Bukkit.getWorlds()) {
                        lastDiameter.put(w.getUID().toString(), dia);
                        for (Player p : w.getPlayers()) applyBorderVisual(p, dia);
                    }
                    sender.sendMessage("Diameter set to " + (int)dia);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Invalid number.");
                }
                return true;
            } else if (args[1].equalsIgnoreCase("multiplier")) {
                try {
                    this.multiplier = Double.parseDouble(args[2]);
                    sender.sendMessage("Multiplier set to " + this.multiplier);
                    saveProps();
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Invalid number.");
                }
                return true;
            }
        }
        

if (args[0].equalsIgnoreCase("center")) {
    if (!hasPerm(sender, "borderxp.center")) { sender.sendMessage("No permissions!."); return true; }

    if (args.length == 2 && args[1].equalsIgnoreCase("here")) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players"); return true; }
        Player pl = (Player) sender;
        World w = pl.getWorld();
        double x = Math.floor(pl.getLocation().getX()) + 0.5;
        double z = Math.floor(pl.getLocation().getZ()) + 0.5;
        String id = w.getUID().toString();
        customCenters.put(id, new org.bukkit.util.Vector(x, 0.0, z));
        this.centerMode = "custom";
        lastCenter.put(id, new org.bukkit.util.Vector(x, 0.0, z));
        double dia = lastDiameter.getOrDefault(id, 256.0);
        for (Player t : w.getPlayers()) applyBorderVisual(t, dia);
        saveProps();
        sender.sendMessage("Border center for world '" + w.getName() + "' set to " + x + " / " + z + ".");
        return true;
    }

    if (args.length >= 3 && !args[1].equalsIgnoreCase("reset")) {
        try {
            double x = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);
            World w = null;
            if (args.length >= 4) {
                w = Bukkit.getWorld(args[3]);
                if (w == null) { sender.sendMessage("Unknown world: " + args[3]); return true; }
            } else if (sender instanceof Player) {
                w = ((Player) sender).getWorld();
            } else {
                sender.sendMessage("Please specify a world.");
                return true;
            }
            String id = w.getUID().toString();
            customCenters.put(id, new org.bukkit.util.Vector(x, 0.0, z));
            this.centerMode = "custom";
            lastCenter.put(id, new org.bukkit.util.Vector(x, 0.0, z));
            double dia = lastDiameter.getOrDefault(id, 256.0);
            for (Player t : w.getPlayers()) applyBorderVisual(t, dia);
            saveProps();
            sender.sendMessage("Border center for world '" + w.getName() + "' set to " + x + " / " + z + ".");
        } catch (NumberFormatException ex) {
            sender.sendMessage("Please provide valid numbers for <x> <z>.");
        }
        return true;
    }

    if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
        if (args.length == 3 && args[2].equalsIgnoreCase("all")) {
            customCenters.clear();
            this.centerMode = "spawn";
            saveProps();
            sender.sendMessage("All custom centers cleared.");
            return true;
        }
        World w = (sender instanceof Player) ? ((Player) sender).getWorld() : null;
        if (args.length == 3) {
            World maybe = Bukkit.getWorld(args[2]);
            if (maybe == null) { sender.sendMessage("Unknown world: " + args[2]); return true; }
            w = maybe;
        }
        if (w == null) { sender.sendMessage("Please specify a world."); return true; }
        String id = w.getUID().toString();
        customCenters.remove(id);
        saveProps();
        sender.sendMessage("Custom center for world '" + w.getName() + "' removed (fallback to spawn).");
        return true;
    }

    sender.sendMessage("Usage: /borderxp center here | <x> <z> [world] | reset [world|all]");
    return true;
}
if (args[0].equalsIgnoreCase("timer")) { if (!hasPerm(sender, "borderxp.timer")) { sender.sendMessage("No permission."); return true; }
            if (sender instanceof Player) {
                Player p = (Player)sender;
                UUID id = p.getUniqueId();
                if (timerEnabled.contains(id)) {
                    timerEnabled.remove(id);
                    p.sendMessage("BorderXP timer: OFF");
                } else {
                    timerEnabled.add(id);
                    p.sendMessage("BorderXP timer: ON");
                }
                return true;
            } else {
                sender.sendMessage("Only players can toggle the timer.");
                return true;
            }
        }
        return false;
    }


public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
    java.util.List<String> all = new java.util.ArrayList<>();
    if (!cmd.getName().equalsIgnoreCase("borderxp")) return all;
    if (args.length == 1) {
                if (hasPerm(sender, "borderxp.reload")) all.add("reload");
        if (hasPerm(sender, "borderxp.set")) all.add("set");
        if (hasPerm(sender, "borderxp.center")) all.add("center");
        if (hasPerm(sender, "borderxp.timer")) all.add("timer");
        if (hasPerm(sender, "borderxp.setglobal")) all.add("setglobal");
        if (hasPerm(sender, "borderxp.globalmode")) all.add("globalmode");
        if (hasPerm(sender, "borderxp.setglobal")) all.add("capturefrom");
        return filterPrefix(all, args[0]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
        if (hasPerm(sender, "borderxp.set")) {
            all.add("size");
            all.add("multiplier");
        }
        return filterPrefix(all, args[1]);
    }
    if (args[0].equalsIgnoreCase("center")) {
        if (args.length == 2) {
            if (hasPerm(sender, "borderxp.center")) {
                all.add("here");
                all.add("reset");
            }
            return filterPrefix(all, args[1]);
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
            all.add("all");
            for (World w : Bukkit.getWorlds()) all.add(w.getName());
            return filterPrefix(all, args[2]);
        }
        if (args.length >= 4 && !args[1].equalsIgnoreCase("reset")) {
            for (World w : Bukkit.getWorlds()) all.add(w.getName());
            return filterPrefix(all, args[3]);
        }
    }
    //
    if (args.length >= 1 && args[0].equalsIgnoreCase("globalmode")) {
        if (!hasPerm(sender, "borderxp.globalmode")) return filterPrefix(all, args.length>=2?args[1]:"");
        if (args.length == 2) {
            all.add("max"); all.add("avg"); all.add("sum"); all.add("leader"); all.add("manual");
            return filterPrefix(all, args[1]);
        }
    }
    if (args.length >= 1 && args[0].equalsIgnoreCase("setglobal")) {
        if (!hasPerm(sender, "borderxp.setglobal")) return filterPrefix(all, args.length>=2?args[1]:"");
        if (args.length == 2) {
            int g = Math.max(0, this.globalXpLevel);
            int[] sugg = new int[]{g, Math.max(0,g-5), g+5, 0, 10, 30, 50};
            java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
            for (int v : sugg) uniq.add(Integer.toString(v));
            all.addAll(uniq);
            return filterPrefix(all, args[1]);
        }
        if (args.length == 3) {
            all.add("0"); all.add("0.25"); all.add("0.5"); all.add("0.75"); all.add("1");
            return filterPrefix(all, args[2]);
        }
    }
    if (args.length >= 1 && args[0].equalsIgnoreCase("capturefrom")) {
        if (!hasPerm(sender, "borderxp.setglobal")) return filterPrefix(all, args.length>=2?args[1]:"");
        if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) all.add(p.getName());
            return filterPrefix(all, args[1]);
        }
    }
    return all;
}

private java.util.List<String> filterPrefix(java.util.List<String> items, String prefix) {
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String s : items) if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
    return out;
}

}