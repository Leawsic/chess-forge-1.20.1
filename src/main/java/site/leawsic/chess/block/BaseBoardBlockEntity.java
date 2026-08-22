package site.leawsic.chess.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import site.leawsic.chess.Chess;
import site.leawsic.chess.config.AiDifficulty;
import site.leawsic.chess.config.AiScheduler;
import site.leawsic.chess.config.ChessGameConfig;
import site.leawsic.chess.config.GoAi;
import site.leawsic.chess.config.GomokuAi;
import site.leawsic.chess.config.GomokuConfig;
import site.leawsic.chess.config.Move;
import site.leawsic.chess.screen.handler.BaseBoardMenu;

public class BaseBoardBlockEntity extends BlockEntity implements MenuProvider {
    private final BaseBoardBlock.GameKind kind;
    private int gameMode;
    private int[][] board;
    private int currentPlayer;
    private boolean gameOver;
    private boolean editMode;
    private int winner;
    private double blackScore;
    private double whiteScore;
    private int consecutivePasses;
    private int koX = -1;
    private int koY = -1;
    private UUID hostPlayer;
    private UUID guestPlayer;
    private boolean multiplayer;
    private int hostPieceType = 1;
    private int guestPieceType = 2;
    private boolean aiEnabled;
    private int aiPlayerPieceType = 1;
    private AiDifficulty aiDifficulty = AiDifficulty.NORMAL;
    private boolean aiThinking;
    private int aiGeneration;
    private int lastMoveX = -1;
    private int lastMoveY = -1;
    private final List<Move> moveHistory = new ArrayList<>();

    public BaseBoardBlockEntity(BlockPos pos, BlockState state) {
        super(Chess.BASE_BOARD_ENTITY.get(), pos, state);
        kind = state.getBlock() instanceof BaseBoardBlock block ? block.gameKind() : BaseBoardBlock.GameKind.GOMOKU;
        gameMode = 0;
        resetBoard();
    }

    public BaseBoardBlock.GameKind gameKind() { return kind; }
    public int getGameMode() { return gameMode; }
    public ChessGameConfig getConfig() { return gameMode == 1 ? GomokuConfig.GO_CONFIG : GomokuConfig.CONFIG; }
    public ChessGameConfig getPrimaryConfig() { return GomokuConfig.CONFIG; }
    public ChessGameConfig getAltConfig() { return GomokuConfig.GO_CONFIG; }
    public int[][] getBoard() { return board; }
    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public boolean isEditMode() { return editMode; }
    public int getWinner() { return winner; }
    public double getBlackScore() { return blackScore; }
    public double getWhiteScore() { return whiteScore; }
    public int getConsecutivePasses() { return consecutivePasses; }
    public int getKoX() { return koX; }
    public int getKoY() { return koY; }
    public UUID getHostPlayer() { return hostPlayer; }
    public UUID getGuestPlayer() { return guestPlayer; }
    public boolean isMultiplayer() { return multiplayer; }
    public int getHostPieceType() { return hostPieceType; }
    public int getGuestPieceType() { return guestPieceType; }
    public boolean isAiEnabled() { return aiEnabled; }
    public int getAiPlayerPieceType() { return aiPlayerPieceType; }
    public AiDifficulty getAiDifficulty() { return aiDifficulty; }
    public boolean isAiThinking() { return aiThinking; }
    public int getLastMoveX() { return lastMoveX; }
    public int getLastMoveY() { return lastMoveY; }
    public Move getLastMove() { return lastMoveX >= 0 && lastMoveY >= 0 ? new Move(lastMoveX, lastMoveY, board[lastMoveY][lastMoveX]) : null; }
    public List<Move> getMoveHistory() { return List.copyOf(moveHistory); }
    public boolean canUse(Player player) { return player != null && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64.0; }

    public boolean isHost(UUID player) { return hostPlayer != null && hostPlayer.equals(player); }
    public boolean isInGame(UUID player) { return isHost(player) || guestPlayer != null && guestPlayer.equals(player); }
    public int getPlayerPieceType(UUID player) { return isHost(player) ? hostPieceType : guestPlayer != null && guestPlayer.equals(player) ? guestPieceType : 0; }

    public void setHost(UUID player) {
        if (hostPlayer == null) { hostPlayer = player; multiplayer = false; editMode = false; sync(); }
    }

    public void replaceHost(UUID player) {
        hostPlayer = player; guestPlayer = null; multiplayer = false; editMode = false; hostPieceType = 1; guestPieceType = 2; sync();
    }

