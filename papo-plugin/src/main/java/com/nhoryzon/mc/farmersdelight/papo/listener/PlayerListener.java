package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.logic.EffectManager;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Cake;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Player-centric mechanics: food effects, backstabbing, rotten tomato, animal feeding, cake cutting. */
public final class PlayerListener implements Listener {

    private final FarmersDelightPlugin plugin;

    public PlayerListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    /* ===================== food effects ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        var effects = plugin.recipes().effectsFor(event.getItem());
        plugin.advancements().onMealConsumed(player, event.getItem());
        if (effects == null) return;
        plugin.effectManager().applyFromConfig(player, effects);
    }

    /* ===================== backstabbing ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();

        // skillet attack sounds + knockback
        if (CraftEngineHook.isCustomItem(weapon, FD.SKILLET_ITEM)) {
            float cooldown = player.getAttackCooldown();
            String sound = cooldown > 0.8f ? FD.SND_SKILLET_ATK_STRONG : FD.SND_SKILLET_ATK_WEAK;
            player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 0.7f, 1.0f);
            Vector knock = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (knock.lengthSquared() > 0) {
                knock.normalize().multiply(0.9).setY(0.4);
                target.setVelocity(target.getVelocity().add(knock));
            }
        }

        // backstabbing knives
        if (!FDRecipes_isKnife(weapon)) return;
        // datapack enchantments resolve through the Paper registry mirror
        var backstab = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .get(net.kyori.adventure.key.Key.key(FD.MOD_ID, "backstabbing"));
        if (backstab == null) return;
        int level = weapon.getEnchantmentLevel(backstab);
        if (level <= 0) return;
        if (isBehind(target, player)) {
            float multiplier = 1.2f + 0.2f * level;
            event.setDamage(event.getDamage() * multiplier);
            player.getWorld().playSound(player.getLocation(),
                    "minecraft:entity.player.attack.crit", SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        // knives slightly reduce knockback (mod behaviour)
        Vector knock = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (knock.lengthSquared() > 0) {
            knock.normalize().multiply(0.15).setY(0.3);
            target.setVelocity(target.getVelocity().add(knock));
        }
    }

    private boolean FDRecipes_isKnife(ItemStack stack) {
        String id = GameTicker_idOf(stack);
        return id != null && id.startsWith("farmersdelight:") && id.endsWith("_knife");
    }

    private String GameTicker_idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        Key custom = CraftEngineHook.customItemId(stack);
        return custom != null ? custom.toString() : stack.getType().key().toString();
    }

    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector look = target.getEyeLocation().getDirection().setY(0).normalize();
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() < 1.0E-4) return false;
        toAttacker.normalize();
        return look.dot(toAttacker) < -0.5;
    }

    /* ===================== rotten tomato throw ===================================== */

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball snowball)) return;
        ItemStack item = snowball.getItem();
        if (!CraftEngineHook.isCustomItem(item, FD.ROTTEN_TOMATO)) return;
        Location loc = snowball.getLocation();
        loc.getWorld().playSound(loc, FD.SND_RT_HIT, SoundCategory.NEUTRAL, 1.0f,
                ThreadLocalRandom.current().nextFloat(0.9f, 1.1f));
        loc.getWorld().spawnParticle(Particle.ITEM, loc, 12, 0.2, 0.2, 0.2, 0.1, item);
        if (snowball.getShooter() instanceof Player shooter && event.getHitEntity() != null) {
            plugin.advancements().onTomatoHitRaider(shooter, event.getHitEntity());
        }
    }

    /* ===================== animal feeding / breeding / taming ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held == null || held.getType().isAir()) return;
        String heldId = GameTicker_idOf(held);
        if (heldId == null) return;

        // dog food / horse feed
        if (heldId.equals("farmersdelight:dog_food") && entity instanceof org.bukkit.entity.Wolf wolf) {
            if (!wolf.isTamed() || wolf.isDead()) return;
            event.setCancelled(true);
            feedAnimal(player, held, wolf,
                    List.of(effect("minecraft:speed", 6000), effect("minecraft:strength", 6000),
                            effect("minecraft:resistance", 6000)));
            return;
        }
        if (heldId.equals("farmersdelight:horse_feed") && entity instanceof org.bukkit.entity.AbstractHorse horse) {
            if (!horse.isTamed()) return;
            event.setCancelled(true);
            feedAnimal(player, held, horse,
                    List.of(effect("minecraft:speed", 6000, true), effect("minecraft:jump_boost", 6000)));
            return;
        }

        // chicken breeding with FD seeds
        if (entity instanceof org.bukkit.entity.Chicken chicken
                && (heldId.equals("farmersdelight:cabbage_seeds")
                || heldId.equals("farmersdelight:tomato_seeds")
                || heldId.equals("farmersdelight:rice"))) {
            event.setCancelled(true);
            breedAnimal(player, held, chicken);
            return;
        }
        // pig breeding with cabbage/tomato
        if (entity instanceof org.bukkit.entity.Pig pig
                && (heldId.equals("farmersdelight:cabbage") || heldId.equals("farmersdelight:tomato"))) {
            event.setCancelled(true);
            breedAnimal(player, held, pig);
            return;
        }
        // parrot taming with FD seeds
        if (entity instanceof Parrot parrot && !parrot.isTamed()
                && (heldId.equals("farmersdelight:cabbage_seeds")
                || heldId.equals("farmersdelight:tomato_seeds")
                || heldId.equals("farmersdelight:rice"))) {
            event.setCancelled(true);
            consume(player, held);
            if (ThreadLocalRandom.current().nextDouble() < 1.0 / 3.0) {
                parrot.setTamed(true);
                parrot.setOwner(player);
                parrot.getWorld().spawnParticle(Particle.HEART,
                        parrot.getLocation().add(0, 0.8, 0), 6, 0.3, 0.3, 0.3, 0);
            } else {
                parrot.getWorld().spawnParticle(Particle.SMOKE,
                        parrot.getLocation().add(0, 0.8, 0), 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private record EffectSpec(String effect, int duration, boolean amplified) {
    }

    private EffectSpec effect(String name, int duration) {
        return new EffectSpec(name, duration, false);
    }

    private EffectSpec effect(String name, int duration, boolean amplified) {
        return new EffectSpec(name, duration, amplified);
    }

    private void feedAnimal(Player player, ItemStack held, LivingEntity animal, List<EffectSpec> effects) {
        animal.setHealth(animal.getAttribute(Attribute.MAX_HEALTH) != null
                ? animal.getAttribute(Attribute.MAX_HEALTH).getValue() : animal.getHealth());
        for (EffectSpec spec : effects) {
            var type = org.bukkit.potion.PotionEffectType.getByName(
                    spec.effect().replace("minecraft:", ""));
            if (type != null) {
                animal.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        type, spec.duration(), spec.amplified() ? 1 : 0, false, true, true));
            }
        }
        animal.getWorld().spawnParticle(Particle.HEART, animal.getLocation().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0);
        animal.getWorld().playSound(animal.getLocation(), "minecraft:entity.generic.eat",
                SoundCategory.NEUTRAL, 0.8f, 1.0f);
        consume(player, held);
        // return the bowl for dog food
        if (GameTicker_idOf(held).equals("farmersdelight:dog_food")) {
            player.getInventory().addItem(new ItemStack(Material.BOWL));
        }
    }

    private void breedAnimal(Player player, ItemStack held, Animals animal) {
        consume(player, held);
        animal.getWorld().spawnParticle(Particle.HEART, animal.getLocation().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0);
        try {
            animal.setLoveModeTicks(600);
        } catch (Throwable ignored) {
        }
    }

    private void consume(Player player, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        stack.setAmount(stack.getAmount() - 1);
    }

    /* ===================== knife cuts cake / pies ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        ItemStack held = event.getItem();
        if (held == null || !FDRecipes_isKnife(held)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        // mod KnifeItem#useOnBlock: a knife on a pumpkin carves it, dropping 4 seeds
        // from the carved face (click face, or the player's facing on top/bottom clicks)
        if (block.getType() == Material.PUMPKIN) {
            event.setCancelled(true);
            org.bukkit.block.BlockFace clicked = event.getBlockFace();
            org.bukkit.block.BlockFace facing =
                    clicked == org.bukkit.block.BlockFace.UP || clicked == org.bukkit.block.BlockFace.DOWN
                            ? event.getPlayer().getFacing().getOppositeFace()
                            : clicked;
            block.getWorld().playSound(block.getLocation(), "minecraft:block.pumpkin.carve",
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
            block.setBlockData(org.bukkit.Bukkit.createBlockData(
                    "minecraft:carved_pumpkin[facing=" + facing.name().toLowerCase(java.util.Locale.ROOT) + "]"));
            ItemStack seeds = new ItemStack(Material.PUMPKIN_SEEDS, 4);
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5 + facing.getModX() * 0.65, 0.1, 0.5 + facing.getModZ() * 0.65),
                    seeds);
            held.damage(1, event.getPlayer());
            return;
        }

        // mod KnivesEventListener: knife on a candle cake pops the candle, leaves a
        // bitten cake (bites=1) and drops one slice
        if (block.getType().name().endsWith("_CANDLE_CAKE")) {
            event.setCancelled(true);
            String candleMatName = block.getType().name()
                    .substring(0, block.getType().name().length() - "_CAKE".length());
            Material candle = Material.matchMaterial(candleMatName);
            if (candle != null) {
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(candle));
            }
            dropSlice(block, 1);
            block.setType(Material.CAKE);
            if (block.getBlockData() instanceof Cake plain) plain.setBites(1);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.wool.break", SoundCategory.BLOCKS, 0.8f, 0.8f);
            held.damage(1, event.getPlayer());
            return;
        }

        if (block.getType() == Material.CAKE
                && block.getBlockData() instanceof Cake cake) {
            event.setCancelled(true);
            int bites = cake.getBites();
            dropSlice(block, 7 - bites);
            if (bites >= 5) {
                block.setType(Material.AIR);
            } else {
                cake.setBites(bites + 1);
                block.setBlockData(cake);
            }
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.wool.break", SoundCategory.BLOCKS, 0.8f, 1.0f);
            held.damage(1, event.getPlayer());
            return;
        }

        // cut FD pies into slices
        var custom = CraftEngineHook.customBlockState(block);
        if (custom != null) {
            String id = custom.owner().value().id().toString();
            String slice = switch (id) {
                case "farmersdelight:apple_pie" -> "apple_pie_slice";
                case "farmersdelight:sweet_berry_cheesecake" -> "sweet_berry_cheesecake_slice";
                case "farmersdelight:chocolate_pie" -> "chocolate_pie_slice";
                default -> null;
            };
            if (slice != null) {
                event.setCancelled(true);
                Integer bites = plugin.gameTicker().getInt(custom, "bites");
                int remaining = 4 - (bites == null ? 0 : bites);
                ItemStack sliceStack = CraftEngineHook.buildItem(Key.of(FD.MOD_ID, slice));
                if (sliceStack != null && remaining > 0) {
                    sliceStack.setAmount(remaining);
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5), sliceStack);
                }
                plugin.gameTicker().cropIndex.remove(block);
                CraftEngineHook.removeBlock(block, false);
                block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                        "minecraft:block.wool.break", SoundCategory.BLOCKS, 0.8f, 1.0f);
                held.damage(1, event.getPlayer());
            }
        }
    }

    private void dropSlice(Block at, int count) {
        ItemStack slice = CraftEngineHook.buildItem(Key.of(FD.MOD_ID, "cake_slice"));
        if (slice == null || count <= 0) return;
        slice.setAmount(count);
        at.getWorld().dropItemNaturally(at.getLocation().add(0.5, 0.4, 0.5), slice);
    }

    /* ===================== rope climbing ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isFlying() || player.getGameMode() == GameMode.SPECTATOR) return;
        var to = event.getTo();
        if (to == null) return;
        // cheap pre-filter: rope furniture only occupies otherwise-air blocks
        if (!to.getBlock().getType().isAir()) return;
        var entry = plugin.furnitureTracker().at(
                to.getBlock().getLocation().add(0.5, 0, 0.5), FD.ROPE);
        if (entry == null) return;
        player.setFallDistance(0);
        Vector velocity = player.getVelocity();
        if (player.isSneaking()) {
            player.setVelocity(new Vector(velocity.getX() * 0.3, 0.0, velocity.getZ() * 0.3));
        } else if (velocity.getY() < 0.05) {
            player.setVelocity(new Vector(velocity.getX(), 0.13, velocity.getZ()));
        }
    }

    /* ===================== handheld skillet ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkilletUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!CraftEngineHook.isCustomItem(main, FD.SKILLET_ITEM)) return;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) return;
        // must be near a heat source
        if (!plugin.gameTicker().isHeated(player.getLocation())) {
            return;
        }
        ItemStack cooked = plugin.gameTicker().campfireResult(off);
        if (cooked == null) return;
        event.setCancelled(true);
        plugin.skilletHand().start(player, off, cooked);
    }
}
