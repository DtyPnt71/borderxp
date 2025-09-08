package com.brnsvr.borderxp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.Server;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandManager;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.ConstructPluginEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector2d;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin("borderxp")
public class BorderXPPlugin {

    @Inject private PluginContainer container;

    //
    private double multiplier = 2.0;
    private double minDiameter = 2.0;
    private double maxDiameter = 1_000_000.0;
    private int tickInterval = 20;
    private String centerMode = "spawn";
    private boolean showTimerDefault = true;

    
    //
    private int globalXpLevel = 0;
//
    //
    private String formatActionbar = " | Playtime: {time} | MaxLvl: {max} | Target: {target}";
    //
    private String prefixColored = "&aBorderXP by BrnSvr";
    private String formatJoin = " v{version}";
    private String formatConsole = " v{version} loaded";

    //
    private ScheduledTask task;
    private final Map<String, Double> lastDiameter = new HashMap<>();
    private final Map<String, Vector2d> lastCenter = new HashMap<>();
    private final Set<UUID> timerEnabled = ConcurrentHashMap.newKeySet();
    private static final double EPS = 0.01;

    //
    private long globalTimerSeconds = 0L;
    private long lastTickMs = System.currentTimeMillis();
    private long lastPersistMs = System.currentTimeMillis();

    //
    private Path configPath() {
        return Sponge.game().gameDirectory().resolve("config").resolve("borderxp.properties");
    }
    private String version() { return container.metadata().version().toString(); }
    private static String fmtTime(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
    private static double parseDouble(String s, double def) {
        try { return (s == null) ? def : Double.parseDouble(s.trim()); }
        catch (Exception e) { return def; }
    }
    private String brandPrefix() { return "BorderXP by BrnSvr"; }
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

    //
    @Listener
    public void onConstruct(final ConstructPluginEvent event) throws IOException {
        // Ensure default config exists
        Path cfg = configPath();
        if (!Files.exists(cfg)) {
            Files.createDirectories(cfg.getParent());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("borderxp.properties")) {
                if (in != null) Files.copy(in, cfg);
            }
        }
        loadConfig();
        //
        container.logger().info("BorderXP loaded - by BrnSvr " + version());
    }

    @Listener
    public void onPlayerJoin(final ServerSideConnectionEvent.Join event) {
        final ServerPlayer p = event.player();
        try { p.offer(Keys.EXPERIENCE_LEVEL, this.globalXpLevel); } catch (Exception ignored) {} enforceBorderForPlayer(p);
        p.sendMessage(render(this.formatJoin, this.globalTimerSeconds, computeMaxLevel(), (int) clamp(computeMaxLevel()*this.multiplier, this.minDiameter, this.maxDiameter)));
        if (showTimerDefault) {
            timerEnabled.add(p.uniqueId());
        }
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        final Parameter.Value<Double> DIAMETER = Parameter.doubleNumber().key("diameter").build();
        final Parameter.Value<Double> MULTIPLIER = Parameter.doubleNumber().key("value").build();

        var info = Command.builder()
            .shortDescription(Component.text("Show BorderXP info"))
            .executor(ctx -> { ctx.sendMessage(render(" v{version}", this.globalTimerSeconds, computeMaxLevel(), 0)); return CommandResult.success(); })
            .build();

        var setSize = Command.builder()
            .shortDescription(Component.text("Set world border diameter now"))
            .addParameter(DIAMETER)
            .executor(ctx -> {
                double dia = ctx.requireOne(DIAMETER);
                setGlobalDiameter(dia);
                ctx.sendMessage(Component.text("Border diameter set to " + dia, NamedTextColor.YELLOW));
                return CommandResult.success();
            }).build();

        var setMult = Command.builder()
            .shortDescription(Component.text("Set multiplier (affects future automatic updates)"))
            .addParameter(MULTIPLIER)
            .executor(ctx -> {
                double v = ctx.requireOne(MULTIPLIER);
                this.multiplier = v;
                saveConfigKey("multiplier", Double.toString(v));
                ctx.sendMessage(Component.text("Multiplier set to " + v, NamedTextColor.YELLOW));
                return CommandResult.success();
            }).build();

        var setGroup = Command.builder()
            .shortDescription(Component.text("Set settings"))
            .addChild(setSize, "size")
            .addChild(setMult, "multiplier")
            .build();

        var timerToggle = Command.builder()
            .shortDescription(Component.text("Toggle ActionBar timer"))
            .executor(ctx -> {
                var root = ctx.cause().root();
                if (root instanceof ServerPlayer p) {
                    UUID id = p.uniqueId();
                    if (timerEnabled.contains(id)) {
                        timerEnabled.remove(id);
                        p.sendMessage(Component.text("BorderXP timer: OFF", NamedTextColor.RED));
                    } else {
                        timerEnabled.add(id);
                        p.sendMessage(Component.text("BorderXP timer: ON", NamedTextColor.GREEN));
                    }
                    return CommandResult.success();
                }
                return CommandResult.error(Component.text("Only players can toggle the timer."));
            }).build();

        var reload = Command.builder()
            .shortDescription(Component.text("Reload BorderXP config"))
            .executor(ctx -> {
                try { loadConfig(); ctx.sendMessage(Component.text("BorderXP config reloaded.", NamedTextColor.GREEN)); return CommandResult.success(); }
                catch (Exception e) { return CommandResult.error(Component.text("Failed: " + e.getMessage(), NamedTextColor.RED)); }
            }).build();

        var root = Command.builder()
            .shortDescription(Component.text("BorderXP controls"))
            .addChild(info, "info")
            .addChild(setGroup, "set")
            .addChild(timerToggle, "timer")
            .addChild(reload, "reload")
            .build();

        event.register(this.container, root, "levelborder", "borderxp", "lb");
    }

