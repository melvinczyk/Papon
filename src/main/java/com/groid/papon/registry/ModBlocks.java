package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Chef Papon's pocket dimension (see boss/PotatoDome): the blocks the dome is built from. They
 * cannot be broken, pushed or blown up (the arena stays sealed while the fight is on), drop
 * nothing, and are taken down again when the fight ends. Textures from tools/gen_blocks.py.
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, PaponMod.MOD_ID);

    /** The dome's shell: potato skin, brown and speckled, with the odd eye. */
    public static final RegistryObject<Block> POTATO_SKIN = REGISTRY.register("potato_skin",
            () -> new Block(props(MapColor.DIRT)));
    /** The floor: the pale, starchy inside of a potato. */
    public static final RegistryObject<Block> POTATO_FLESH = REGISTRY.register("potato_flesh",
            () -> new Block(props(MapColor.SAND)));
    /** A potato's eye, sprouting and glowing: the dome's light. */
    public static final RegistryObject<Block> POTATO_EYE = REGISTRY.register("potato_eye",
            () -> new Block(props(MapColor.COLOR_PURPLE).lightLevel(s -> 15)));

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3_600_000.0F).noLootTable()
                .sound(SoundType.MUD).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false);
    }

    public static boolean isDome(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(POTATO_SKIN.get()) || s.is(POTATO_FLESH.get()) || s.is(POTATO_EYE.get());
    }

    private ModBlocks() {}
}
