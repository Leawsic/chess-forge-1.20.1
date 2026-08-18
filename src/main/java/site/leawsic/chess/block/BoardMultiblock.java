package site.leawsic.chess.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import site.leawsic.chess.Chess;

public final class BoardMultiblock {
    private BoardMultiblock() {}

    public static boolean canAssemble(Level level, BlockPos origin, Direction facing) {
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            BlockPos pos = offset(origin, facing, x, z);
            if (!pos.equals(origin) && !level.getBlockState(pos).canBeReplaced()) return false;
        }
        return true;
    }

    public static boolean assemble(Level level, BlockPos origin, Direction facing) {
        if (!canAssemble(level, origin, facing)) return false;
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) if (x != 0 || z != 0) {
            BlockPos pos = offset(origin, facing, x, z);
            level.setBlock(pos, Chess.PLACEHOLDER.get().defaultBlockState()
                    .setValue(BoardPlaceholderBlock.FACING, facing)
                    .setValue(BoardPlaceholderBlock.OFFSET_X, x + 1)
                    .setValue(BoardPlaceholderBlock.OFFSET_Z, z + 1), 3);
        }
        return true;
    }

    public static void remove(Level level, BlockPos origin, Direction facing) {
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) if (x != 0 || z != 0) {
            BlockPos pos = offset(origin, facing, x, z);
            if (level.getBlockState(pos).is(Chess.PLACEHOLDER.get())) level.destroyBlock(pos, false);
        }
    }

    public static BlockPos offset(BlockPos origin, Direction facing, int x, int z) {
        Direction right = facing.getClockWise();
        return origin.relative(right, x).relative(facing, z);
    }

    public static BlockPos origin(BlockPos placeholder, BlockState state) {
        return offset(placeholder, state.getValue(BoardPlaceholderBlock.FACING),
                1 - state.getValue(BoardPlaceholderBlock.OFFSET_X),
                1 - state.getValue(BoardPlaceholderBlock.OFFSET_Z));
    }
}
