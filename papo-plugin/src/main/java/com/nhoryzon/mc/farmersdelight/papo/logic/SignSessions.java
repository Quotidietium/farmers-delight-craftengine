package com.nhoryzon.mc.farmersdelight.papo.logic;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

/** Chat-driven text entry for canvas signs (server-side replacement for sign editing). */
public final class SignSessions implements Listener {

    private record Session(BukkitFurniture sign, int line) {
    }

    private final FarmersDelightPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SignSessions(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, BukkitFurniture sign) {
        sessions.put(player.getUniqueId(), new Session(sign, 0));
        player.sendMessage(Component.translatable("farmersdelight.sign.edit", NamedTextColor.GOLD));
        render(sign, "");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        String plain = plain(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plain.equalsIgnoreCase("cancel") || plain.equalsIgnoreCase("done")
                    || plain.equalsIgnoreCase("完成") || plain.isEmpty() && session.line() > 0) {
                sessions.remove(event.getPlayer().getUniqueId());
                return;
            }
            append(session.sign(), session.line(), plain);
            if (session.line() >= 3) {
                sessions.remove(event.getPlayer().getUniqueId());
            } else {
                sessions.put(event.getPlayer().getUniqueId(),
                        new Session(session.sign(), session.line() + 1));
            }
        });
    }

    private final Map<UUID, java.util.List<String>> lines = new ConcurrentHashMap<>();

    private void append(BukkitFurniture sign, int line, String text) {
        var existing = lines.computeIfAbsent(sign.baseEntity().getUniqueId(),
                k -> new java.util.concurrent.CopyOnWriteArrayList<String>());
        while (existing.size() <= line) existing.add("");
        existing.set(line, text);
        render(sign, String.join("\n", existing));
    }

    private void render(BukkitFurniture sign, String text) {
        var pdc = GameTicker.data(sign);
        String existingId = pdc.get(GameTicker.fdKey("text"), PersistentDataType.STRING);
        TextDisplay display;
        if (existingId != null) {
            var entity = Bukkit.getEntity(UUID.fromString(existingId));
            if (entity instanceof TextDisplay td) {
                td.text(Component.text(text, NamedTextColor.BLACK));
                return;
            }
        }
        var loc = sign.location().clone().add(0.5, 0.7, 0.5);
        display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(Component.text(text == null || text.isEmpty() ? " " : text, NamedTextColor.BLACK));
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setLineWidth(120);
            td.setSeeThrough(false);
            td.setShadowed(false);
            td.setViewRange(0.4f);
            td.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, -0.28f), new org.joml.Quaternionf(),
                    new Vector3f(1f, 1f, 1f), new org.joml.Quaternionf()));
            td.setPersistent(true);
        });
        pdc.set(GameTicker.fdKey("text"), PersistentDataType.STRING, display.getUniqueId().toString());
    }

    private String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }
}
