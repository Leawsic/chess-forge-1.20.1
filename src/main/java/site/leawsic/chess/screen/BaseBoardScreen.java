package site.leawsic.chess.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.BaseBoardBlockEntity;
import site.leawsic.chess.config.ChessGameConfig;
import site.leawsic.chess.config.GomokuConfig;
import site.leawsic.chess.network.ChessNetwork;
import site.leawsic.chess.screen.handler.BaseBoardMenu;

public class BaseBoardScreen extends AbstractContainerScreen<BaseBoardMenu> {
    private static final int MIN_BACKGROUND_WIDTH = 280;
    private static final int MIN_BACKGROUND_HEIGHT = 220;
    private static final int MAX_SCREEN_MARGIN = 16;
    private static final int SIDE_CONTENT_VERTICAL_OFFSET = 24;

    private final ChessGameConfig config;
    private int boardLeft, boardTop, scaledBoardTextureWidth, scaledBoardTextureHeight;
    private float boardScale = 1.0f;
    private Button clearButton, editModeButton, aiButton, passButton, finishGoButton;
    private Button joinButton, leaveButton, hostBlackButton, hostWhiteButton;
    private Button modeGomokuButton, modeGoButton;
    private Button[] pieceSelectButtons;
    private int selectedPieceType = 1;
    private boolean localLeftGame;
    private long lastEscapePress;
    private Component notice;
    private long noticeUntil;

    public BaseBoardScreen(BaseBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        config = menu.board() == null ? GomokuConfig.CONFIG : menu.board().getConfig();
        imageWidth = Math.max(MIN_BACKGROUND_WIDTH, config.getBoardTextureWidth() + 32);
        imageHeight = Math.max(MIN_BACKGROUND_HEIGHT, config.getBoardTextureHeight() + 80);
    }

    public void showNotice(Component value) {
        notice = value;
        noticeUntil = System.currentTimeMillis() + 5000L;
    }

    private BaseBoardBlockEntity board() { return menu.board(); }

    private ChessGameConfig activeConfig() {
        BaseBoardBlockEntity board = board();
        return board == null ? config : board.getConfig();
    }

    private void send(Object packet) { ChessNetwork.CHANNEL.sendToServer(packet); }

    private void sendSimple(java.util.function.Function<net.minecraft.core.BlockPos, Object> factory) {
        if (board() != null) send(factory.apply(board().getBlockPos()));
    }

    @Override
    protected void init() {
        super.init();
        imageWidth = Math.min(width - MAX_SCREEN_MARGIN * 2,
                Math.max(MIN_BACKGROUND_WIDTH, config.getBoardTextureWidth() + 32));
        imageHeight = Math.min(height - MAX_SCREEN_MARGIN * 2,
                Math.max(MIN_BACKGROUND_HEIGHT, config.getBoardTextureHeight() + 80));
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;

        int availableWidth = Math.max(1, imageWidth - 16);
        int availableHeight = Math.max(1, imageHeight - 72);
        boardScale = Math.min(1.0F, Math.min(
                availableWidth / (float) config.getBoardTextureWidth(),
                availableHeight / (float) config.getBoardTextureHeight()));
        scaledBoardTextureWidth = Math.round(config.getBoardTextureWidth() * boardScale);
        scaledBoardTextureHeight = Math.round(config.getBoardTextureHeight() * boardScale);
        boardLeft = leftPos + (imageWidth - scaledBoardTextureWidth) / 2;
        boardTop = topPos + Math.max(8, Math.min(30, imageHeight - scaledBoardTextureHeight - 52));

        int buttonY1 = topPos + imageHeight - 48;
        int buttonY2 = topPos + imageHeight - 24;
        clearButton = button("gui.chess.clear", leftPos + 10, buttonY1, 60, b -> sendSimple(ChessNetwork.ClearBoardPacket::new));
        editModeButton = button("gui.chess.edit_mode", leftPos + 75, buttonY1, 80, b -> sendSimple(ChessNetwork.ToggleEditModePacket::new));
        aiButton = button("gui.chess.ai", leftPos + 145, buttonY2, 70, b -> sendSimple(ChessNetwork.ToggleAiPacket::new));
        passButton = button("gui.chess.pass", leftPos + 10, buttonY2, 55, b -> sendSimple(ChessNetwork.PassTurnPacket::new));
        finishGoButton = button("gui.chess.go.finish", leftPos + 70, buttonY2, 70, b -> sendSimple(ChessNetwork.FinishGoGamePacket::new));
        joinButton = button("gui.chess.join", leftPos + imageWidth - 120, buttonY1, 40, b -> {
            localLeftGame = false;
            sendSimple(ChessNetwork.JoinGamePacket::new);
        });
        leaveButton = button("gui.chess.leave", leftPos + imageWidth - 75, buttonY1, 65, b -> {
            localLeftGame = true;
            sendSimple(ChessNetwork.LeaveGamePacket::new);
        });
        hostBlackButton = button("gui.chess.host_black", leftPos + Math.max(10, imageWidth - 205), buttonY2, 95, b -> setPieceTypes(1, 2));
        hostWhiteButton = button("gui.chess.host_white", leftPos + Math.max(110, imageWidth - 105), buttonY2, 95, b -> setPieceTypes(2, 1));
        modeGomokuButton = button("gui.chess.mode.gomoku", leftPos + imageWidth - 115, topPos + 4, 60, b -> setGameMode(0));
        modeGoButton = button("gui.chess.mode.go", leftPos + imageWidth - 50, topPos + 4, 40, b -> setGameMode(1));
        initPieceSelectButtons();
        updateButtons(board());
    }