    private void loadConfig() throws IOException {
        Properties p = new Properties();
        try (var in = Files.newInputStream(configPath())) {
            p.load(in);
        }
        this.multiplier = parseDouble(p.getProperty("multiplier"), 2.0);
        this.minDiameter = parseDouble(p.getProperty("minDiameter"), 2.0);
        this.maxDiameter = parseDouble(p.getProperty("maxDiameter"), 1_000_000.0);
        this.tickInterval = (int) parseDouble(p.getProperty("tickInterval"), 20);
        this.centerMode = p.getProperty("center", "spawn").trim();
        this.showTimerDefault = Boolean.parseBoolean(p.getProperty("showTimerDefault", "true"));
        //
        this.formatActionbar = p.getProperty("message.actionbar", this.formatActionbar);
        this.formatJoin = p.getProperty("message.join", this.formatJoin);
        this.formatConsole = p.getProperty("message.console", this.formatConsole);
        //
        try { this.globalTimerSeconds = Long.parseLong(p.getProperty("globalTimerSeconds", "0")); } catch (Exception ignored) {}
    }

    private void saveConfigKey(String key, String value) {
        try {
            var props = new Properties();
            var cfg = configPath();
            if (Files.exists(cfg)) {
                try (var in = Files.newInputStream(cfg)) { props.load(in); }
            }
            props.setProperty(key, value);
            try (var out = Files.newOutputStream(cfg)) { props.store(out, "BorderXP"); }
        } catch (Exception ignored) {}
    }
    private void persistGlobalTimer() {
        try {
            var props = new Properties();
            var cfg = configPath();
            if (Files.exists(cfg)) {
                try (var in = Files.newInputStream(cfg)) { props.load(in); }
            }
            props.setProperty("globalTimerSeconds", Long.toString(this.globalTimerSeconds));
                        props.setProperty("globalXpLevel", Integer.toString(this.globalXpLevel));
try (var out = Files.newOutputStream(cfg)) { props.store(out, "BorderXP"); }
        } catch (Exception ignored) {}
    }

    @Listener
    public void onStartedEngine(final StartedEngineEvent<Server> event) {
        //
        try {
            var msg = render(this.formatConsole, this.globalTimerSeconds, computeMaxLevel(), (int) clamp(computeMaxLevel()*this.multiplier, this.minDiameter, this.maxDiameter));
            Sponge.systemSubject().sendMessage(msg);
        } catch (Throwable ignored) {}
        //
        if (showTimerDefault) {
            for (ServerPlayer p : Sponge.server().onlinePlayers()) {
                timerEnabled.add(p.uniqueId());
            }
        }
        //
        if (this.task != null) this.task.cancel();
        this.task = Sponge.server().scheduler().submit(
            Task.builder()
                .plugin(this.container)
                .interval(Duration.ofMillis(50L * Math.max(1, this.tickInterval)))
                .execute(this::tick)
                .build()
        );
    }

    @Listener
    public void onStoppingEngine(final StoppingEngineEvent<Server> event) {
        if (this.task != null) this.task.cancel();
        persistGlobalTimer();
    }

    //
    private void tick() {
        //
        long nowMs = System.currentTimeMillis();
        long deltaMs = nowMs - lastTickMs;
        if (deltaMs >= 1000) {
            this.globalTimerSeconds += (deltaMs / 1000);
            lastTickMs = nowMs - (deltaMs % 1000);
        }
        if (nowMs - lastPersistMs >= 5000) {
            persistGlobalTimer();
            lastPersistMs = nowMs;
        }


        //
        for (ServerPlayer p : Sponge.server().onlinePlayers()) {
            try {
                int lvl = p.experienceLevel().get();
                if (lvl != this.globalXpLevel) {
                    this.globalXpLevel = lvl;
                    this.lastPersistMs = 0;
                    break;
                }
            } catch (Exception ignored) {}
        }
        updateBorders();
        for (ServerPlayer p : Sponge.server().onlinePlayers()) { enforceBorderForPlayer(p); }
double targetDia = clamp(this.globalXpLevel * this.multiplier, this.minDiameter, this.maxDiameter);
        int targetInt = (int) targetDia;

        Component line = render(this.formatActionbar, this.globalTimerSeconds, this.globalXpLevel, targetInt);
        for (ServerPlayer p : Sponge.server().onlinePlayers()) {
            if (timerEnabled.contains(p.uniqueId())) {
                p.sendActionBar(line);
            }
        }
    }

