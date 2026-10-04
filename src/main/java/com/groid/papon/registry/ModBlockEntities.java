package com.groid.papon.registry;

import com.groid.papon.PaponMod;
import com.groid.papon.block.ArenaFlagBlockEntity;
import com.groid.papon.block.StageLampBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The coliseum's two blocks with something to draw: the flags and the stage's floodlights. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PaponMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ArenaFlagBlockEntity>> ARENA_FLAG = REGISTRY.register("arena_flag",
            () -> BlockEntityType.Builder.of(ArenaFlagBlockEntity::new, ModBlocks.ARENA_FLAG.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<StageLampBlockEntity>> STAGE_LAMP = REGISTRY.register("stage_lamp",
            () -> BlockEntityType.Builder.of(StageLampBlockEntity::new, ModBlocks.STAGE_LAMP.get()).build(null));

    private ModBlockEntities() {}
}
