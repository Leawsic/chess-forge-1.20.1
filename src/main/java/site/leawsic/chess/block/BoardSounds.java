package site.leawsic.chess.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import site.leawsic.chess.Chess;

/**
 * 棋盘音效播放。
 *
 * <p>在服务端调用，由服务器广播给附近客户端，因此双人对局中双方都能听到对手落子。
 * 音高做小幅随机化，连续落子时不会听起来像复制粘贴。
 */
final class BoardSounds {
    private static final float RANGE_VOLUME = 0.7F;

    private BoardSounds() {}

    static void place(BlockEntity board, boolean captured) {
        play(board, captured ? Chess.PIECE_CAPTURE.get() : Chess.PIECE_PLACE.get(), RANGE_VOLUME, 0.06F);
    }

    static void check(BlockEntity board) {
        play(board, Chess.CHECK_ALERT.get(), 0.55F, 0.02F);
    }

    /** 对局结束音：{@code playerWon} 决定播放胜利还是失败音。 */
    static void gameOver(BlockEntity board, boolean playerWon) {
        play(board, playerWon ? Chess.GAME_WIN.get() : Chess.GAME_LOSE.get(), 0.8F, 0.0F);
    }

    private static void play(BlockEntity board, SoundEvent sound, float volume, float pitchJitter) {
        Level level = board.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = board.getBlockPos();
        float pitch = pitchJitter <= 0 ? 1.0F
                : 1.0F + (level.getRandom().nextFloat() * 2.0F - 1.0F) * pitchJitter;
        // player 传 null 表示服务器广播给范围内所有客户端，包括触发这次落子的玩家。
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
