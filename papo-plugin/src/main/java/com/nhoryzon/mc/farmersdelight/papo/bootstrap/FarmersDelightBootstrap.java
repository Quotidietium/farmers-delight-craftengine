package com.nhoryzon.mc.farmersdelight.papo.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
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

/**
 * Bootstrap 阶段注册 Farmer's Delight 的自定义附魔（背刺）。
 * 附魔的数值效果由主插件的伤害监听器实现（Paper 附魔构建器不含效果组件）。
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
    }
}
