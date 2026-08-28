package com.nhoryzon.mc.farmersdelight.papo.bootstrap;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Bootstrap 阶段注册 Farmer's Delight 的自定义附魔（背刺）
 * 与捆绑的进度数据包（由插件按触发条件授予）。
 */
@SuppressWarnings("UnstableApiUsage")
public class FarmersDelightBootstrap implements PluginBootstrap {

    public static final TypedKey<Enchantment> BACKSTABBING_KEY =
            TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("farmersdelight", "backstabbing"));

    @Override
    public void bootstrap(BootstrapContext context) {
        // Backstabbing moved to a bundled datapack enchantment (1.21 datapack format):
        // datapack/data/farmersdelight/enchantment/backstabbing.json. The datapack
        // registry entry is what the vanilla enchanting table enumerates, so Backstabbing
        // now appears in table offers deterministically (mod parity).

        // discover the bundled advancement datapack inside the plugin jar
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                var uri = Objects.requireNonNull(
                        getClass().getClassLoader().getResource("datapack"), "datapack folder missing").toURI();
                event.registrar().discoverPack(uri, "farmersdelight-advancements");
            } catch (URISyntaxException | IOException e) {
                context.getLogger().error("Failed to discover bundled advancement datapack", e);
            }
        });
    }
}
