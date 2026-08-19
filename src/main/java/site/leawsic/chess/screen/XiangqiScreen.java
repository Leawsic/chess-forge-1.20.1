package site.leawsic.chess.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import site.leawsic.chess.Chess;
import site.leawsic.chess.block.XiangqiBoardBlockEntity;
import site.leawsic.chess.config.XiangqiConfig;
import site.leawsic.chess.network.ChessNetwork;
import site.leawsic.chess.screen.handler.XiangqiMenu;

public class XiangqiScreen extends AbstractContainerScreen<XiangqiMenu> {
    private int boardLeft, boardTop;
    private float scale;
    private int selectedX = -1, selectedY = -1;
    private Button resetButton, joinButton, leaveButton, hostRedButton, hostBlackButton, aiButton;
    private boolean leaveSent;
    private long lastEscapePress;
    private Component notice;
    private long noticeUntil;

    public XiangqiScreen(XiangqiMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 340;
    }

    public void showNotice(Component value) {
        notice = value;
        noticeUntil = System.currentTimeMillis() + 5000L;
    }

    private XiangqiBoardBlockEntity board() { return menu.board(); }
    private void send(Object packet) { ChessNetwork.CHANNEL.sendToServer(packet); }

    private void sendSimple(java.util.function.Function<net.minecraft.core.BlockPos, Object> factory) {
        if (board() != null) send(factory.apply(board().getBlockPos()));
    }

    @Override
    protected void init() {
        super.init();
        imageWidth = Math.min(width - 32, 544);
        imageHeight = Math.min(height - 32, 580);
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        scale = Math.min((imageWidth - 16) / (float) XiangqiConfig.BOARD_TEXTURE_SIZE,
                (imageHeight - 72) / (float) XiangqiConfig.BOARD_TEXTURE_SIZE);
        boardLeft = leftPos + (imageWidth - Math.round(XiangqiConfig.BOARD_TEXTURE_SIZE * scale)) / 2;
        boardTop = topPos + 24;
        resetButton = button("gui.chess.clear", leftPos + 10, topPos + imageHeight - 24, 60, b -> sendSimple(ChessNetwork.XiangqiResetPacket::new));
        joinButton = button("gui.chess.join", leftPos + 75, topPos + imageHeight - 24, 55, b -> sendSimple(ChessNetwork.JoinGamePacket::new));
        leaveButton = button("gui.chess.leave", leftPos + 135, topPos + imageHeight - 24, 55, b -> leaveGame());
        hostRedButton = button("gui.chess.xq.host_red", leftPos + imageWidth - 190, topPos + imageHeight - 24, 85, b -> setPieceTypes(1, -1));
        hostBlackButton = button("gui.chess.xq.host_black", leftPos + imageWidth - 100, topPos + imageHeight - 24, 85, b -> setPieceTypes(-1, 1));
        aiButton = button("gui.chess.xq.ai", leftPos + 10, topPos + imageHeight - 48, 80, b -> sendSimple(ChessNetwork.ToggleAiPacket::new));
        updateButtons(board());
    }