    private int computeMaxLevel() {
        int maxLevel = 1;
        for (ServerPlayer p : Sponge.server().onlinePlayers()) {
            int lvl = p.experienceLevel().get();
            if (lvl > maxLevel) maxLevel = lvl;
        }
        return maxLevel;
    }

    private void setGlobalDiameter(double diameter) throws CommandException {
        diameter = clamp(diameter, this.minDiameter, this.maxDiameter);
        CommandManager cm = Sponge.server().commandManager();
        var console = Sponge.systemSubject();
        for (ServerWorld world : Sponge.server().worldManager().worlds()) {
            cm.process(console, "worldborder set " + diameter);
            lastDiameter.put(world.key().formatted(), diameter);
        }
    }

    private void updateBorders() {

        //
        for (ServerPlayer p : Sponge.server().onlinePlayers()) {
            try {
                Integer current = p.experienceLevel().get();
                if (current != null && current != this.globalXpLevel) {
                    p.offer(Keys.EXPERIENCE_LEVEL, this.globalXpLevel);
                }
            } catch (Exception ignored) {}
        }

        double diameter = clamp(this.globalXpLevel * this.multiplier, this.minDiameter, this.maxDiameter);

        CommandManager cm = Sponge.server().commandManager();
        var console = Sponge.systemSubject();

        for (ServerWorld world : Sponge.server().worldManager().worlds()) {
            String key = world.key().formatted();

            //
            Double lastDia = lastDiameter.get(key);
            if (lastDia == null || Math.abs(lastDia - diameter) > EPS) {
                try {
                    cm.process(console, "worldborder set " + diameter);
                    lastDiameter.put(key, diameter);
                } catch (CommandException ignored) {}
            }

            //
            if ("spawn".equalsIgnoreCase(this.centerMode)) {
                Vector3i spawn = world.properties().spawnPosition();
                Vector2d newC = Vector2d.from(spawn.x() + 0.5, spawn.z() + 0.5);
                Vector2d prev = lastCenter.get(key);
                if (prev == null || prev.distance(newC) > EPS) {
                    try {
                        cm.process(console, "worldborder center " + newC.x() + " " + newC.y());
                        lastCenter.put(key, newC);
                    } catch (CommandException ignored) {}
                }
            } else {
                lastCenter.remove(key);
            }
        }
    }
    private void enforceBorderForPlayer(ServerPlayer player) {
        ServerWorld world = (ServerWorld) player.world();
        String key = world.key().formatted();
        Double dia = lastDiameter.get(key);
        Vector2d center = lastCenter.get(key);
        if (dia == null || center == null) return;

        double radius = dia / 2.0;
        var pos = player.position();
        double dx = pos.x() - center.x();
        double dz = pos.z() - center.y();
        double dist = Math.sqrt(dx*dx + dz*dz);
        if (dist > radius) {
            double scale = (radius - 0.05) / (dist == 0 ? 1 : dist);
            double newX = center.x() + dx * scale;
            double newZ = center.y() + dz * scale;
            player.setPosition(new org.spongepowered.math.vector.Vector3d(newX, pos.y(), newZ));
            player.sendActionBar(Component.text("Du wurdest zur Border zurückgesetzt", NamedTextColor.RED));
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
    @Listener
    public void onMove(org.spongepowered.api.event.entity.MoveEntityEvent event) {
        if (!(event.entity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.entity();
        ServerWorld world = (ServerWorld) player.world();
        String key = world.key().formatted();
        Double dia = lastDiameter.get(key);
        Vector2d center = lastCenter.get(key);
        if (dia == null || center == null) return;

        double radius = dia / 2.0;
        var to = event.destinationPosition();
        double dx = to.x() - center.x();
        double dz = to.z() - center.y();
        double dist = Math.sqrt(dx*dx + dz*dz);

        if (dist > radius - 0.01) {
            event.setCancelled(true);
            double scale = (radius - 0.05) / (dist == 0 ? 1 : dist);
            double newX = center.x() + dx * scale;
            double newZ = center.y() + dz * scale;
            player.setPosition(new org.spongepowered.math.vector.Vector3d(newX, player.position().y(), newZ));
            player.sendActionBar(Component.text("Border limit reached", NamedTextColor.RED));
        }
    }

}
