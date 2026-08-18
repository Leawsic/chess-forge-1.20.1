package site.leawsic.chess.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BoardPlaceholderBlock extends HorizontalDirectionalBlock {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty OFFSET_X = net.minecraft.world.level.block.state.properties.IntegerProperty.create("offset_x", -1, 1);
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty OFFSET_Z = net.minecraft.world.level.block.state.properties.IntegerProperty.create("offset_z", -1, 1);

    public BoardPlaceholderBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(2.5f).noLootTable().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OFFSET_X, 0).setValue(OFFSET_Z, 0));
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Z);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos origin = BoardMultiblock.origin(pos, state);
        BlockState originState = level.getBlockState(origin);
        return originState.getBlock().use(originState, level, origin, player, hand, hit);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return Shapes.empty(); }
    @Override public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return Shapes.empty(); }

    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) { super.playerWillDestroy(level, pos, state, player); return; }
        BlockPos origin = BoardMultiblock.origin(pos, state);
        if (level.getBlockState(origin).getBlock() instanceof BaseBoardBlock || level.getBlockState(origin).getBlock() instanceof XiangqiBoardBlock) {
            level.destroyBlock(origin, !player.isCreative());
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
