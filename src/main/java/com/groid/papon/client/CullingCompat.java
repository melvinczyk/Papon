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
 */
final class CullingCompat {
    private static boolean done, broken, setsBroken;
    private static int ticks;

    private CullingCompat() {}

    @SuppressWarnings("unchecked")
    static void tick() {
        if (broken || ++ticks % 100 != 1 || !ModList.get().isLoaded("entityculling")) return;
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
}
