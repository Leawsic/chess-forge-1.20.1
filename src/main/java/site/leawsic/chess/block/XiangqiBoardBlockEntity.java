package site.leawsic.chess.block;

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
import site.leawsic.chess.config.AiScheduler;
import site.leawsic.chess.config.XiangqiAi;
import site.leawsic.chess.config.XiangqiConfig;
import site.leawsic.chess.screen.handler.XiangqiMenu;

public class XiangqiBoardBlockEntity extends BlockEntity implements MenuProvider {
    private int[][] board;
    private int currentPlayer;
    private boolean gameOver;
    private int winner;
    private UUID hostPlayer;
    private UUID guestPlayer;
    private boolean multiplayer;
    private int hostPieceType = XiangqiConfig.RED;
    private int guestPieceType = XiangqiConfig.BLACK;
    private boolean aiEnabled;
    private int aiPlayerPieceType = XiangqiConfig.RED;
    private boolean aiThinking;
    private int aiGeneration;
    private boolean hasMoved;
    private int lastFromX = -1;
    private int lastFromY = -1;
    private int lastToX = -1;
    private int lastToY = -1;

    public XiangqiBoardBlockEntity(BlockPos pos, BlockState state) { super(Chess.XIANGQI_ENTITY.get(), pos, state); resetBoard(XiangqiConfig.RED); }
    public int[][] getBoard() { return board; }
    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public UUID getHostPlayer() { return hostPlayer; }
    public UUID getGuestPlayer() { return guestPlayer; }
    public boolean isMultiplayer() { return multiplayer; }
    public boolean isGameStarted() { return guestPlayer != null; }
    public int getHostPieceType() { return hostPieceType; }
    public int getGuestPieceType() { return guestPieceType; }
    public boolean isAiEnabled() { return aiEnabled; }
    public int getAiPlayerPieceType() { return aiPlayerPieceType; }
    public boolean isAiThinking() { return aiThinking; }
    public int getLastFromX() { return lastFromX; }
    public int getLastFromY() { return lastFromY; }
    public int getLastToX() { return lastToX; }
    public int getLastToY() { return lastToY; }
    public XiangqiAi.Move getLastMove() { return lastFromX >= 0 ? new XiangqiAi.Move(lastFromX, lastFromY, lastToX, lastToY) : null; }
    public boolean isEditMode() { return false; }
    public boolean canUse(Player player) { return player != null && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64.0; }
    public boolean isHost(UUID player) { return hostPlayer != null && hostPlayer.equals(player); }
    public boolean isInGame(UUID player) { return isHost(player) || guestPlayer != null && guestPlayer.equals(player); }
    public int getPlayerPieceType(UUID player) { return isHost(player) ? hostPieceType : guestPlayer != null && guestPlayer.equals(player) ? guestPieceType : 0; }

    public void setHost(UUID player) { if (hostPlayer == null) { clearSession(); hostPlayer = player; sync(); } }
    public void replaceHost(UUID player) { hostPlayer = player; guestPlayer = null; multiplayer = false; hostPieceType = XiangqiConfig.RED; guestPieceType = XiangqiConfig.BLACK; aiEnabled = false; aiPlayerPieceType = XiangqiConfig.RED; resetBoard(hostPieceType); sync(); }
    public boolean joinGame(UUID player) {
        if (isInGame(player)) return true;
        if (hostPlayer == null) { setHost(player); return true; }
        if (guestPlayer != null) return false;
        guestPlayer = player; multiplayer = true; cancelAiMove(); aiEnabled = false; resetBoard(XiangqiConfig.RED); sync(); return true;
    }
    public boolean leaveGame(UUID player) {
        if (isHost(player)) {
            if (guestPlayer != null) { hostPlayer = guestPlayer; hostPieceType = guestPieceType; guestPieceType = -hostPieceType; guestPlayer = null; multiplayer = false; }
            else clearSession();
        } else if (guestPlayer != null && guestPlayer.equals(player)) { guestPlayer = null; multiplayer = false; }
        else return false;
        sync(); return true;
    }
    public boolean setPieceTypes(int hostType, int guestType, UUID player) {
        if (!isHost(player) || isGameStarted() || hostType == guestType || !isColor(hostType) || !isColor(guestType)) return false;
        hostPieceType = hostType; guestPieceType = guestType; resetBoard(isGameStarted() ? XiangqiConfig.RED : hostPieceType); sync(); return true;
    }
    public boolean toggleAi(UUID player) {
        if (multiplayer || gameOver || hasMoved || hostPlayer != null && !isHost(player)) return false;
        cancelAiMove();
        if (!aiEnabled) { aiEnabled = true; aiPlayerPieceType = XiangqiConfig.RED; }
        else if (aiPlayerPieceType == XiangqiConfig.RED) aiPlayerPieceType = XiangqiConfig.BLACK;
        else { aiEnabled = false; aiPlayerPieceType = XiangqiConfig.RED; }
        hostPieceType = aiPlayerPieceType; guestPieceType = -aiPlayerPieceType; resetBoard(XiangqiConfig.RED); if (aiEnabled && currentPlayer != aiPlayerPieceType) scheduleAiMove(); sync(); return true;
    }
    public boolean resetBoard(UUID player) {
        if (!isHost(player)) return false;
        resetBoard(aiEnabled || isGameStarted() ? XiangqiConfig.RED : hostPieceType); if (aiEnabled && currentPlayer != aiPlayerPieceType) scheduleAiMove(); sync(); return true;
    }

