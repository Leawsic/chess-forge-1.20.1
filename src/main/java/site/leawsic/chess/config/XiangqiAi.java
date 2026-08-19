package site.leawsic.chess.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class XiangqiAi {
    private static final int NODE_WIDTH = 16, MAX_DEPTH = 4, Q_DEPTH = 2;
    private static final long TIME_BUDGET = 400L;
    private static final int MATE = 10_000_000;
    private static final int[] ROOK_R = {8, 12, 18, 24, 28, 30, 28, 24, 20, 16}, ROOK_F = {0, 8, 16, 24, 30, 30, 24, 16, 8};
    private static final int[] CANNON_R = {0, 4, 10, 18, 26, 32, 34, 28, 20, 12}, CANNON_F = {0, 4, 8, 12, 14, 14, 12, 8, 4};
    private static final int[] HORSE_R = {0, 6, 12, 18, 24, 26, 24, 18, 12, 6}, HORSE_F = {0, 10, 18, 24, 26, 26, 24, 18, 10};
    private static final int[] ELEPHANT_R = {26, 12, 18, 6, 8, 0, 0, 0, 0, 0}, ELEPHANT_F = {0, 0, 0, 8, 12, 8, 0, 0, 0};
    private static final int[] ADVISOR_R = {30, 16, 8, 0, 0, 0, 0, 0, 0, 0}, ADVISOR_F = {0, 0, 0, 14, 18, 14, 0, 0, 0};
    private static final int[] GENERAL_R = {30, 12, 4, 0, 0, 0, 0, 0, 0, 0}, GENERAL_F = {0, 0, 0, 12, 16, 12, 0, 0, 0};
    private static final int[] SOLDIER_R = {0, 0, 0, 14, 22, 40, 60, 90, 120, 150}, SOLDIER_F = {0, 0, 2, 8, 10, 8, 2, 0, 0};
    private static final int[][] HISTORY = new int[90][90];
    private XiangqiAi() {}

    public static Move chooseMove(int[][] b, int side) {
        List<ScoredMove> moves = legalMoves(b, side, 0, true);
        if (moves.isEmpty()) return null;
        if (moves.size() == 1) return moves.get(0).move;
        clearHistory();
        long deadline = System.nanoTime() + TIME_BUDGET * 1_000_000L;
        List<ScoredMove> bestByDepth = List.of(moves.get(0));
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            try {
                List<ScoredMove> result = rootSearch(b, side, depth, deadline);
                if (result == null) break;
                bestByDepth = result;
                if (result.get(0).orderScore >= MATE - 100_000) break;
            } catch (Abort ignored) { break; }
        }
        List<Move> pool = new ArrayList<>();
        int bestScore = bestByDepth.get(0).orderScore;
        int tolerance = Math.abs(bestScore) >= MATE - 100_000 ? 25 : Math.max(35, Math.abs(bestScore) / 40);
        for (ScoredMove s : bestByDepth) if (s.orderScore >= bestScore - tolerance) pool.add(s.move);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static List<ScoredMove> rootSearch(int[][] b, int side, int depth, long deadline) {
        List<ScoredMove> ordered = legalMoves(b, side, 0, true);
        List<ScoredMove> results = new ArrayList<>(ordered.size());
        int alpha = Integer.MIN_VALUE + 1, beta = Integer.MAX_VALUE - 1;
        for (ScoredMove c : ordered) {
            if (System.nanoTime() > deadline) return null;
            int cap = makeMove(b, c.move);
            int score = Math.abs(cap) == XiangqiConfig.GENERAL ? MATE + depth : -negamax(b, -side, depth - 1, -beta, -alpha, deadline);
            undoMove(b, c.move, cap);
            if (score > alpha) alpha = score;
            results.add(new ScoredMove(c.move, score));
        }
        results.sort(Comparator.comparingInt(ScoredMove::orderScore).reversed());
        return results;
    }

    private static int negamax(int[][] b, int side, int depth, int alpha, int beta, long deadline) {
        if (System.nanoTime() > deadline) throw new Abort();
        List<ScoredMove> moves = legalMoves(b, side, depth >= 2 ? 10 : NODE_WIDTH, depth >= 2);
        if (moves.isEmpty()) return -(MATE - depth);
        if (depth <= 0) return quiescence(b, side, alpha, beta, 0, deadline);
        int best = Integer.MIN_VALUE + 1;
        for (ScoredMove c : moves) {
            int cap = makeMove(b, c.move);
            int score = Math.abs(cap) == XiangqiConfig.GENERAL ? MATE + depth : -negamax(b, -side, depth - 1, -beta, -alpha, deadline);
            undoMove(b, c.move, cap);
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) {
                HISTORY[c.move.fromY() * 9 + c.move.fromX()][c.move.toY() * 9 + c.move.toX()] += depth * depth;
                break;
            }
        }
        return best;
    }

    private static int quiescence(int[][] b, int side, int alpha, int beta, int q, long deadline) {
        if (System.nanoTime() > deadline) throw new Abort();
        int stand = evaluate(b, side);
        if (stand >= beta) return stand;
        if (stand > alpha) alpha = stand;
        if (q >= Q_DEPTH) return stand;
        List<ScoredMove> caps = new ArrayList<>();
        for (ScoredMove m : legalMoves(b, side, 0, true)) if (b[m.move.toY()][m.move.toX()] != 0) caps.add(m);
        for (ScoredMove c : caps) {
            int cap = makeMove(b, c.move);
            int score = Math.abs(cap) == XiangqiConfig.GENERAL ? MATE + q : -quiescence(b, -side, -beta, -alpha, q + 1, deadline);
            undoMove(b, c.move, cap);
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }
        return alpha;
    }

    private static List<ScoredMove> legalMoves(int[][] b, int side, int limit, boolean filter) {
        List<ScoredMove> moves = new ArrayList<>();
        boolean inCheck = XiangqiConfig.isInCheck(b, side);
        int width = inCheck ? 0 : limit;
        for (int fy = 0; fy < XiangqiConfig.ROWS; fy++) for (int fx = 0; fx < XiangqiConfig.COLS; fx++) {
            int p = b[fy][fx];
            if (XiangqiConfig.color(p) != side) continue;
            for (int ty = 0; ty < XiangqiConfig.ROWS; ty++) for (int tx = 0; tx < XiangqiConfig.COLS; tx++) {
                if (XiangqiConfig.color(b[ty][tx]) == side || !XiangqiConfig.isLegalMove(b, fx, fy, tx, ty)) continue;
                Move m = new Move(fx, fy, tx, ty);
                int fi = fy * 9 + fx, ti = ty * 9 + tx;
                if (filter) {
                    int cap = makeMove(b, m);
                    boolean legal = !XiangqiConfig.isInCheck(b, side);
                    if (legal) moves.add(new ScoredMove(m, orderScore(b, p, cap, tx, ty, side) + HISTORY[fi][ti]));
                    undoMove(b, m, cap);
                } else {
                    moves.add(new ScoredMove(m, orderScore(b, p, b[ty][tx], tx, ty, side) + HISTORY[fi][ti]));
                }
            }
        }
        moves.sort(Comparator.comparingInt(ScoredMove::orderScore).reversed());
        return width > 0 && moves.size() > width ? new ArrayList<>(moves.subList(0, width)) : moves;
    }

    private static int orderScore(int[][] b, int p, int cap, int x, int y, int side) {
        if (Math.abs(cap) == XiangqiConfig.GENERAL) return MATE;
        int score = value(Math.abs(cap)) * 10 - value(Math.abs(p)) / 10;
        if (XiangqiConfig.isInCheck(b, -side)) score += 15_000;
        score += pst(p, x, y, side) * 4;
        if (inEnemyPalace(x, y, side)) score += 180;
        return score;
    }

    private static int evaluate(int[][] b, int side) {
        int score = 0;
        for (int y = 0; y < XiangqiConfig.ROWS; y++) for (int x = 0; x < XiangqiConfig.COLS; x++) {
            int p = b[y][x];
            if (p == 0) continue;
            int s = XiangqiConfig.color(p);
            int v = value(Math.abs(p)) + pst(p, x, y, s);
            score += s == side ? v : -v;
        }
        if (XiangqiConfig.isInCheck(b, -side)) score += 1200;
        if (XiangqiConfig.isInCheck(b, side)) score -= 1800;
        return score;
    }

    private static int pst(int p, int x, int y, int side) {
        int r = side == XiangqiConfig.RED ? XiangqiConfig.ROWS - 1 - y : y;
        return switch (Math.abs(p)) {
            case XiangqiConfig.ROOK -> ROOK_R[r] + ROOK_F[x];
            case XiangqiConfig.CANNON -> CANNON_R[r] + CANNON_F[x];
            case XiangqiConfig.HORSE -> HORSE_R[r] + HORSE_F[x];
            case XiangqiConfig.ELEPHANT -> ELEPHANT_R[r] + ELEPHANT_F[x];
            case XiangqiConfig.ADVISOR -> ADVISOR_R[r] + ADVISOR_F[x];
            case XiangqiConfig.GENERAL -> GENERAL_R[r] + GENERAL_F[x];
            case XiangqiConfig.SOLDIER -> SOLDIER_R[r] + SOLDIER_F[x];
            default -> 0;
        };
    }

    private static boolean inEnemyPalace(int x, int y, int side) { return x >= 3 && x <= 5 && (side == XiangqiConfig.RED ? y <= 2 : y >= 7); }
    private static int makeMove(int[][] b, Move m) { int cap = b[m.toY][m.toX]; b[m.toY][m.toX] = b[m.fromY][m.fromX]; b[m.fromY][m.fromX] = 0; return cap; }
    private static void undoMove(int[][] b, Move m, int cap) { b[m.fromY][m.fromX] = b[m.toY][m.toX]; b[m.toY][m.toX] = cap; }
    private static void clearHistory() { for (int[] row : HISTORY) java.util.Arrays.fill(row, 0); }
    private static int value(int p) {
        return switch (p) {
            case XiangqiConfig.ROOK -> 900;
            case XiangqiConfig.CANNON -> 475;
            case XiangqiConfig.HORSE -> 425;
            case XiangqiConfig.ELEPHANT, XiangqiConfig.ADVISOR -> 225;
            case XiangqiConfig.SOLDIER -> 120;
            case XiangqiConfig.GENERAL -> 100_000;
            default -> 0;
        };
    }
    public record Move(int fromX, int fromY, int toX, int toY) {}
    private record ScoredMove(Move move, int orderScore) {}
    private static final class Abort extends RuntimeException {}
}