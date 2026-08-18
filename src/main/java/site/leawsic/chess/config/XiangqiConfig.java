package site.leawsic.chess.config;

import net.minecraft.resources.ResourceLocation;
import site.leawsic.chess.Chess;

public final class XiangqiConfig {
    private XiangqiConfig() {}
    public static final int COLS = 9, ROWS = 10, RED = 1, BLACK = -1, GENERAL = 1, ADVISOR = 2, ELEPHANT = 3, HORSE = 4, ROOK = 5, CANNON = 6, SOLDIER = 7, BOARD_TEXTURE_SIZE = 512, BOARD_LEFT_U = 56, BOARD_TOP_V = 31, CELL_PIXELS = 50, PIECE_PIXELS = 40;
    public static final ResourceLocation BOARD_TEXTURE = Chess.id("block/xq_board_top");
    public static int[][] createInitialBoard() { int[][] b = new int[ROWS][COLS]; int[] back = {ROOK, HORSE, ELEPHANT, ADVISOR, GENERAL, ADVISOR, ELEPHANT, HORSE, ROOK}; for (int x = 0; x < COLS; x++) { b[0][x] = -back[x]; b[ROWS - 1][x] = back[x]; } b[2][1] = b[2][7] = -CANNON; b[7][1] = b[7][7] = CANNON; for (int x = 0; x < COLS; x += 2) { b[3][x] = -SOLDIER; b[6][x] = SOLDIER; } return b; }
    public static int textureX(int c) { return BOARD_LEFT_U + c * CELL_PIXELS; }
    public static int textureY(int r) { return BOARD_TOP_V + r * CELL_PIXELS; }
    public static ResourceLocation pieceTexture(int p) { return Chess.id("xq_pieces/" + textureName(p)); }
    public static String textureName(int p) { return (p > 0 ? "hong_" : "hei_") + switch (Math.abs(p)) { case GENERAL -> "jiang"; case ADVISOR -> "shi"; case ELEPHANT -> "xiang"; case HORSE -> "ma"; case ROOK -> "ju"; case CANNON -> "pao"; default -> "zu"; }; }
    public static boolean isLegalMove(int[][] b, int fx, int fy, int tx, int ty) { if (fx == tx && fy == ty) return false; int p = b[fy][fx], type = Math.abs(p), side = color(p), dx = tx - fx, dy = ty - fy, adx = Math.abs(dx), ady = Math.abs(dy); return switch (type) { case GENERAL -> (adx + ady == 1 && inPalace(tx, ty, side)) || (dx == 0 && Math.abs(b[ty][tx]) == GENERAL && clearPath(b, fx, fy, tx, ty, 0)); case ADVISOR -> adx == 1 && ady == 1 && inPalace(tx, ty, side); case ELEPHANT -> adx == 2 && ady == 2 && !crossedRiver(ty, side) && b[fy + dy / 2][fx + dx / 2] == 0; case HORSE -> adx == 2 && ady == 1 && b[fy][fx + dx / 2] == 0 || adx == 1 && ady == 2 && b[fy + dy / 2][fx] == 0; case ROOK -> (dx == 0 || dy == 0) && clearPath(b, fx, fy, tx, ty, 0); case CANNON -> (dx == 0 || dy == 0) && clearPath(b, fx, fy, tx, ty, b[ty][tx] == 0 ? 0 : 1); case SOLDIER -> dy == (side == RED ? -1 : 1) && dx == 0 || crossedRiver(fy, side) && dy == 0 && adx == 1; default -> false; }; }
    public static String moveRuleKey(int p) { return switch (Math.abs(p)) { case GENERAL -> "gui.chess.xq.rule.general"; case ADVISOR -> "gui.chess.xq.rule.advisor"; case ELEPHANT -> "gui.chess.xq.rule.elephant"; case HORSE -> "gui.chess.xq.rule.horse"; case ROOK -> "gui.chess.xq.rule.rook"; case CANNON -> "gui.chess.xq.rule.cannon"; case SOLDIER -> "gui.chess.xq.rule.soldier"; default -> "gui.chess.xq.invalid_move"; }; }
    public static boolean isInCheck(int[][] b, int side) { int gx = -1, gy = -1; for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) if (b[y][x] == side * GENERAL) { gx = x; gy = y; break; } if (gx < 0) return true; for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) if (color(b[y][x]) == -side && isLegalMove(b, x, y, gx, gy)) return true; return false; }
    public static boolean hasLegalResponse(int[][] b, int side) { for (int fy = 0; fy < ROWS; fy++) for (int fx = 0; fx < COLS; fx++) if (color(b[fy][fx]) == side) for (int ty = 0; ty < ROWS; ty++) for (int tx = 0; tx < COLS; tx++) if (color(b[ty][tx]) != side && isLegalMove(b, fx, fy, tx, ty)) { int moving = b[fy][fx], cap = b[ty][tx]; b[ty][tx] = moving; b[fy][fx] = 0; boolean safe = !isInCheck(b, side); b[fy][fx] = moving; b[ty][tx] = cap; if (safe) return true; } return false; }
    public static boolean inBounds(int x, int y) { return x >= 0 && x < COLS && y >= 0 && y < ROWS; }
    public static int color(int p) { return Integer.compare(p, 0); }
    private static boolean inPalace(int x, int y, int s) { return x >= 3 && x <= 5 && (s == RED ? y >= 7 && y <= 9 : y <= 2); }
    private static boolean crossedRiver(int y, int s) { return s == RED ? y <= 4 : y >= 5; }
    private static boolean clearPath(int[][] b, int fx, int fy, int tx, int ty, int expected) { int sx = Integer.compare(tx, fx), sy = Integer.compare(ty, fy), n = 0; for (int x = fx + sx, y = fy + sy; x != tx || y != ty; x += sx, y += sy) if (b[y][x] != 0) n++; return n == expected; }
}
