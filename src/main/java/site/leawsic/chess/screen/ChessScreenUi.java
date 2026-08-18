package site.leawsic.chess.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class ChessScreenUi {
    private ChessScreenUi() {}
    static void gameOver(GuiGraphics g, Font font, int width, int height, Component winner) {
        g.fill(0, 0, width, height, 0xD2000000);
        int w = Math.max(150, font.width(winner) + 40), x = width / 2 - w / 2, y = height / 2 - 47;
        g.fill(x, y, x + w, y + 54, 0xE8120F0A); g.renderOutline(x, y, w, 54, 0xFFFFD54F);
        g.drawCenteredString(font, winner, width / 2, y + 17, 0xFFFFD54F);
        g.drawCenteredString(font, Component.translatable("gui.chess.game_over"), width / 2, y + 34, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.chess.clear_hint"), width / 2, height - 30, 0xFFDDDDDD);
    }
}
