package com.groid.papon.client;

import com.groid.papon.PaponMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.function.Function;

/**
 * Entity Culling (tr7zw; in the Groid Pack) hides entities it thinks the camera cannot see, from async raycasts, and with
 * tickCulling on it stops ticking them on the client too. Through a cutscene the camera jumps from shot to shot, so the
 * boss, the band and the crowd went missing for frames after every cut and his client-side clock stopped (shots out of
 * step, frames never shown: the user's "glitched in production"). Every papon entity is put on its whitelists, by
 * reflection (no compile dependency; nothing happens without the mod).
 * <p>
 * (2026-10-05) Sodium Extras ("Embeddium++", also in the pack) has a second culler: with its "Max Distance Checks" on (the
 * pack's config: 64 blocks round, 32 up or down) EntityRenderDispatcher.shouldRender drops any entity further than that
 * from the camera, whatever its own renderer says. The giant potato over the coliseum, the serpents and El Gigante's skull
 * high over the floor, the lasers out of eyes 26 blocks up, the piñata on its rope, the flags round the far podium: all of
 * them came and went as the camera moved (the user: "attacks that become invisible, flicker, disappear"). Its whitelist is
 * a pair of fields its mixin adds to EntityType and BlockEntityType, filled in lazily from its config: ours are set to
 * "checked, whitelisted" here.
 */
final class CullingCompat {
    private static boolean done, broken, setsBroken, extrasDone;
    private static int ticks;

    private CullingCompat() {}

    @SuppressWarnings("unchecked")
    static void tick() {
        if (++ticks % 100 != 1) return;
        if (!extrasDone) distanceWhitelist();
        if (broken || !ModList.get().isLoaded("entityculling")) return;
        try {
            Class<?> base = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Object inst = base.getField("instance").get(null);
            if (inst == null) return;
            if (!done) {
                Function<Entity, Boolean> ours = e -> PaponMod.MOD_ID.equals(EntityType.getKey(e.getType()).getNamespace());
                base.getMethod("addDynamicEntityWhitelist", Function.class).invoke(inst, ours);
                done = true;
                PaponMod.LOG.info("[papon] Entity Culling: papon entities whitelisted");
            }
            // the static whitelists too (they cover tick culling), kept topped up in case it rebuilds them from its config
            if (setsBroken) return;
            try {
                for (String f : new String[]{"entityWhitelist", "tickCullWhitelists"}) {
                    Set<EntityType<?>> set = (Set<EntityType<?>>) base.getField(f).get(inst);
                    if (set == null) continue;
                    for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
                        var key = ForgeRegistries.ENTITY_TYPES.getKey(t);
                        if (key != null && PaponMod.MOD_ID.equals(key.getNamespace())) set.add(t);
                    }
                }
            } catch (Throwable e) {
                setsBroken = true;          // an unmodifiable set: the dynamic whitelist above has to do
            }
        } catch (Throwable e) {
            broken = true;
            PaponMod.LOG.warn("[papon] could not whitelist papon entities in Entity Culling: {}", e.toString());
        }
    }

    /** Sodium Extras' distance culling: every papon entity and block entity type marked as on its whitelist. */
    private static void distanceWhitelist() {
        extrasDone = true;
        int n = 0;
        try {
            for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
                var key = ForgeRegistries.ENTITY_TYPES.getKey(t);
                if (key != null && PaponMod.MOD_ID.equals(key.getNamespace())) n += allow(t);
            }
            for (net.minecraft.world.level.block.entity.BlockEntityType<?> t : ForgeRegistries.BLOCK_ENTITY_TYPES.getValues()) {
                var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(t);
                if (key != null && PaponMod.MOD_ID.equals(key.getNamespace())) n += allow(t);
            }
        } catch (Throwable e) {
            PaponMod.LOG.warn("[papon] could not whitelist papon entities in Sodium Extras' distance culling: {}", e.toString());
            return;
        }
        if (n > 0) PaponMod.LOG.info("[papon] Sodium Extras: {} papon types exempt from its entity distance culling", n);
    }

    /** Sets the culler's "checked" and "whitelisted" flags on a type (fields its mixin added; none without the mod). */
    private static int allow(Object type) throws IllegalAccessException {
        java.lang.reflect.Field checked = null, white = null;
        for (Class<?> c = type.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType() != boolean.class) continue;
                String name = f.getName();
                if (name.endsWith("$checked")) checked = f;
                else if (name.endsWith("$whitelisted")) white = f;
            }
        }
        if (checked == null || white == null) return 0;
        white.setAccessible(true);
        checked.setAccessible(true);
        white.setBoolean(type, true);
        checked.setBoolean(type, true);
        return 1;
    }
}
