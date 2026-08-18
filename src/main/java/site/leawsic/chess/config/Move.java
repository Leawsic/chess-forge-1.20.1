package site.leawsic.chess.config;

public record Move(int x, int y, int player, int toX, int toY) {
    public Move(int x, int y, int player) { this(x, y, player, -1, -1); }
    public Move(int fromX, int fromY, int toX, int toY) { this(fromX, fromY, 0, toX, toY); }
    public boolean isXiangqi() { return toX >= 0 && toY >= 0; }
}
