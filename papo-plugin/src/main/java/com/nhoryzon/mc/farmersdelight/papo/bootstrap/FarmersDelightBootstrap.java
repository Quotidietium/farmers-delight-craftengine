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
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
            WritableRegistry<Enchantment, EnchantmentRegistryEntry.Builder> registry = event.registry();
            registry.register(BACKSTABBING_KEY, builder -> builder
                    .description(Component.translatable("farmersdelight.enchantment.backstabbing")
                            .color(NamedTextColor.GRAY))
                    // 刀具的基础材质集合（燧石/铁粒/金粒/钻石/下界合金碎片）
                    .supportedItems(RegistrySet.keySet(RegistryKey.ITEM,
                            ItemTypeKeys.FLINT,
                            ItemTypeKeys.IRON_NUGGET,
                            ItemTypeKeys.GOLD_NUGGET,
                            ItemTypeKeys.DIAMOND,
                            ItemTypeKeys.NETHERITE_SCRAP))
                    .weight(10)
                    .maxLevel(3)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(15, 9))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(65, 9))
                    .anvilCost(4)
                    .activeSlots(EquipmentSlotGroup.MAINHAND)
            );
        });

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
