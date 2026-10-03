package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.journey.TacoLine;
import com.groid.papon.journey.TacoTruckScene;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the boss fight starts. The Wandering Papon hands his Taco Coupon through the window; the
 * taquero finds it is a fake ({@link #onFraud}), and then finds himself found out: he rips off his
 * mustache, takes off his toque (Norwood 7), realizes what everyone just saw, and flies into a
 * rage (TacoVendorEntity's unmasking states). At the end of that rage {@link #startFight} swaps
 * him for {@link PaponBossEntity}, who leaps out of the serving window into the lane, past the
 * Wandering Papon, with his van parked beside the truck.
 */
public final class TacoBoss {
    private TacoBoss() {}

    /** Hook: the coupon has just been declared a fraud. The unmasking runs on the vendor from here. */
    public static void onFraud(ServerLevel level, TacoVendorEntity vendor, WanderingPaponEntity wanderer) {
        PaponMod.LOG.info("[boss] fraud at the Taco Truck {}: the unmasking begins", vendor.blockPosition());
    }

    /** The rage is over: the taquero becomes the boss and jumps out of the window. */
    public static void startFight(ServerLevel level, TacoVendorEntity vendor) {
        TacoLine line = vendor.getLine();
        CaravanEntity van = line != null ? TacoTruckScene.parkVan(level, line) : null;
        Vec3 from = vendor.position();
        Vec3 to = line != null
                ? line.slot(0).add(Vec3.atLowerCornerOf(line.out().getNormal()).scale(5.0))
                : from.add(vendor.getLookAngle().multiply(6, 0, 6));
        PaponBossEntity boss = spawn(level, from, vendor.getYRot(), van);
        if (boss == null) return;
        boss.begin(from, to, van);
        vendor.discard();
        PaponMod.LOG.info("[boss] Chef Papon is loose at {} (van {})", boss.blockPosition(), van == null ? "none" : van.blockPosition());
    }

    /** A boss right here, no truck needed (/papon boss). */
    @Nullable
    public static PaponBossEntity spawnHere(ServerLevel level, Vec3 at, float yaw, @Nullable CaravanEntity van) {
        PaponBossEntity boss = spawn(level, at, yaw, van);
        if (boss != null) boss.beginHere(van);
        return boss;
    }

    @Nullable
    private static PaponBossEntity spawn(ServerLevel level, Vec3 at, float yaw, @Nullable CaravanEntity van) {
        PaponBossEntity boss = ModEntities.PAPON_BOSS.get().create(level);
        if (boss == null) return null;
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(PaponConfig.BOSS_HEALTH.get());
        boss.setHealth(boss.getMaxHealth());
        boss.moveTo(at.x, at.y, at.z, yaw, 0);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        level.addFreshEntity(boss);
        return boss;
    }
}
