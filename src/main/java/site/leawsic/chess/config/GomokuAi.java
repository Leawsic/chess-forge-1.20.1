package site.leawsic.chess.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class GomokuAi {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int ROOT_WIDTH = 20, NODE_WIDTH = 10, MAX_DEPTH = 5;
    private static final long TIME_BUDGET = 300L;
    private static final int FIVE = 10_000_000, OPEN_FOUR = 1_200_000, FOUR = 450_000,
            OPEN_THREE = 90_000, THREE = 15_000, OPEN_TWO = 3_200, TWO = 450, ONE = 30;
    private GomokuAi() {}

    public static Move chooseMove(int[][] b, int ai) {
        int op = other(ai);
        List<Move> wins = winningMoves(b, ai);
        if (!wins.isEmpty()) return wins.get(ThreadLocalRandom.current().nextInt(wins.size()));
        List<Move> opWins = winningMoves(b, op);
        if (opWins.size() == 1) { Move m = opWins.get(0); return new Move(m.x(), m.y(), ai); }
        List<ScoredMove> cs = candidates(b, ai, ROOT_WIDTH);
        if (cs.isEmpty()) return null;
        if (cs.size() == 1) return cs.get(0).move;
        long deadline = System.nanoTime() + TIME_BUDGET * 1_000_000L;
        List<ScoredMove> bestByDepth = List.of(cs.get(0));
        for (int depth = 2; depth <= MAX_DEPTH; depth++) {
            try {
                List<ScoredMove> result = rootSearch(b, ai, depth, deadline);
                if (result == null) break;
                bestByDepth = result;
                if (result.get(0).score >= FIVE / 2) break;
            } catch (Abort ignored) { break; }
        }
        List<Move> pool = new ArrayList<>();
        int bestScore = bestByDepth.get(0).score;
        int tolerance = bestScore >= FIVE / 2 || bestScore <= -FIVE / 2
                ? 2_000 : Math.max(4_000, Math.abs(bestScore) / 60);
        for (ScoredMove s : bestByDepth) if (s.score >= bestScore - tolerance) pool.add(s.move);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static List<ScoredMove> rootSearch(int[][] b, int ai, int depth, long deadline) {
        List<ScoredMove> ordered = candidates(b, ai, ROOT_WIDTH);
        List<ScoredMove> results = new ArrayList<>(ordered.size());
        int alpha = Integer.MIN_VALUE + 1, beta = Integer.MAX_VALUE - 1, op = other(ai);
        for (ScoredMove c : ordered) {
            if (System.nanoTime() > deadline) return null;
            int x = c.move.x(), y = c.move.y();
            b[y][x] = ai;
            int score = isWin(b, x, y, ai) ? FIVE + depth : -negamax(b, op, depth - 1, -beta, -alpha, deadline);
            b[y][x] = 0;
            if (score > alpha) alpha = score;
            results.add(new ScoredMove(c.move, score));
        }
        results.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return results;
    }

    private static int negamax(int[][] b, int mover, int depth, int alpha, int beta, long deadline) {
        if (depth <= 0) return evaluate(b, mover);
        if (System.nanoTime() > deadline) throw new Abort();
        List<ScoredMove> cs = candidates(b, mover, NODE_WIDTH);
        if (cs.isEmpty()) return 0;
        int best = Integer.MIN_VALUE + 1, op = other(mover);
        for (ScoredMove c : cs) {
            int x = c.move.x(), y = c.move.y();
            b[y][x] = mover;
            int score = isWin(b, x, y, mover) ? FIVE + depth : -negamax(b, op, depth - 1, -beta, -alpha, deadline);
            b[y][x] = 0;
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    private static int evaluate(int[][] b, int mover) {
        int mine = 0, theirs = 0;
        for (int y = 0; y < b.length; y++) for (int x = 0; x < b[0].length; x++) {
            int p = b[y][x];
            if (p == 0) continue;
            for (int[] d : DIRECTIONS) {
                int px = x - d[0], py = y - d[1];
                if (inB(b, px, py) && b[py][px] == p) continue;
                int f = count(b, x, y, d[0], d[1], p), back = count(b, x, y, -d[0], -d[1], p);
                int open = (isOpen(b, x + (f + 1) * d[0], y + (f + 1) * d[1]) ? 1 : 0)
                        + (isOpen(b, x - (back + 1) * d[0], y - (back + 1) * d[1]) ? 1 : 0);
                int v = patternValue(f + back + 1, open);
                if (p == mover) mine += v; else theirs += v;
            }
        }
        return mine - theirs;
    }

    private static List<ScoredMove> candidates(int[][] b, int p, int limit) {
        int op = other(p), cx = b[0].length / 2, cy = b.length / 2;
        boolean empty = true;
        List<ScoredMove> m = new ArrayList<>();
        for (int y = 0; y < b.length; y++) for (int x = 0; x < b[0].length; x++) {
            if (b[y][x] != 0) { empty = false; continue; }
            if (!hasNeighbor(b, x, y) && !(x == cx && y == cy)) continue;
            int score = scorePoint(b, x, y, p) * 6 + scorePoint(b, x, y, op) * 4 - Math.abs(x - cx) - Math.abs(y - cy);
            m.add(new ScoredMove(new Move(x, y, p), score));
        }
        if (empty) return List.of(new ScoredMove(new Move(cx, cy, p), 1));
        m.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return m.size() > limit ? new ArrayList<>(m.subList(0, limit)) : m;
    }

    private static boolean hasNeighbor(int[][] b, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int nx = x + dx, ny = y + dy;
            if (ny >= 0 && ny < b.length && nx >= 0 && nx < b[0].length && b[ny][nx] != 0) return true;
        }
        return false;
    }

    private static int scorePoint(int[][] b, int x, int y, int p) {
        int s = 0;
        for (int[] d : DIRECTIONS) {
            int f = count(b, x, y, d[0], d[1], p), back = count(b, x, y, -d[0], -d[1], p);
            int open = (isOpen(b, x + (f + 1) * d[0], y + (f + 1) * d[1]) ? 1 : 0)
                    + (isOpen(b, x - (back + 1) * d[0], y - (back + 1) * d[1]) ? 1 : 0);
            s += patternValue(f + back + 1, open);
        }
        return s;
    }

    private static int patternValue(int s, int o) {
        if (s >= 5) return FIVE;
        if (s == 4) return o == 2 ? OPEN_FOUR : FOUR;
        if (s == 3) return o == 2 ? OPEN_THREE : THREE;
        if (s == 2) return o == 2 ? OPEN_TWO : TWO;
        return o == 2 ? ONE : 0;
    }

    private static List<Move> winningMoves(int[][] b, int p) {
        List<Move> m = new ArrayList<>();
        for (int y = 0; y < b.length; y++) for (int x = 0; x < b[0].length; x++)
            if (b[y][x] == 0) { b[y][x] = p; if (isWin(b, x, y, p)) m.add(new Move(x, y, p)); b[y][x] = 0; }
        return m;
    }

    private static boolean isWin(int[][] b, int x, int y, int p) {
        for (int[] d : DIRECTIONS) if (1 + count(b, x, y, d[0], d[1], p) + count(b, x, y, -d[0], -d[1], p) >= 5) return true;
        return false;
    }

    private static int count(int[][] b, int x, int y, int dx, int dy, int p) {
        int n = 0;
        for (x += dx, y += dy; y >= 0 && y < b.length && x >= 0 && x < b[0].length && b[y][x] == p; x += dx, y += dy) n++;
        return n;
    }

    private static boolean isOpen(int[][] b, int x, int y) { return inB(b, x, y) && b[y][x] == 0; }
    private static boolean inB(int[][] b, int x, int y) { return y >= 0 && y < b.length && x >= 0 && x < b[0].length; }
    private static int other(int p) { return p == 1 ? 2 : 1; }
    private record ScoredMove(Move move, int score) {}
    private static final class Abort extends RuntimeException {}
}