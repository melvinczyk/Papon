package com.groid.papon.boss;

import com.groid.papon.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A beam of light (phase three): Ascended Papon's eyes, his golden statues' eyes, the Aztec sun behind him. Only a
 * picture: the boss moves both ends every tick and decides what it burns (PaponBossEntity, the van in the way
 * included). Both ends are synced (not the entity's position, which lags), the width and the colour too, and
 * whether it is only aiming (a thin flickering line) or firing. Drawn by client/BeamRenderer. Never saved.
 */
public class BeamEntity extends Entity {
    private static final EntityDataAccessor<Float> FX = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FY = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FZ = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TX = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TY = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TZ = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);
    /** 0 aiming (a thin line), 1 firing. */
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);

    /** A beam out of a boss's eye: his entity id (or -1) and which eye (1 right, -1 left). The client starts it at the eye as
     *  drawn (client/BossRenderer), not where the server thinks his eye is. */
    private static final EntityDataAccessor<Integer> ANCHOR = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);

    public static final int AIM = 0, FIRE = 1;

    private int life = 400;
    /** Client: the ends as drawn, eased toward the synced ones (they move every tick; this keeps them smooth). */
    public Vec3 drawFrom = Vec3.ZERO, drawTo = Vec3.ZERO, prevFrom = Vec3.ZERO, prevTo = Vec3.ZERO;
    private boolean placed;

    public BeamEntity(EntityType<? extends BeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static BeamEntity create(ServerLevel level, Vec3 from, Vec3 to, float width, int color) {
        BeamEntity b = new BeamEntity(ModEntities.BEAM.get(), level);
        b.setPos(from.x, from.y, from.z);
        b.set(from, to);
        b.entityData.set(WIDTH, width);
        b.entityData.set(COLOR, color);
        return b;
    }

    public BeamEntity life(int ticks) {
        life = ticks;
        return this;
    }

    public void set(Vec3 from, Vec3 to) {
        entityData.set(FX, (float) from.x);
        entityData.set(FY, (float) from.y);
        entityData.set(FZ, (float) from.z);
        entityData.set(TX, (float) to.x);
        entityData.set(TY, (float) to.y);
        entityData.set(TZ, (float) to.z);
        setPos(from.x, from.y, from.z);
    }

    /** Out of {@code boss}'s eye ({@code side} 1 right, -1 left). */
    public BeamEntity eye(Entity boss, int side) {
        entityData.set(ANCHOR, boss.getId());
        entityData.set(SIDE, side);
        return this;
    }

    public int anchor() {
        return entityData.get(ANCHOR);
    }

    public int side() {
        return entityData.get(SIDE);
    }

    public void setMode(int mode) {
        entityData.set(MODE, mode);
    }

    public void setWidth(float w) {
        entityData.set(WIDTH, w);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public Vec3 from() {
        return new Vec3(entityData.get(FX), entityData.get(FY), entityData.get(FZ));
    }

    public Vec3 to() {
        return new Vec3(entityData.get(TX), entityData.get(TY), entityData.get(TZ));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FX, 0F);
        entityData.define(FY, 0F);
        entityData.define(FZ, 0F);
        entityData.define(TX, 0F);
        entityData.define(TY, 0F);
        entityData.define(TZ, 0F);
        entityData.define(WIDTH, 0.5F);
        entityData.define(COLOR, 0xFFFFFF);
        entityData.define(MODE, AIM);
        entityData.define(ANCHOR, -1);
        entityData.define(SIDE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            prevFrom = drawFrom;
            prevTo = drawTo;
            if (!placed) {
                drawFrom = from();
                drawTo = to();
                prevFrom = drawFrom;
                prevTo = drawTo;
                placed = true;
            } else {
                drawFrom = drawFrom.lerp(from(), 0.6);
                drawTo = drawTo.lerp(to(), 0.6);
            }
            return;
        }
        if (--life <= 0) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 192 * 192;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