    private Button button(String key, int x, int y, int width, java.util.function.Consumer<Button> action) {
        Button button = Button.builder(Component.translatable(key), action::accept).bounds(x, y, width, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private void setPieceTypes(int host, int guest) {
        if (board() != null) send(new ChessNetwork.SetPieceTypesPacket(board().getBlockPos(), host, guest));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        ResourceLocation texture = texture(XiangqiConfig.BOARD_TEXTURE);
        int pixels = Math.round(XiangqiConfig.BOARD_TEXTURE_SIZE * scale);
        graphics.blit(texture, boardLeft, boardTop, pixels, pixels, 0, 0,
                XiangqiConfig.BOARD_TEXTURE_SIZE, XiangqiConfig.BOARD_TEXTURE_SIZE,
                XiangqiConfig.BOARD_TEXTURE_SIZE, XiangqiConfig.BOARD_TEXTURE_SIZE);
        XiangqiBoardBlockEntity board = board();
        if (board == null) return;
        for (int row = 0; row < XiangqiConfig.ROWS; row++) for (int col = 0; col < XiangqiConfig.COLS; col++) {
            int piece = board.getBoard()[row][col];
            if (piece == 0) continue;
            int centerX = XiangqiConfig.textureX(col);
            int centerY = XiangqiConfig.textureY(row);
            ResourceLocation pieceTexture = texture(XiangqiConfig.pieceTexture(piece));
            graphics.blit(pieceTexture, Math.round(boardLeft + scale * (centerX - 20)),
                    Math.round(boardTop + scale * (centerY - 20)), Math.round(40 * scale), Math.round(40 * scale),
                    0, 0, 40, 40, 40, 40);
            if (col == selectedX && row == selectedY) {
                int left = Math.round(boardLeft + scale * (centerX - 20));
                int top = Math.round(boardTop + scale * (centerY - 20));
                graphics.renderOutline(left - 1, top - 1, Math.round(40 * scale) + 2, Math.round(40 * scale) + 2, 0xFFFFFF00);
            }
        }
        drawLastMoveMarker(graphics, board.getLastFromX(), board.getLastFromY());
        drawLastMoveMarker(graphics, board.getLastToX(), board.getLastToY());
    }

    private static ResourceLocation texture(ResourceLocation location) {
        return new ResourceLocation(location.getNamespace(), "textures/" + location.getPath() + ".png");
    }

    private void drawLastMoveMarker(GuiGraphics graphics, int col, int row) {
        if (!XiangqiConfig.inBounds(col, row)) return;
        int centerX = Math.round(boardLeft + scale * XiangqiConfig.textureX(col));
        int centerY = Math.round(boardTop + scale * XiangqiConfig.textureY(row));
        int size = Math.round((XiangqiConfig.PIECE_PIXELS + 6) * scale);
        graphics.renderOutline(centerX - size / 2 - 2, centerY - size / 2 - 2, size + 4, size + 4, 0xFF251A08);
        graphics.renderOutline(centerX - size / 2 - 1, centerY - size / 2 - 1, size + 2, size + 2, 0xFFFFD54F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float logicalX = (float) ((mouseX - boardLeft) / scale);
        float logicalY = (float) ((mouseY - boardTop) / scale);
        int col = Math.round((logicalX - XiangqiConfig.BOARD_LEFT_U) / XiangqiConfig.CELL_PIXELS);
        int row = Math.round((logicalY - XiangqiConfig.BOARD_TOP_V) / XiangqiConfig.CELL_PIXELS);
        if (!XiangqiConfig.inBounds(col, row)) return super.mouseClicked(mouseX, mouseY, button);
        XiangqiBoardBlockEntity boardEntity = board();
        if (boardEntity == null || minecraft == null || minecraft.player == null || boardEntity.isGameOver()) return true;
        int piece = boardEntity.getBoard()[row][col];
        if (selectedX < 0) {
            boolean allowed = boardEntity.isAiEnabled()
                    ? boardEntity.getAiPlayerPieceType() == XiangqiConfig.color(piece)
                    : !boardEntity.isGameStarted() || boardEntity.getPlayerPieceType(minecraft.player.getUUID()) == XiangqiConfig.color(piece);
            if (piece != 0 && XiangqiConfig.color(piece) == boardEntity.getCurrentPlayer() && allowed) {
                selectedX = col;
                selectedY = row;
            }
        } else {
            send(new ChessNetwork.XiangqiMovePacket(boardEntity.getBlockPos(), selectedX, selectedY, col, row));
            selectedX = selectedY = -1;
        }
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        XiangqiBoardBlockEntity boardEntity = board();
        if (boardEntity == null) return;
        if (boardEntity.isGameOver()) {
            ChessScreenUi.gameOver(graphics, font, imageWidth, imageHeight,
                    Component.translatable(boardEntity.getWinner() == XiangqiConfig.RED
                            ? "gui.chess.xq.red_wins" : "gui.chess.xq.black_wins"));
        } else {
            graphics.drawString(font, status(boardEntity), 10, 10, 0xFFFFFF, false);
        }
        if (!boardEntity.isGameOver() && XiangqiConfig.isInCheck(boardEntity.getBoard(), boardEntity.getCurrentPlayer())) {
            drawCheckWarning(graphics, boardEntity.getCurrentPlayer());
        }
        updateButtons(boardEntity);
    }

    private Component status(XiangqiBoardBlockEntity boardEntity) {
        if (boardEntity.isAiEnabled()) {
            return Component.translatable(boardEntity.isAiThinking() ? "gui.chess.xq.ai_turn"
                    : boardEntity.getCurrentPlayer() == boardEntity.getAiPlayerPieceType()
                    ? "gui.chess.xq.player_turn" : "gui.chess.xq.ai_turn");
        }
        if (!boardEntity.isGameStarted() || minecraft == null || minecraft.level == null) {
            return Component.translatable(boardEntity.getCurrentPlayer() == XiangqiConfig.RED
                    ? "gui.chess.xq.red_turn" : "gui.chess.xq.black_turn");
        }
        String host = playerName(boardEntity.getHostPlayer());
        String guest = playerName(boardEntity.getGuestPlayer());
        Component hostColor = Component.translatable(boardEntity.getHostPieceType() == XiangqiConfig.RED ? "gui.chess.xq.red" : "gui.chess.xq.black");
        Component guestColor = Component.translatable(boardEntity.getGuestPieceType() == XiangqiConfig.RED ? "gui.chess.xq.red" : "gui.chess.xq.black");
        String turn = boardEntity.getCurrentPlayer() == boardEntity.getHostPieceType() ? host : guest;
        return Component.translatable("gui.chess.xq.multiplayer_turn", host, hostColor, guest, guestColor, turn);
    }

    private String playerName(java.util.UUID uuid) {
        if (uuid == null || minecraft == null || minecraft.level == null) return "?";
        var player = minecraft.level.getPlayerByUUID(uuid);
        return player == null ? "?" : player.getName().getString();
    }

    private void drawCheckWarning(GuiGraphics graphics, int side) {
        Component warning = Component.translatable(side == XiangqiConfig.RED ? "gui.chess.xq.red_in_check" : "gui.chess.xq.black_in_check");
        int textWidth = font.width(warning);
        int centerX = boardLeft - leftPos + Math.round(XiangqiConfig.BOARD_TEXTURE_SIZE * scale) / 2;
        int left = centerX - textWidth / 2 - 8;
        int pulse = (int) ((Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 24.0);
        graphics.fill(left, 5, left + textWidth + 16, 22, 0xD0700000 | (pulse << 16));
        graphics.renderOutline(left, 5, textWidth + 16, 17, 0xFFFFD54F);
        graphics.drawString(font, warning, centerX - textWidth / 2, 9, 0xFFFFFF, true);
    }

    private void updateButtons(XiangqiBoardBlockEntity boardEntity) {
        if (boardEntity == null || minecraft == null || minecraft.player == null) return;
        boolean host = boardEntity.isHost(minecraft.player.getUUID());
        boolean inGame = boardEntity.isInGame(minecraft.player.getUUID());
        boolean started = boardEntity.isGameStarted();
        resetButton.active = host;
        joinButton.visible = joinButton.active = boardEntity.getHostPlayer() != null && !inGame && boardEntity.getGuestPlayer() == null;
        leaveButton.visible = leaveButton.active = inGame && started;
        hostRedButton.visible = hostBlackButton.visible = !boardEntity.isAiEnabled();
        hostRedButton.active = host && !boardEntity.isAiEnabled() && !started;
        hostBlackButton.active = host && !boardEntity.isAiEnabled() && !started;
        aiButton.visible = !started;
        aiButton.active = aiButton.visible && host && !boardEntity.isGameOver();
        aiButton.setMessage(Component.translatable(!boardEntity.isAiEnabled() ? "gui.chess.xq.ai"
                : boardEntity.getAiPlayerPieceType() == XiangqiConfig.RED ? "gui.chess.xq.ai_red" : "gui.chess.xq.ai_black"));
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
        long now = System.currentTimeMillis();
        if (now - lastEscapePress > 1500L) {
            lastEscapePress = now;
            showNotice(Component.translatable("gui.chess.exit_confirm"));
            return true;
        }
        XiangqiBoardBlockEntity boardEntity = board();
        if (boardEntity != null && minecraft != null && minecraft.player != null && boardEntity.isInGame(minecraft.player.getUUID())) leaveGame();
        onClose();
        return true;
    }

    private void leaveGame() {
        if (leaveSent) return;
        leaveSent = true;
        sendSimple(ChessNetwork.LeaveGamePacket::new);
    }

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
        int boardPixels = Math.round(XiangqiConfig.BOARD_TEXTURE_SIZE * scale);
        int leftSpace = boardLeft;
        int rightSpace = width - boardLeft - boardPixels;
        int centerX = leftSpace >= rightSpace ? leftSpace / 2 : boardLeft + boardPixels + rightSpace / 2;
        int maxWidth = Math.max(80, Math.max(leftSpace, rightSpace) - 16);
        var lines = font.split(notice, maxWidth);
        int top = (height - lines.size() * font.lineHeight) / 2;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, centerX - font.width(line) / 2, top + i * font.lineHeight,
                    (alpha << 24) | 0xFFFFFF, true);
        }
    }
}
