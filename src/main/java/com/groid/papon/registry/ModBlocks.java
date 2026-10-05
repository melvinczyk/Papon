package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.block.ArenaBarrierBlock;
import com.groid.papon.block.ArenaBrazierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Chef Papon's arena, El Coliseo Papa (see boss/Coliseum): the blocks it is built from. They
 * cannot be broken, pushed or blown up (the arena stays whole while the fight is on), drop
 * nothing, and are taken down again when the fight ends. Textures from tools/gen_blocks.py.
 * The three potato blocks are the old dome's, still used in the arena (the floor's mosaic).
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, PaponMod.MOD_ID);

    /** Potato skin, brown and speckled, with the odd eye. */
    public static final RegistryObject<Block> POTATO_SKIN = REGISTRY.register("potato_skin",
            () -> new Block(props(MapColor.DIRT)));
    /** The pale, starchy inside of a potato. */
    public static final RegistryObject<Block> POTATO_FLESH = REGISTRY.register("potato_flesh",
            () -> new Block(props(MapColor.SAND)));
    /** A potato's eye, sprouting and glowing: a light. */
    public static final RegistryObject<Block> POTATO_EYE = REGISTRY.register("potato_eye",
            () -> new Block(props(MapColor.COLOR_PURPLE).lightLevel(s -> 15)));

    // ------------------------------------------------------------------ the coliseum
    public static final RegistryObject<Block> ARENA_STONE = stone("arena_stone", MapColor.SAND);
    public static final RegistryObject<Block> ARENA_BRICKS = stone("arena_bricks", MapColor.SAND);
    public static final RegistryObject<Block> ARENA_COLUMN = REGISTRY.register("arena_column",
            () -> new RotatedPillarBlock(stoneProps(MapColor.SAND)));
    public static final RegistryObject<Block> ARENA_SAND = REGISTRY.register("arena_sand",
            () -> new Block(props(MapColor.SAND).sound(SoundType.SAND)));
    public static final RegistryObject<Block> ARENA_SEAT = stone("arena_seat", MapColor.WOOD);
    public static final RegistryObject<Block> ARENA_GOLD = REGISTRY.register("arena_gold",
            () -> new Block(props(MapColor.GOLD).sound(SoundType.METAL)));
    public static final RegistryObject<Block> ARENA_DARK = stone("arena_dark", MapColor.COLOR_BROWN);
    public static final RegistryObject<Block> ARENA_DRAPE_GREEN = cloth("arena_drape_green", MapColor.COLOR_GREEN);
    public static final RegistryObject<Block> ARENA_DRAPE_WHITE = cloth("arena_drape_white", MapColor.SNOW);
    public static final RegistryObject<Block> ARENA_DRAPE_RED = cloth("arena_drape_red", MapColor.COLOR_RED);
    public static final RegistryObject<Block> ARENA_DRAPE_EMBLEM = cloth("arena_drape_emblem", MapColor.SNOW);
    public static final RegistryObject<Block> ARENA_BRAZIER = REGISTRY.register("arena_brazier",
            () -> new ArenaBrazierBlock(props(MapColor.COLOR_ORANGE).sound(SoundType.LANTERN).lightLevel(s -> s.getValue(ArenaBrazierBlock.LIT) ? 15 : 0).noOcclusion()));
    public static final RegistryObject<Block> ARENA_BARRIER = REGISTRY.register("arena_barrier",
            () -> new ArenaBarrierBlock(props(MapColor.NONE).noOcclusion().noParticlesOnBreak()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));

    /** La Freidora (boss/Freidora): the sand cracked and glowing just before it gives way, and the oil under it. */
    public static final RegistryObject<Block> ARENA_HOT_SAND = REGISTRY.register("arena_hot_sand",
            () -> new com.groid.papon.block.HotSandBlock(props(MapColor.COLOR_ORANGE).sound(SoundType.SAND).lightLevel(s -> 9)));
    public static final RegistryObject<Block> ARENA_FRYING_OIL = REGISTRY.register("arena_frying_oil",
            () -> new com.groid.papon.block.FryingOilBlock(props(MapColor.GOLD).sound(SoundType.HONEY_BLOCK).lightLevel(s -> 11).noOcclusion().dynamicShape()));

    /** A stretch of wall with La Bandera Papa hung on it (block/ArenaFlagBlock; the cloth is drawn by client/ArenaFlagRenderer). */
    public static final RegistryObject<Block> ARENA_FLAG = REGISTRY.register("arena_flag",
            () -> new com.groid.papon.block.ArenaFlagBlock(stoneProps(MapColor.SAND)));
    /** The band stand's floodlights, switched on at nightfall by one of the coro (block/StageLampBlock). */
    public static final RegistryObject<Block> STAGE_LAMP = REGISTRY.register("stage_lamp",
            () -> new com.groid.papon.block.StageLampBlock(props(MapColor.COLOR_BLACK).sound(SoundType.LANTERN).noOcclusion()
                    .lightLevel(s -> s.getValue(com.groid.papon.block.StageLampBlock.LIT) ? 15 : 0)));

    // ------------------------------------------------------------------ El Coliseo de los Muertos (phase four, boss/Coliseum.dead)
    /** The floor: night-stone tiles, a marigold petal cut in each. */
    public static final RegistryObject<Block> MUERTOS_TILE = stone("muertos_tile", MapColor.COLOR_BLACK);
    public static final RegistryObject<Block> MUERTOS_BRICKS = stone("muertos_bricks", MapColor.COLOR_BLACK);
    public static final RegistryObject<Block> MUERTOS_STONE = stone("muertos_stone", MapColor.COLOR_GRAY);
    public static final RegistryObject<Block> MUERTOS_DARK = stone("muertos_dark", MapColor.COLOR_BLACK);
    public static final RegistryObject<Block> MUERTOS_BONE = REGISTRY.register("muertos_bone",
            () -> new Block(props(MapColor.SAND).sound(SoundType.BONE_BLOCK)));
    public static final RegistryObject<Block> BONE_COLUMN = REGISTRY.register("bone_column",
            () -> new RotatedPillarBlock(props(MapColor.SAND).sound(SoundType.BONE_BLOCK)));
    /** A sugar skull, its painted face to one side, its eyes glowing (block/SugarSkullBlock). */
    public static final RegistryObject<Block> MUERTOS_SKULL = REGISTRY.register("muertos_skull",
            () -> new com.groid.papon.block.SugarSkullBlock(props(MapColor.SNOW).sound(SoundType.BONE_BLOCK).lightLevel(s -> 7)));
    /** The blue running in the floor's cracks (block/EctoplasmBlock): a light. */
    public static final RegistryObject<Block> ECTOPLASM = REGISTRY.register("ectoplasm",
            () -> new com.groid.papon.block.EctoplasmBlock(props(MapColor.DIAMOND).sound(SoundType.HONEY_BLOCK).lightLevel(s -> 12).noOcclusion()));
    /** Cempasuchil: a mass of marigolds. */
    public static final RegistryObject<Block> MUERTOS_MARIGOLD = REGISTRY.register("muertos_marigold",
            () -> new Block(props(MapColor.COLOR_ORANGE).sound(SoundType.AZALEA_LEAVES)));
    public static final RegistryObject<Block> MUERTOS_PINK = stone("muertos_pink", MapColor.COLOR_PINK);
    public static final RegistryObject<Block> MUERTOS_PURPLE = stone("muertos_purple", MapColor.COLOR_PURPLE);
    /** A seat: a striped serape folded on the step. */
    public static final RegistryObject<Block> MUERTOS_SEAT = stone("muertos_seat", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> SPIRIT_BRAZIER = REGISTRY.register("spirit_brazier",
            () -> new com.groid.papon.block.SpiritBrazierBlock(props(MapColor.COLOR_CYAN).sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion()));
    public static final RegistryObject<Block> MUERTOS_CANDLES = REGISTRY.register("muertos_candles",
            () -> new com.groid.papon.block.MuertosCandlesBlock(props(MapColor.SNOW).sound(SoundType.CANDLE).lightLevel(s -> 14).noOcclusion()));
    /** La Grieta de las Almas: the floor torn open onto the souls (block/SoulVoidBlock). */
    public static final RegistryObject<Block> SOUL_VOID = REGISTRY.register("soul_void",
            () -> new com.groid.papon.block.SoulVoidBlock(props(MapColor.COLOR_BLACK).sound(SoundType.SOUL_SAND).lightLevel(s -> 6).noOcclusion()
                    .dynamicShape()));

    private static RegistryObject<Block> stone(String name, MapColor color) {
        return REGISTRY.register(name, () -> new Block(stoneProps(color)));
    }

    private static RegistryObject<Block> cloth(String name, MapColor color) {
        return REGISTRY.register(name, () -> new Block(props(color).sound(SoundType.WOOL)));
    }

    private static BlockBehaviour.Properties stoneProps(MapColor color) {
        return props(color).sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3_600_000.0F).noLootTable()
                .sound(SoundType.MUD).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false);
    }

    /** Any block of the arena (or of the old potato dome): the only blocks a teardown removes. */
    public static boolean isArena(BlockState s) {
        return PaponMod.MOD_ID.equals(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock()).getNamespace());
    }

    private ModBlocks() {}
}
