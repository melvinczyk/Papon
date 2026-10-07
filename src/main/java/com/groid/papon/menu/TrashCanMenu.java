package com.groid.papon.menu;

import com.groid.papon.registry.ModBlocks;
import com.groid.papon.registry.ModMenus;
import com.groid.papon.block.TrashCanBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The trash can's window (block/TrashCanBlock, client/TrashCanScreen): one slot over your inventory. Shift-click (or put)
 * a stack in and the can chews it for a moment, then eats it for experience (TrashCanBlockEntity); until then it can be
 * taken back.
 */
public class TrashCanMenu extends AbstractContainerMenu {
    /** Where the trash slot's item sits in the window. */
    public static final int SLOT_X = 80, SLOT_Y = 35;
    private final TrashCanBlockEntity.Bin trash;
    private final ContainerLevelAccess access;

    /** Client side: an empty stand-in, filled by the server's sync. */
    public TrashCanMenu(int id, Inventory inv) { this(id, inv, new TrashCanBlockEntity.Bin(null), ContainerLevelAccess.NULL); }

    public TrashCanMenu(int id, Inventory inv, TrashCanBlockEntity.Bin trash, ContainerLevelAccess access) {
        super(ModMenus.TRASH_CAN.get(), id);
        this.trash = trash;
        this.access = access;
        trash.startOpen(inv.player);
        addSlot(new Slot(trash, 0, SLOT_X, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index == 0) {
            // back out of the bin into the inventory
            ItemStack copy = stack.copy();
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
            slot.setChanged();
            return stack.isEmpty() ? ItemStack.EMPTY : copy;
        }
        // into the bin: whatever was waiting eaten, the whole stack in its place
        trash.feed(stack.copy());
        slot.set(ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) { return stillValid(access, player, ModBlocks.TRASH_CAN.get()); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        trash.stopOpen(player);
    }
}
