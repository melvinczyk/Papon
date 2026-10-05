package com.groid.papon.block;

import com.groid.papon.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A floodlight's aim (radians round from +X toward +Z; NaN: straight along its facing), set by the
 * coliseum's build so the beam (client/StageLampRenderer) falls on the singer.
 */
public class StageLampBlockEntity extends BlockEntity {
    private float aim = Float.NaN;

    public StageLampBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.STAGE_LAMP.get(), pos, state); }

    public float aim() { return aim; }

    public void setAim(float aim) {
        this.aim = aim;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Aim", aim);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        aim = tag.contains("Aim") ? tag.getFloat("Aim") : Float.NaN;
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(10); }
}