    public String tryMove(int fromX, int fromY, int toX, int toY, UUID player) {
        if (gameOver) return "gui.chess.xq.game_over";
        if (multiplayer) { if (!isInGame(player)) return "gui.chess.xq.not_player"; if (getPlayerPieceType(player) != currentPlayer) return "gui.chess.xq.not_your_turn"; }
        else if (!isHost(player)) return "gui.chess.xq.not_host";
        if (aiEnabled && currentPlayer != aiPlayerPieceType) return "gui.chess.xq.not_your_turn";
        if (!XiangqiConfig.inBounds(fromX, fromY) || !XiangqiConfig.inBounds(toX, toY)) return "gui.chess.xq.invalid_position";
        int piece = board[fromY][fromX];
        if (piece == 0) return "gui.chess.xq.empty_position";
        if (XiangqiConfig.color(piece) != currentPlayer) return "gui.chess.xq.not_your_turn";
        if (XiangqiConfig.color(board[toY][toX]) == currentPlayer) return "gui.chess.xq.own_piece";
        if (!XiangqiConfig.isLegalMove(board, fromX, fromY, toX, toY)) return XiangqiConfig.moveRuleKey(piece);
        int captured = board[toY][toX]; board[toY][toX] = piece; board[fromY][fromX] = 0;
        if (XiangqiConfig.isInCheck(board, currentPlayer)) { board[fromY][fromX] = piece; board[toY][toX] = captured; return "gui.chess.xq.self_check"; }
        recordLastMove(fromX, fromY, toX, toY); hasMoved = true; finishMove(captured); if (aiEnabled && !gameOver && currentPlayer != aiPlayerPieceType) scheduleAiMove(); sync(); return null;
    }

