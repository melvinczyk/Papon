package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Damage types (data/papon/damage_type/*.json). The van is in minecraft:bypasses_shield: you
 * cannot block a minivan, you can only get out of its way.
 */
public final class ModDamage {
    public static final ResourceKey<DamageType> CARAVAN = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("caravan"));
    public static final ResourceKey<DamageType> SPATULA = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("spatula"));
    public static final ResourceKey<DamageType> BELLY_FLOP = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("belly_flop"));
    public static final ResourceKey<DamageType> TEQUILA_FIRE = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("tequila_fire"));
    public static final ResourceKey<DamageType> MOLOTOV = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("molotov"));

    /** A share of your maximum health, through armor, enchantments and shields: his mechanics, not his stats. */
    public static final ResourceKey<DamageType> TRUE_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("true_hit"));
    /** The Habanero burn and its boil-over (also through armor). */
    public static final ResourceKey<DamageType> HABANERO = ResourceKey.create(Registries.DAMAGE_TYPE, PaponMod.id("habanero"));

    private ModDamage() {}

    public static DamageSource of(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity cause) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, cause);
    }

    public static DamageSource caravan(ServerLevel level, Entity van, @Nullable Entity driver) {
        return of(level, CARAVAN, van, driver);
    }
}