    public boolean joinGame(UUID player) {
        if (isInGame(player)) return true;
        if (hostPlayer == null) { setHost(player); return true; }
        if (guestPlayer != null) return false;
        guestPlayer = player; multiplayer = true; aiEnabled = false; editMode = false; resetBoard(); sync(); return true;
    }

    public boolean leaveGame(UUID player) {
        if (isHost(player)) {
            if (guestPlayer != null) { hostPlayer = guestPlayer; hostPieceType = guestPieceType; guestPieceType = nextPlayer(hostPieceType); }
            else { hostPlayer = null; hostPieceType = 1; guestPieceType = 2; }
            guestPlayer = null; multiplayer = false; editMode = false;
        } else if (guestPlayer != null && guestPlayer.equals(player)) {
            guestPlayer = null; multiplayer = false; editMode = false;
        } else return false;
        sync(); return true;
    }

    public boolean setPieceTypes(int hostType, int guestType, UUID player) {
        if (!multiplayer || !isHost(player) || !isValidPiecePair(hostType, guestType)) return false;
        hostPieceType = hostType; guestPieceType = guestType; resetBoard(); sync(); return true;
    }

    public boolean setGameMode(int mode, UUID player) {
        if (mode < 0 || mode > 1 || !isHost(player) || hasAnyPieces() || gameOver || mode == gameMode) return false;
        gameMode = mode; resetBoard(); if (aiEnabled && currentPlayer != aiPlayerPieceType) scheduleAiMove(); sync(); return true;
    }

    public boolean toggleAi(UUID player) {
        if (multiplayer || hostPlayer != null && !isHost(player)) return false;
        if (aiEnabled && (hasAnyPieces() || gameOver)) { aiEnabled = false; aiPlayerPieceType = 1; cancelAiMove(); sync(); return true; }
        if (hasAnyPieces() || gameOver) return false;
        cancelAiMove();
        if (!aiEnabled) { aiEnabled = true; aiPlayerPieceType = 1; }
        else if (aiPlayerPieceType == 1) { aiPlayerPieceType = 2; resetBoard(); scheduleAiMove(); }
        else { aiEnabled = false; aiPlayerPieceType = 1; }
        editMode = false; sync(); return true;
    }

    public boolean clearBoard(UUID player) {
        if (!isInGame(player) || multiplayer && !isHost(player)) return false;
        resetBoard(); if (aiEnabled && aiPlayerPieceType == 2) scheduleAiMove(); sync(); return true;
    }

    /** 循环切换 AI 难度。对局进行中也允许调整，立即对下一步生效。 */
    public boolean cycleAiDifficulty(UUID player) {
        if (multiplayer || hostPlayer != null && !isHost(player)) return false;
        aiDifficulty = aiDifficulty.next(); sync(); return true;
    }

    public boolean toggleEditMode(UUID player) {
        if (multiplayer || !isHost(player) || gameOver) return false;
        editMode = !editMode; sync(); return true;
    }

    public void clearEditMode(UUID player) { if (isHost(player) && editMode) { editMode = false; sync(); } }
    public void setEditMode(boolean value) { if (!value) { editMode = false; sync(); } }
    public void setAiEnabled(boolean value) { if (!value && aiEnabled) { aiEnabled = false; cancelAiMove(); sync(); } }

    public boolean placePiece(int x, int y, int pieceType, UUID player) {
        ChessGameConfig config = getConfig();
        if (gameOver || x < 0 || y < 0 || x >= config.getCols() || y >= config.getRows() || board[y][x] != 0) return false;
        if (multiplayer) { if (!isInGame(player) || getPlayerPieceType(player) != currentPlayer) return false; pieceType = currentPlayer; }
        else { if (hostPlayer != null && !isHost(player) || aiEnabled && !editMode && currentPlayer != aiPlayerPieceType || !editMode && pieceType != currentPlayer) return false; }
        ChessGameConfig.PlaceResult result = config.checkPlacement(this, new Move(x, y, pieceType));
        if (!result.success()) return false;
        board[y][x] = pieceType;
        for (Move captured : result.capturedPieces()) board[captured.y()][captured.x()] = 0;
        moveHistory.add(new Move(x, y, pieceType)); lastMoveX = x; lastMoveY = y; consecutivePasses = 0; koX = result.koX(); koY = result.koY();
        BoardSounds.place(this, !result.capturedPieces().isEmpty());
        applyResult(result, !editMode);
        if (aiEnabled && !editMode && !gameOver && currentPlayer != aiPlayerPieceType) scheduleAiMove();
        sync(); return true;
    }

