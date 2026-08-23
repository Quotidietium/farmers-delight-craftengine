package dev.zurker.fdprobe;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FdProbePlugin extends JavaPlugin {

    private Class<?> sink;
    private Method mEnter, mFlush;
    private Field fArmed, fLines, fDropped;
    private boolean eventsOn = true;
    private Path eventLog;
    private int armTask = -1;

    @Override
    public void onEnable() {
        // no bundled config
        eventLog = getDataFolder().toPath().resolve("events.log");
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(eventLog, "# events.log start " + java.time.LocalDateTime.now() + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            getLogger().warning("event log init failed: " + e);
        }
        getServer().getPluginManager().registerEvents(new EventTracer(this), this);
        getLogger().info("FdProbe ready. /fdprobe inst | arm <ms> | ev on|off | status");
    }

    void sinkInit() {
        if (sink != null) return;
        sink = CaptureAgent.sinkClass();
        if (sink == null) return;
        try {
            mEnter = sink.getMethod("enter", String.class, Object[].class);
            mFlush = sink.getMethod("flush");
            fArmed = sink.getField("ARMED");
            fLines = sink.getField("LINES");
            fDropped = sink.getField("DROPPED");
        } catch (ReflectiveOperationException e) {
            sink = null;
            getLogger().warning("sink reflect failed: " + e);
        }
    }

    boolean armed() {
        try { return sink != null && fArmed.getBoolean(null); } catch (Exception e) { return false; }
    }

    void setArmed(boolean v, long ms) {
        sinkInit();
        if (sink == null) return;
        try {
            fArmed.setBoolean(null, v);
            if (v) {
                mark("ARM +" + ms + "ms");
                if (armTask != -1) getServer().getScheduler().cancelTask(armTask);
                armTask = getServer().getScheduler().runTaskLater(this, () -> {
                    try { fArmed.setBoolean(null, false); mFlush.invoke(null); mark("DISARM(auto)"); } catch (Exception ignored) {}
                    armTask = -1;
                }, Math.max(1, ms / 50)).getTaskId();
            } else {
                if (armTask != -1) { getServer().getScheduler().cancelTask(armTask); armTask = -1; }
                try { mFlush.invoke(null); } catch (Exception ignored) {}
                mark("DISARM");
            }
        } catch (Exception e) {
            getLogger().warning("arm failed: " + e);
        }
    }

    void mark(String text) {
        sinkInit();
        if (sink == null || !armed()) return;
        try { mEnter.invoke(null, "### " + text, null); } catch (Exception ignored) {}
    }

    void ev(String line) {
        if (!eventsOn) return;
        try {
            Files.writeString(eventLog, "[" + System.currentTimeMillis() + "] " + line + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    boolean eventsOn() { return eventsOn; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("inst | arm <ms> | disarm | status | ev on|off | mark <text>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "inst" -> {
                try {
                    System.setProperty("fdprobe.sink.file",
                            getDataFolder().toPath().resolve("capture.log").toString());
                    CaptureAgent.install(getDataFolder().toPath());
                    sinkInit();
                    sender.sendMessage("instrumentation installed, sink=" + (sink != null));
                } catch (Throwable t) {
                    sender.sendMessage("install FAILED: " + t.getMessage());
                    getLogger().log(java.util.logging.Level.WARNING, "install failed", t);
                }
            }
            case "arm" -> {
                long ms = args.length > 1 ? Long.parseLong(args[1]) : 5000;
                setArmed(true, ms);
                sender.sendMessage("armed for " + ms + "ms (lines=" + lines() + ")");
            }
            case "disarm" -> { setArmed(false, 0); sender.sendMessage("disarmed, flushed"); }
            case "status" -> {
                sinkInit();
                sender.sendMessage("inst=" + CaptureAgent.installed() + " sink=" + (sink != null)
                        + " armed=" + armed() + " lines=" + lines() + " dropped=" + dropped()
                        + " events=" + eventsOn);
            }
            case "ev" -> {
                eventsOn = args.length > 1 && args[1].equalsIgnoreCase("on");
                sender.sendMessage("event tracing: " + eventsOn);
            }
            case "mark" -> {
                mark(String.join(" ", args).substring(4));
                sender.sendMessage("marker written (only effective while armed)");
            }
            default -> sender.sendMessage("unknown subcommand");
        }
        return true;
    }

    private long lines() { try { return sink != null ? fLines.getLong(null) : -1; } catch (Exception e) { return -1; } }
    private long dropped() { try { return sink != null ? fDropped.getLong(null) : -1; } catch (Exception e) { return -1; } }
}
