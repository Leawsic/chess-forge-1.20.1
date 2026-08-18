package site.leawsic.chess.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class XiangqiBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public XiangqiBoardBlock() { super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).noOcclusion()); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); return BoardMultiblock.canAssemble(context.getLevel(), context.getClickedPos(), state.getValue(FACING)) ? state : null; }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) { super.onPlace(state, level, pos, oldState, moving); if (!level.isClientSide && oldState.getBlock() != this) BoardMultiblock.assemble(level, pos, state.getValue(FACING)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new XiangqiBoardBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof XiangqiBoardBlockEntity board)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) { if (board.getHostPlayer() == null) board.setHost(player.getUUID()); NetworkHooks.openScreen(serverPlayer, board, pos); }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next) { if (state.getBlock() != next.getBlock()) BoardMultiblock.remove(level, pos, state.getValue(FACING)); super.onRemove(state, level, pos, next); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return Block.box(-16, 0, -16, 32, 1, 32); }
    @Override public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return Block.box(-16, 0, -16, 32, 1, 32); }
}
