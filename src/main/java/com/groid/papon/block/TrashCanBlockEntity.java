package com.groid.papon.block;

import com.groid.papon.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The trash can's insides (block/TrashCanBlock): one slot, shown in its window (menu/TrashCanMenu). What goes in is
 * chewed for {@link #CHEW} ticks (it can still be taken back) and eaten, or eaten at once when something new replaces
 * it; each eaten stack pays out experience orbs from the lid ({@link #xpPer}), and the face on its front beams while it
 * eats. Items players throw into its mouth are eaten too. Hoppers can feed it, but it gives nothing for theirs (no AFK
 * experience farms).
 */
public class TrashCanBlockEntity extends BlockEntity {
    public static final int EVENT_EAT = 2;
    /** Ticks an item sits in the slot before it is eaten. */
    static final int CHEW = 30;
    /** Ticks the happy face shows after a meal. */
    static final int HAPPY = 24;

    /** The slot; whatever lands in it starts being chewed. */
    public final Bin slot = new Bin(this);
    private int chew;
    /** Experience owed but not yet a whole point. */
    private float credit;

    float lid, lidO, lidVel;
    int happy;

    private final LazyOptional<IItemHandler> handler = LazyOptional.of(Chute::new);

    public TrashCanBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.TRASH_CAN.get(), pos, state); }

    /**
     * Experience for one item: a full stack of a 64-stack item is worth 8 points (a stack of 16 the same, an unstackable
     * item 1 on its own).
     */
    static float xpPer(ItemStack s) { return s.getMaxStackSize() <= 1 ? 1F : 8F / s.getMaxStackSize(); }

    /** Eats {@code stack}: gone for good, experience out of the lid when {@code paid}. */
    void eat(ItemStack stack, boolean paid) {
        if (!(level instanceof ServerLevel sl) || stack.isEmpty()) return;
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_EAT, 0);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.8F, 0.8F + level.random.nextFloat() * 0.3F);
        if (!paid) return;
        credit += stack.getCount() * xpPer(stack);
        int whole = (int) credit;
        if (whole > 0) {
            credit -= whole;
            ExperienceOrb.award(sl, Vec3.atBottomCenterOf(worldPosition).add(0, 1.1, 0), whole);
            if (level.random.nextInt(4) == 0) level.playSound(null, worldPosition, SoundEvents.PLAYER_BURP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrashCanBlockEntity can) {
        if (can.chew > 0 && --can.chew == 0) {
            ItemStack s = can.slot.getItem(0);
            can.slot.setItem(0, ItemStack.EMPTY);
            can.eat(s, true);
        }
        if (level.getGameTime() % 4 != 0) return;
        // items thrown into its mouth: a player's are paid for
        AABB mouth = new AABB(pos.getX() + 0.12, pos.getY() + 0.8, pos.getZ() + 0.12, pos.getX() + 0.88, pos.getY() + 1.6, pos.getZ() + 0.88);
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, mouth, ItemEntity::isAlive)) {
            ItemStack s = it.getItem();
            it.discard();
            can.eat(s, it.getOwner() instanceof Player);
        }
    }

    // ------------------------------------------------------------------ the lid and the face (client)

    public static void clientTick(Level level, BlockPos pos, BlockState state, TrashCanBlockEntity can) {
        if (can.happy > 0) can.happy--;
        can.lidO = can.lid;
        can.lidVel -= 0.05F;
        can.lid += can.lidVel;
        if (can.lid > 1.6F) { can.lid = 1.6F; can.lidVel = 0; }
        if (can.lid <= 0) {
            can.lid = 0;
            if (can.lidVel < -0.14F) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                        Math.min(0.6F, -can.lidVel * 1.2F), 1.35F + level.random.nextFloat() * 0.15F, false);
                can.lidVel = -can.lidVel * 0.32F;
            } else can.lidVel = 0;
        }
    }

    public float lid(float partial) { return Mth.lerp(partial, lidO, lid); }

    /** True while the face beams after a meal. */
    public boolean happy() { return happy > 0; }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_EAT) {
            if (level != null && level.isClientSide) {
                happy = HAPPY;
                lidVel = Math.max(lidVel, 0) + 0.3F + level.random.nextFloat() * 0.05F;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    // ------------------------------------------------------------------ saving, hoppers

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!slot.getItem(0).isEmpty()) tag.put("Last", slot.getItem(0).save(new CompoundTag()));
        tag.putInt("Chew", chew);
        tag.putFloat("Credit", credit);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        slot.setItem(0, tag.contains("Last") ? ItemStack.of(tag.getCompound("Last")) : ItemStack.EMPTY);
        chew = tag.getInt("Chew");
        credit = tag.getFloat("Credit");
    }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0, 1, 0).inflate(0.5); }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    /**
     * The can's one slot. {@link #feed} is how the window puts things in: whatever was waiting is eaten first. Anything
     * new in the slot starts the chew. The window's client copy has no can.
     */
    public static final class Bin extends SimpleContainer {
        @Nullable private final TrashCanBlockEntity can;

        public Bin(@Nullable TrashCanBlockEntity can) {
            super(1);
            this.can = can;
        }

        public void feed(ItemStack stack) {
            if (can != null) can.eat(getItem(0), true);
            setItem(0, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (can == null) return;
            can.setChanged();
            if (!getItem(0).isEmpty() && can.chew == 0) can.chew = CHEW;
            if (getItem(0).isEmpty()) can.chew = 0;
        }
    }

    /** What a hopper sees: one slot, always empty, that swallows anything and pays nothing. */
    private final class Chute implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!simulate && level != null && level.getGameTime() % 10 == 0) eat(stack, false);
            return ItemStack.EMPTY;
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
    }
}
