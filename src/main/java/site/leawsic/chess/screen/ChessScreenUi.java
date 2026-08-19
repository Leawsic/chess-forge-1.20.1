package site.leawsic.chess.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ChessScreenUi {
    private ChessScreenUi() {}

    static void gameOver(GuiGraphics graphics, Font font, int width, int height, Component winner) {
        graphics.fill(0, 0, width, height, 0xD2000000);
        int centerX = width / 2;
        int centerY = height / 2 - 20;
        int titleWidth = Math.max(150, font.width(winner) + 40);
        graphics.fill(centerX - titleWidth / 2, centerY - 17, centerX + titleWidth / 2, centerY + 37, 0xE8120F0A);
        graphics.renderOutline(centerX - titleWidth / 2, centerY - 17, titleWidth, 54, 0xFFFFD54F);
        graphics.drawCenteredString(font, winner, centerX, centerY, 0xFFD54F);
        graphics.drawCenteredString(font, Component.translatable("gui.chess.game_over"), centerX, centerY + 25, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.chess.clear_hint"), centerX, height - 60, 0xDDDDDD);
    }
}