    public boolean passTurn(UUID player) {
        if (!getConfig().supportsPass() || gameOver || editMode || !hasAnyPieces()) return false;
        if (multiplayer) { if (!isInGame(player) || getPlayerPieceType(player) != currentPlayer) return false; }
        else if (hostPlayer != null && !isHost(player) || aiEnabled && currentPlayer != aiPlayerPieceType) return false;
        ChessGameConfig.PlaceResult result = getConfig().checkPass(this, currentPlayer);
        if (!result.success()) return false;
        moveHistory.add(new Move(-1, -1, currentPlayer)); consecutivePasses++; koX = koY = -1; applyResult(result, true);
        if (aiEnabled && !gameOver && currentPlayer != aiPlayerPieceType) scheduleAiMove();
        sync(); return true;
    }

    public boolean finishGoGame(UUID player) {
        if (gameMode != 1 || gameOver || editMode || !hasAnyPieces() || (multiplayer ? !isHost(player) : hostPlayer != null && !isHost(player))) return false;
        GomokuConfig.Score score = GomokuConfig.calculateScore(board, getConfig().getRows(), getConfig().getCols());
        blackScore = score.blackScore(); whiteScore = score.whiteScore(); winner = blackScore > whiteScore ? 1 : whiteScore > blackScore ? 2 : 0; gameOver = true; editMode = false;
        BoardSounds.gameOver(this, !aiEnabled || winner == aiPlayerPieceType);
        sync(); return true;
    }

    private void applyResult(ChessGameConfig.PlaceResult result, boolean switchTurn) {
        if (result.gameOver()) {
            gameOver = true; editMode = false; winner = result.winner(); blackScore = result.blackScore(); whiteScore = result.whiteScore();
            // 胜负音以人类玩家的视角判定：人机模式下 AI 获胜则播放失败音。
            BoardSounds.gameOver(this, !aiEnabled || winner == aiPlayerPieceType);
        }
        else if (result.switchPlayer() && switchTurn) currentPlayer = nextPlayer(currentPlayer);
    }

    private void scheduleAiMove() {
        if (aiThinking || !aiEnabled || gameOver || currentPlayer == aiPlayerPieceType || !(level instanceof ServerLevel server)) return;
        aiThinking = true; int generation = ++aiGeneration; int player = currentPlayer; int[][] snapshot = copyBoard(); int savedKoX = koX; int savedKoY = koY; AiDifficulty difficulty = aiDifficulty; sync();
        // 搜索在 AI 线程完成，只把结果切回主线程应用，避免阻塞服务器 tick。
        AiScheduler.think(() -> { Move move = gameMode == 1 ? GoAi.chooseMove(snapshot, player, savedKoX, savedKoY, difficulty) : GomokuAi.chooseMove(snapshot, player, difficulty); server.getServer().execute(() -> finishAiMove(move, player, generation)); });
    }

    private void finishAiMove(Move move, int player, int generation) {
        if (generation != aiGeneration || !aiEnabled || gameOver || currentPlayer != player) return;
        aiThinking = false;
        if (move == null) { finishAiPass(player); return; }
        ChessGameConfig.PlaceResult result = getConfig().checkPlacement(this, new Move(move.x(), move.y(), player));
        if (!result.success() || board[move.y()][move.x()] != 0) { sync(); return; }
        board[move.y()][move.x()] = player;
        for (Move captured : result.capturedPieces()) board[captured.y()][captured.x()] = 0;
        moveHistory.add(new Move(move.x(), move.y(), player)); lastMoveX = move.x(); lastMoveY = move.y(); consecutivePasses = 0; koX = result.koX(); koY = result.koY();
        BoardSounds.place(this, !result.capturedPieces().isEmpty());
        applyResult(result, true); sync();
    }

    private void finishAiPass(int player) {
        if (!getConfig().supportsPass()) { sync(); return; }
        ChessGameConfig.PlaceResult result = getConfig().checkPass(this, player);
        if (!result.success()) { sync(); return; }
        moveHistory.add(new Move(-1, -1, player)); consecutivePasses++; koX = koY = -1; applyResult(result, true); sync();
    }

