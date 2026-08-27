package com.nhoryzon.mc.farmersdelight.papo.ce.behavior;

import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Minimal NMS bridge used by our CE block behaviors. CraftEngine's proxy modules are
 * not part of the published maven artifacts, so - like the reference implementation -
 * we reach the handful of needed methods (raw brightness, bukkit world, block
 * coordinates, CraftBukkit's block-grow event) through reflection against the
 * mojang-mapped Paper runtime.
 */
final class CeReflection {

    private static Method getRawBrightness;
    private static Method getWorld;
    private static Method getX;
    private static Method getY;
    private static Method getZ;
    private static Method growEvent;
    private static boolean resolved;

    private CeReflection() {
    }

    private static void resolve(Object level, Object pos) {
        if (resolved) return;
        resolved = true;
        try {
            getRawBrightness = level.getClass().getMethod("getRawBrightness", pos.getClass(), int.class);
        } catch (Exception ignored) {
        }
        try {
            getWorld = level.getClass().getMethod("getWorld");
        } catch (Exception ignored) {
        }
        try {
            getX = pos.getClass().getMethod("getX");
            getY = pos.getClass().getMethod("getY");
            getZ = pos.getClass().getMethod("getZ");
        } catch (Exception ignored) {
        }
        try {
            Class<?> factory = Class.forName("org.bukkit.craftbukkit.event.CraftEventFactory");
            for (Method method : factory.getMethods()) {
                if (!method.getName().equals("handleBlockGrowEvent")) continue;
                Class<?>[] params = method.getParameterTypes();
                // prefer the 4-arg variant (flags) present on 1.21.5+
                if (params.length == 4 && params[1] == pos.getClass()
                        && params[3] == int.class) {
                    growEvent = method;
                    break;
                }
            }
            if (growEvent == null) {
                for (Method method : factory.getMethods()) {
                    if (!method.getName().equals("handleBlockGrowEvent")) continue;
                    if (method.getParameterTypes().length == 3) {
                        growEvent = method;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    static int rawBrightness(Object level, Object pos) {
        resolve(level, pos);
        if (getRawBrightness == null) return 15;
        try {
            return (int) getRawBrightness.invoke(level, pos, 0);
        } catch (Exception e) {
            return 15;
        }
    }

    static World world(Object level) {
        try {
            resolve(level, level);
            return getWorld == null ? null : (World) getWorld.invoke(level);
        } catch (Exception e) {
            return null;
        }
    }

    static int x(Object pos) {
        try {
            resolve(pos, pos);
            return getX == null ? 0 : (int) getX.invoke(pos);
        } catch (Exception e) {
            return 0;
        }
    }

    static int y(Object pos) {
        try {
            resolve(pos, pos);
            return getY == null ? 0 : (int) getY.invoke(pos);
        } catch (Exception e) {
            return 0;
        }
    }

    static int z(Object pos) {
        try {
            resolve(pos, pos);
            return getZ == null ? 0 : (int) getZ.invoke(pos);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Fire CraftBukkit's handleBlockGrowEvent; returns false when unavailable or cancelled. */
    static boolean grow(Object level, Object pos, Object nmsState) {
        resolve(level, pos);
        if (growEvent == null) return false;
        try {
            Object result;
            if (growEvent.getParameterCount() == 4) {
                // Block.UPDATE_CLIENTS
                result = growEvent.invoke(null, level, pos, nmsState, 2);
            } else {
                result = growEvent.invoke(null, level, pos, nmsState);
            }
            return !(result instanceof Boolean b) || b;
        } catch (Exception e) {
            return false;
        }
    }
}
