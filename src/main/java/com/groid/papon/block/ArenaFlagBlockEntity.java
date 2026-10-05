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
 * Which flag hangs here and which way it faces: {@link #normal} is the direction (radians round from
 * +X toward +Z) the cloth faces, out from the wall. Set once by the coliseum's build (boss/Coliseum)
 * and sent to the client with the chunk.
 */
public class ArenaFlagBlockEntity extends BlockEntity {
    /** On the podium, facing the sand: 4.2 x 2.4, hung from a gold rod at the block's top. */
    public static final int DRAPE = 0;
    /** In an arch of the facade, facing out: 2.8 x 1.6, hung from the arch's crown. */
    public static final int ARCH = 1;
    /** Behind the band, on the podium over the stage: 7 x 4. */
    public static final int BACKDROP = 2;
    /** Added to the kind: El Coliseo de los Muertos' flag (the calavera potato on black). */
    public static final int DEAD = 16;

    private int kind;
    private float normal;

    public ArenaFlagBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ARENA_FLAG.get(), pos, state); }

    public int kind() { return kind; }

    public float normal() { return normal; }

    public void setup(int kind, float normal) {
        this.kind = kind;
        this.normal = normal;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Kind", kind);
        tag.putFloat("Normal", normal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        kind = tag.getInt("Kind");
        normal = tag.getFloat("Normal");
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(4.5); }
}
