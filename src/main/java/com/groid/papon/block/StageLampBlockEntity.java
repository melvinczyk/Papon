package com.groid.papon.block;

import com.groid.papon.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Nothing to store: it is there so client/StageLampRenderer can draw the beam of a lit floodlight. */
public class StageLampBlockEntity extends BlockEntity {
    public StageLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAGE_LAMP.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(10);
    }
}