    private Button button(String key, int x, int y, int width, java.util.function.Consumer<Button> action) {
        Button button = Button.builder(Component.translatable(key), action::accept)
                .bounds(x, y, width, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private void initPieceSelectButtons() {
        int count = config.getPlayerCount();
        pieceSelectButtons = new Button[count];
        int y = topPos + imageHeight - 24;
        for (int i = 0; i < count; i++) {
            int pieceType = i + 1;
            int x = leftPos + 10 + i * 60;
            pieceSelectButtons[i] = button("gui.chess.piece." + config.getPieceType(pieceType).name(), x, y, 55,
                    b -> {
                        selectedPieceType = pieceType;
                        updatePieceButtons(board());
                    });
            pieceSelectButtons[i].visible = false;
        }
    }

    private void updatePieceButtons(BaseBoardBlockEntity board) {
        if (pieceSelectButtons == null) return;
        boolean visible = board != null && board.isEditMode() && !board.isGameOver()
                && !board.isMultiplayer() && isLocalPlayerInGame(board);
        for (int i = 0; i < pieceSelectButtons.length; i++) {
            Button button = pieceSelectButtons[i];
            int pieceType = i + 1;
            button.visible = visible;
            button.active = visible;
            button.setMessage(Component.translatable("gui.chess.piece." + config.getPieceType(pieceType).name()
                    + (pieceType == selectedPieceType && visible ? "_selected" : "")));
        }
    }

    private void setPieceTypes(int host, int guest) {
        if (board() != null) send(new ChessNetwork.SetPieceTypesPacket(board().getBlockPos(), host, guest));
    }

    private void setGameMode(int mode) {
        if (board() != null) send(new ChessNetwork.SetGameModePacket(board().getBlockPos(), mode));
    }

    private boolean hasPieces(BaseBoardBlockEntity board) {
        if (board == null) return false;
        for (int[] row : board.getBoard()) for (int value : row) if (value != 0) return true;
        return false;
    }

    private boolean isLocalPlayerInGame(BaseBoardBlockEntity board) {
        if (minecraft == null || minecraft.player == null || board == null) return false;
        return !localLeftGame && (board.isInGame(minecraft.player.getUUID()) ||
                (!board.isMultiplayer() && board.getHostPlayer() == null));
    }

    private void updateButtons(BaseBoardBlockEntity board) {
        if (board == null || minecraft == null || minecraft.player == null) return;
        boolean host = board.isHost(minecraft.player.getUUID());
        boolean inGame = isLocalPlayerInGame(board);
        boolean multiplayer = board.isMultiplayer();
        boolean full = multiplayer && board.getHostPlayer() != null && board.getGuestPlayer() != null;
        boolean pieces = hasPieces(board);
        boolean gameOver = board.isGameOver();

        updatePieceButtons(board);
        clearButton.visible = !(full && !inGame);
        editModeButton.visible = !(full && !inGame);
        aiButton.visible = !(full && !inGame) && !multiplayer;
        passButton.visible = !(full && !inGame) && activeConfig().supportsPass() && !board.isEditMode() && !gameOver;
        finishGoButton.visible = !(full && !inGame) && board.getGameMode() == 1 && pieces && !gameOver;
        joinButton.visible = !inGame && !full;
        leaveButton.visible = multiplayer && inGame;
        hostBlackButton.visible = multiplayer && host && !pieces && !gameOver;
        hostWhiteButton.visible = hostBlackButton.visible;
        modeGomokuButton.visible = !(full && !inGame);
        modeGoButton.visible = !(full && !inGame);

        clearButton.active = inGame && (!multiplayer || host) && (pieces || gameOver);
        editModeButton.active = inGame && !multiplayer && !gameOver;
        aiButton.active = aiButton.visible && inGame && (board.isAiEnabled() || (!pieces && !gameOver));
        aiButton.setMessage(Component.translatable(!board.isAiEnabled() ? "gui.chess.ai"
                : board.getAiPlayerPieceType() == 1 ? "gui.chess.ai_black" : "gui.chess.ai_white"));
        passButton.active = inGame && !gameOver && !board.isEditMode();
        finishGoButton.active = inGame && (!multiplayer || host) && !board.isEditMode();
        joinButton.active = joinButton.visible;
        leaveButton.active = leaveButton.visible;
        hostBlackButton.active = hostBlackButton.visible && board.getHostPieceType() != 1;
        hostWhiteButton.active = hostWhiteButton.visible && board.getHostPieceType() != 2;
        boolean canSwitch = inGame && (!multiplayer || host) && !pieces && !gameOver;
        modeGomokuButton.active = canSwitch && board.getGameMode() != 0;
        modeGoButton.active = canSwitch && board.getGameMode() != 1;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        BaseBoardBlockEntity board = board();
        ChessGameConfig active = activeConfig();
        ResourceLocation boardTexture = texture(active.getBoardTopTexture());
        graphics.blit(boardTexture, boardLeft, boardTop, scaledBoardTextureWidth, scaledBoardTextureHeight,
                0, 0, active.getBoardTextureWidth(), active.getBoardTextureHeight(),
                active.getBoardTextureWidth(), active.getBoardTextureHeight());
        if (board == null) return;
        for (int row = 0; row < active.getRows(); row++) for (int col = 0; col < active.getCols(); col++) {
            int piece = board.getBoard()[row][col];
            if (piece == 0) continue;
            int size = Math.round(active.getPieceDrawSize() * boardScale);
            int x = Math.round(boardLeft + boardScale * (active.getPieceCenterU(col) - active.getPieceDrawSize() / 2.0F));
            int y = Math.round(boardTop + boardScale * (active.getPieceCenterV(row) - active.getPieceDrawSize() / 2.0F));
            graphics.blit(texture(active.getPieceTexture(piece)), x, y, size, size, 0, 0,
                    active.getPieceTextureSize(), active.getPieceTextureSize(),
                    active.getPieceTextureSize(), active.getPieceTextureSize());
        }
        if (board.getLastMoveX() >= 0) {
            int piece = board.getBoard()[board.getLastMoveY()][board.getLastMoveX()];
            if (piece != 0) {
                float dotSize = active.getPieceDrawSize() * 0.35F;
                int size = Math.round(dotSize * boardScale);
                int x = Math.round(boardLeft + boardScale * (active.getPieceCenterU(board.getLastMoveX()) - dotSize / 2.0F));
                int y = Math.round(boardTop + boardScale * (active.getPieceCenterV(board.getLastMoveY()) - dotSize / 2.0F));
                graphics.setColor(1.0F, 0.2F, 0.2F, 0.75F);
                graphics.blit(texture(active.getPieceTexture(2)), x, y, size, size, 0, 0,
                        active.getPieceTextureSize(), active.getPieceTextureSize(),
                        active.getPieceTextureSize(), active.getPieceTextureSize());
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private static ResourceLocation texture(ResourceLocation location) {
        return new ResourceLocation(location.getNamespace(), "textures/" + location.getPath() + ".png");
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BaseBoardBlockEntity board = board();
        if (board == null) return;
        updateButtons(board);
        Component status;
        if (board.isGameOver()) {
            status = board.getWinner() > 0
                    ? Component.translatable("gui.chess.piece." + board.getConfig().getPieceType(board.getWinner()).name())
                    : Component.translatable("gui.chess.draw");
            if (board.getWinner() > 0) {
                status = Component.literal(status.getString()
                        + Component.translatable("gui.chess.winner_suffix").getString());
            }
        } else if (board.isAiThinking()) {
            status = Component.translatable("gui.chess.ai_thinking");
        } else if (board.isEditMode()) {
            status = Component.translatable("gui.chess.edit_mode");
        } else if (board.isMultiplayer()) {
            String host = playerName(board.getHostPlayer());
            String guest = playerName(board.getGuestPlayer());
            String turn = board.getCurrentPlayer() == board.getHostPieceType() ? host : guest;
            status = Component.literal(host + " vs " + guest + " | "
                    + Component.translatable("gui.chess.turn").getString() + ": " + turn);
        } else {
            status = Component.translatable("gui.chess.turn_format",
                    Component.translatable("gui.chess.piece." + (board.getCurrentPlayer() == 1 ? "black" : "white")));
        }
        graphics.drawString(font, status, 10, 10, 0xFFFFFF, false);
        if (board.isGameOver()) ChessScreenUi.gameOver(graphics, font, imageWidth, imageHeight, status);
        if (board.getGameMode() == 1) {
            GomokuConfig.Score score = GomokuConfig.calculateScore(board.getBoard(), board.getConfig().getRows(), board.getConfig().getCols());
            int leftSpace = boardLeft;
            int rightSpace = width - boardLeft - scaledBoardTextureWidth;
            int centerX = (leftSpace >= rightSpace ? leftSpace / 2 : boardLeft + scaledBoardTextureWidth + rightSpace / 2) - leftPos;
            int centerY = imageHeight / 2 - SIDE_CONTENT_VERTICAL_OFFSET;
            graphics.drawCenteredString(font, Component.translatable("gui.chess.go.black_score", score.blackScore()), centerX, centerY - 7, 0xAAAAAA);
            graphics.drawCenteredString(font, Component.translatable("gui.chess.go.white_score", score.whiteScore()), centerX, centerY + 7, 0xFFFFFF);
        }
    }

    private String playerName(java.util.UUID uuid) {
        if (uuid == null || minecraft == null || minecraft.level == null) return "?";
        var player = minecraft.level.getPlayerByUUID(uuid);
        return player == null ? "?" : player.getName().getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ChessGameConfig active = activeConfig();
        float logicalX = (float) ((mouseX - boardLeft) / boardScale);
        float logicalY = (float) ((mouseY - boardTop) / boardScale);
        int boardWidth = Math.round((active.getCols() - 1) * active.getBoardCellPixelSize());
        int boardHeight = Math.round((active.getRows() - 1) * active.getBoardCellPixelSize());
        if (logicalX >= active.getBoardLeftU() - 4 && logicalX <= active.getBoardLeftU() + boardWidth + 4
                && logicalY >= active.getBoardTopV() - 4 && logicalY <= active.getBoardTopV() + boardHeight + 4) {
            int col = Math.round((logicalX - active.getBoardLeftU()) / active.getBoardCellPixelSize());
            int row = Math.round((logicalY - active.getBoardTopV()) / active.getBoardCellPixelSize());
            BaseBoardBlockEntity boardEntity = board();
            if (boardEntity != null && minecraft != null && minecraft.player != null && isLocalPlayerInGame(boardEntity)
                    && col >= 0 && col < active.getCols() && row >= 0 && row < active.getRows()) {
                int piece = boardEntity.isMultiplayer() ? boardEntity.getPlayerPieceType(minecraft.player.getUUID())
                        : boardEntity.isEditMode() ? selectedPieceType : boardEntity.getCurrentPlayer();
                if (piece != 0 && !boardEntity.isGameOver() && !boardEntity.isAiThinking()
                        && (boardEntity.isEditMode() || !boardEntity.isMultiplayer() || piece == boardEntity.getCurrentPlayer())) {
                    send(new ChessNetwork.PlacePiecePacket(boardEntity.getBlockPos(), col, row, piece));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
        long now = System.currentTimeMillis();
        if (now - lastEscapePress > 1500L) {
            lastEscapePress = now;
            showNotice(Component.translatable("gui.chess.exit_confirm"));
            return true;
        }
        BaseBoardBlockEntity board = board();
        if (board != null && minecraft != null && minecraft.player != null && board.isInGame(minecraft.player.getUUID())) {
            localLeftGame = true;
            sendSimple(ChessNetwork.LeaveGamePacket::new);
        }
        onClose();
        return true;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        drawNotice(graphics);
    }

    private void drawNotice(GuiGraphics graphics) {
        long remaining = noticeUntil - System.currentTimeMillis();
        if (notice == null || remaining <= 0) { notice = null; return; }
        int alpha = remaining < 1000L ? (int) (255L * remaining / 1000L) : 255;
        int leftSpace = boardLeft;
        int rightSpace = width - boardLeft - scaledBoardTextureWidth;
        int centerX = leftSpace >= rightSpace ? leftSpace / 2 : boardLeft + scaledBoardTextureWidth + rightSpace / 2;
        int maxWidth = Math.max(80, Math.max(leftSpace, rightSpace) - 16);
        var lines = font.split(notice, maxWidth);
        int top = (height - lines.size() * font.lineHeight) / 2 + SIDE_CONTENT_VERTICAL_OFFSET;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, centerX - font.width(line) / 2, top + i * font.lineHeight,
                    (alpha << 24) | 0xFFFFFF, true);
        }
    }
}
