package com.groid.papon.block;

import com.groid.papon.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import com.groid.papon.menu.TrashCanMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A galvanized trash can (six iron ingots; textures and models from tools/gen_trash_can.py, the lid drawn by
 * client/TrashCanRenderer). Right-click opens its window (menu/TrashCanMenu): shift-click stacks in to feed it, and it eats
 * them for experience (TrashCanBlockEntity). Items thrown in are eaten too; hoppers can feed it, for no experience.
 */
public class TrashCanBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 3, 14, 15, 13), box(3, 0, 2, 13, 15, 14));

    public TrashCanBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof TrashCanBlockEntity can && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new TrashCanMenu(id, inv, can.slot,
                    ContainerLevelAccess.create(level, pos)), Component.translatable("block.papon.trash_can")), pos);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.3F);
        }
        return InteractionResult.CONSUME;
    }

    /** Breaking it drops what is still in its slot. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!state.is(now.getBlock()) && level.getBlockEntity(pos) instanceof TrashCanBlockEntity can) {
            Containers.dropContents(level, pos, can.slot);
        }
        super.onRemove(state, level, pos, now, moved);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 0; i < 2; i++) tooltip.add(Component.translatable("block.papon.trash_can.tip." + i).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TrashCanBlockEntity(pos, state); }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.TRASH_CAN.get()) return null;
        BlockEntityTicker<TrashCanBlockEntity> ticker = level.isClientSide ? TrashCanBlockEntity::clientTick : TrashCanBlockEntity::serverTick;
        return (BlockEntityTicker<T>) ticker;
    }
}