    private boolean hasAnyPieces() { for (int[] row : board) for (int value : row) if (value != 0) return true; return false; }
    private boolean isValidPiecePair(int hostType, int guestType) { return hostType >= 1 && hostType <= getConfig().getPlayerCount() && guestType >= 1 && guestType <= getConfig().getPlayerCount() && hostType != guestType; }
    private int nextPlayer(int player) { return player == getConfig().getPlayerCount() ? 1 : player + 1; }
    private int[][] copyBoard() { int[][] copy = new int[board.length][]; for (int i = 0; i < board.length; i++) copy[i] = board[i].clone(); return copy; }
    private void cancelAiMove() { aiGeneration++; aiThinking = false; }
    private void resetBoard() { cancelAiMove(); board = new int[getConfig().getRows()][getConfig().getCols()]; moveHistory.clear(); currentPlayer = getConfig().getInitialPlayer(); gameOver = false; editMode = false; winner = -1; blackScore = whiteScore = 0; consecutivePasses = 0; koX = koY = -1; lastMoveX = lastMoveY = -1; }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override public Component getDisplayName() { return Component.translatable(getConfig().getTranslationKey()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new BaseBoardMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putInt("GameMode", gameMode); tag.putIntArray("Board", flatten()); tag.putInt("CurrentPlayer", currentPlayer); tag.putBoolean("GameOver", gameOver); tag.putBoolean("EditMode", editMode); tag.putInt("Winner", winner); tag.putDouble("BlackScore", blackScore); tag.putDouble("WhiteScore", whiteScore); tag.putInt("ConsecutivePasses", consecutivePasses); tag.putInt("KoX", koX); tag.putInt("KoY", koY); if (hostPlayer != null) tag.putUUID("HostPlayer", hostPlayer); if (guestPlayer != null) tag.putUUID("GuestPlayer", guestPlayer); tag.putBoolean("Multiplayer", multiplayer); tag.putInt("HostPieceType", hostPieceType); tag.putInt("GuestPieceType", guestPieceType); tag.putBoolean("AiEnabled", aiEnabled); tag.putInt("AiPlayerPieceType", aiPlayerPieceType); tag.putInt("AiDifficulty", aiDifficulty.id()); tag.putInt("LastMoveX", lastMoveX); tag.putInt("LastMoveY", lastMoveY); CompoundTag moves = new CompoundTag(); for (int i = 0; i < moveHistory.size(); i++) { Move move = moveHistory.get(i); moves.putIntArray(Integer.toString(i), new int[] { move.x(), move.y(), move.player() }); } tag.put("MoveHistory", moves); tag.putInt("MoveCount", moveHistory.size());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); gameMode = tag.contains("GameMode") ? tag.getInt("GameMode") == 1 ? 1 : 0 : kind == BaseBoardBlock.GameKind.GO ? 1 : 0; board = new int[getConfig().getRows()][getConfig().getCols()]; int[] values = tag.getIntArray("Board"); for (int i = 0; i < values.length && i < board.length * board[0].length; i++) board[i / board[0].length][i % board[0].length] = values[i]; currentPlayer = tag.contains("CurrentPlayer") ? tag.getInt("CurrentPlayer") : getConfig().getInitialPlayer(); gameOver = tag.getBoolean("GameOver"); editMode = tag.getBoolean("EditMode"); winner = tag.contains("Winner") ? tag.getInt("Winner") : -1; blackScore = tag.getDouble("BlackScore"); whiteScore = tag.getDouble("WhiteScore"); consecutivePasses = tag.getInt("ConsecutivePasses"); koX = tag.contains("KoX") ? tag.getInt("KoX") : -1; koY = tag.contains("KoY") ? tag.getInt("KoY") : -1; hostPlayer = tag.hasUUID("HostPlayer") ? tag.getUUID("HostPlayer") : null; guestPlayer = tag.hasUUID("GuestPlayer") ? tag.getUUID("GuestPlayer") : null; multiplayer = tag.getBoolean("Multiplayer"); hostPieceType = tag.contains("HostPieceType") ? tag.getInt("HostPieceType") : 1; guestPieceType = tag.contains("GuestPieceType") ? tag.getInt("GuestPieceType") : 2; aiEnabled = tag.getBoolean("AiEnabled"); aiPlayerPieceType = tag.contains("AiPlayerPieceType") ? tag.getInt("AiPlayerPieceType") : 1; aiDifficulty = AiDifficulty.byId(tag.contains("AiDifficulty") ? tag.getInt("AiDifficulty") : AiDifficulty.NORMAL.id()); lastMoveX = tag.contains("LastMoveX") ? tag.getInt("LastMoveX") : -1; lastMoveY = tag.contains("LastMoveY") ? tag.getInt("LastMoveY") : -1; moveHistory.clear(); CompoundTag moves = tag.getCompound("MoveHistory"); for (int i = 0; i < tag.getInt("MoveCount"); i++) { int[] move = moves.getIntArray(Integer.toString(i)); if (move.length == 3) moveHistory.add(new Move(move[0], move[1], move[2])); }
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    private int[] flatten() { int[] values = new int[board.length * board[0].length]; int index = 0; for (int[] row : board) for (int value : row) values[index++] = value; return values; }
}
