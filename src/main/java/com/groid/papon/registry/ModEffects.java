package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.effect.StunnedEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, PaponMod.MOD_ID);

    /** Run over (or rear-ended) by the van: down on the ground, unable to move. */
    public static final RegistryObject<MobEffect> STUNNED = REGISTRY.register("stunned", StunnedEffect::new);
    /** Blowtorched and salted in Chef Papon's grab: armor and toughness down 30%. */
    public static final RegistryObject<MobEffect> BRITTLE = REGISTRY.register("brittle", com.groid.papon.effect.BrittleEffect::new);

    /** Chef Papon's heat, in stacks: % max-health burn through armor; five stacks boil over. */
    public static final RegistryObject<MobEffect> HABANERO = REGISTRY.register("habanero", com.groid.papon.effect.HabaneroEffect::new);
    /** A dirty cleaver: no healing of any kind while it lasts. */
    public static final RegistryObject<MobEffect> FOOD_POISONING = REGISTRY.register("food_poisoning",
            com.groid.papon.effect.FoodPoisoningEffect::new);
    /** Rolled up in a giant tortilla: cannot move, jump, attack or use anything. */
    public static final RegistryObject<MobEffect> BURRITO = REGISTRY.register("burrito",
            () -> new com.groid.papon.effect.BoundEffect(0xE8C887, "5d2a7c41-8e3b-4f19-a6d0-3b9e1c7f2a54", true));
    /** Potato sprouts round the ankles: cannot move or jump. */
    public static final RegistryObject<MobEffect> ROOTED = REGISTRY.register("rooted",
            () -> new com.groid.papon.effect.BoundEffect(0x8DB04A, "c41e9b27-3f6d-4a85-9e2c-7b1d5a3f8e60", false));

    /** A potato from the stands in the face: a tenth of the screen in mash for five seconds. */
    public static final RegistryObject<MobEffect> SPLATTERED = REGISTRY.register("splattered", com.groid.papon.effect.SplatteredEffect::new);

    /** After humping the body pillow long enough: slow and nauseous for fifteen seconds, and in no mood to do it again. */
    public static final RegistryObject<MobEffect> POST_NUT_CLARITY = REGISTRY.register("post_nut_clarity",
            com.groid.papon.effect.PostNutClarityEffect::new);

    private ModEffects() {}
}