    private void scheduleAiMove() {
        if (aiThinking || !aiEnabled || gameOver || currentPlayer == aiPlayerPieceType || !(level instanceof ServerLevel server)) return;
        aiThinking = true; int generation = ++aiGeneration; int side = currentPlayer; int[][] snapshot = copyBoard(); sync();
        AiScheduler.think(() -> server.getServer().execute(() -> finishAiMove(XiangqiAi.chooseMove(snapshot, side), side, generation)));
    }
    private void finishAiMove(XiangqiAi.Move move, int side, int generation) {
        if (generation != aiGeneration || !aiEnabled || gameOver || currentPlayer != side) return;
        aiThinking = false;
        if (move == null) { gameOver = true; winner = -currentPlayer; sync(); return; }
        int captured = board[move.toY()][move.toX()]; board[move.toY()][move.toX()] = board[move.fromY()][move.fromX()]; board[move.fromY()][move.fromX()] = 0; recordLastMove(move.fromX(), move.fromY(), move.toX(), move.toY()); finishMove(captured); sync();
    }
    private void finishMove(int captured) { if (Math.abs(captured) == XiangqiConfig.GENERAL) { gameOver = true; winner = currentPlayer; return; } currentPlayer = -currentPlayer; if (!XiangqiConfig.hasLegalResponse(board, currentPlayer)) { gameOver = true; winner = -currentPlayer; } }
    private void resetBoard(int firstPlayer) { cancelAiMove(); board = XiangqiConfig.createInitialBoard(); currentPlayer = firstPlayer; gameOver = false; winner = 0; hasMoved = false; lastFromX = lastFromY = lastToX = lastToY = -1; }
    private void clearSession() { cancelAiMove(); hostPlayer = null; guestPlayer = null; multiplayer = false; aiEnabled = false; hostPieceType = XiangqiConfig.RED; guestPieceType = XiangqiConfig.BLACK; aiPlayerPieceType = XiangqiConfig.RED; }
    private void recordLastMove(int fromX, int fromY, int toX, int toY) { lastFromX = fromX; lastFromY = fromY; lastToX = toX; lastToY = toY; }
    private static boolean isColor(int color) { return color == XiangqiConfig.RED || color == XiangqiConfig.BLACK; }
    private int[][] copyBoard() { int[][] copy = new int[board.length][]; for (int i = 0; i < board.length; i++) copy[i] = board[i].clone(); return copy; }
    private void cancelAiMove() { aiGeneration++; aiThinking = false; }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override public Component getDisplayName() { return Component.translatable("block.chess.xq_board"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new XiangqiMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putIntArray("Board", flatten()); tag.putInt("CurrentPlayer", currentPlayer); tag.putBoolean("GameOver", gameOver); tag.putInt("Winner", winner); if (hostPlayer != null) tag.putUUID("HostPlayer", hostPlayer); if (guestPlayer != null) tag.putUUID("GuestPlayer", guestPlayer); tag.putBoolean("Multiplayer", multiplayer); tag.putInt("HostPieceType", hostPieceType); tag.putInt("GuestPieceType", guestPieceType); tag.putBoolean("AiEnabled", aiEnabled); tag.putInt("AiPlayerPieceType", aiPlayerPieceType); tag.putBoolean("HasMoved", hasMoved); tag.putInt("LastFromX", lastFromX); tag.putInt("LastFromY", lastFromY); tag.putInt("LastToX", lastToX); tag.putInt("LastToY", lastToY); }
    @Override public void load(CompoundTag tag) { super.load(tag); board = new int[XiangqiConfig.ROWS][XiangqiConfig.COLS]; int[] values = tag.getIntArray("Board"); for (int i = 0; i < values.length && i < XiangqiConfig.ROWS * XiangqiConfig.COLS; i++) board[i / XiangqiConfig.COLS][i % XiangqiConfig.COLS] = values[i]; currentPlayer = tag.contains("CurrentPlayer") ? tag.getInt("CurrentPlayer") : XiangqiConfig.RED; gameOver = tag.getBoolean("GameOver"); winner = tag.getInt("Winner"); hostPlayer = tag.hasUUID("HostPlayer") ? tag.getUUID("HostPlayer") : null; guestPlayer = tag.hasUUID("GuestPlayer") ? tag.getUUID("GuestPlayer") : null; multiplayer = tag.getBoolean("Multiplayer"); hostPieceType = tag.contains("HostPieceType") ? tag.getInt("HostPieceType") : XiangqiConfig.RED; guestPieceType = tag.contains("GuestPieceType") ? tag.getInt("GuestPieceType") : XiangqiConfig.BLACK; aiEnabled = tag.getBoolean("AiEnabled"); aiPlayerPieceType = tag.contains("AiPlayerPieceType") ? tag.getInt("AiPlayerPieceType") : XiangqiConfig.RED; hasMoved = tag.getBoolean("HasMoved"); lastFromX = tag.contains("LastFromX") ? tag.getInt("LastFromX") : -1; lastFromY = tag.contains("LastFromY") ? tag.getInt("LastFromY") : -1; lastToX = tag.contains("LastToX") ? tag.getInt("LastToX") : -1; lastToY = tag.contains("LastToY") ? tag.getInt("LastToY") : -1; }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    private int[] flatten() { int[] values = new int[XiangqiConfig.ROWS * XiangqiConfig.COLS]; int index = 0; for (int[] row : board) for (int value : row) values[index++] = value; return values; }
}
