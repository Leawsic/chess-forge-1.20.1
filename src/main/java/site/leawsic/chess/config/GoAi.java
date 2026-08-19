package site.leawsic.chess.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class GoAi {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int TOLERANCE = 1_500;
    private GoAi() {}

    public static Move chooseMove(int[][] b, int p, int koX, int koY) {
        int op = other(p);
        List<ScoredMove> moves = new ArrayList<>();
        for (int y = 0; y < b.length; y++) for (int x = 0; x < b[0].length; x++) {
            if (b[y][x] != 0 || (x == koX && y == koY)) continue;
            Position pos = play(b, x, y, p);
            if (pos == null) continue;
            moves.add(new ScoredMove(new Move(x, y, p), score(b, x, y, p, op, pos)));
        }
        if (moves.isEmpty()) return null;
        moves.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        int best = moves.get(0).score;
        List<Move> pool = new ArrayList<>();
        for (ScoredMove s : moves) if (s.score >= best - TOLERANCE) pool.add(s);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).move;
    }

    private static int score(int[][] b, int x, int y, int p, int op, Position pos) {
        int score = pos.captured * 4_000 + pos.liberties * 45;
        if (pos.captured == 0 && pos.liberties == 1) score -= 9_000;
        int atari = 0;
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (inBounds(b, nx, ny) && b[ny][nx] == op && liberties(b, group(b, nx, ny, op)) == 1) atari++;
        }
        score += atari * 2_600;
        score += adjacent(b, x, y, p) * 150 + adjacent(b, x, y, op) * 70;
        score -= Math.abs(x - b[0].length / 2) + Math.abs(y - b.length / 2);
        return score;
    }

    private static Position play(int[][] b, int x, int y, int p) {
        int[][] c = copy(b);
        int op = other(p);
        c[y][x] = p;
        Set<Long> captured = new HashSet<>();
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (inBounds(c, nx, ny) && c[ny][nx] == op) {
                Set<Long> g = group(c, nx, ny, op);
                if (liberties(c, g) == 0) captured.addAll(g);
            }
        }
        for (long s : captured) c[(int) s][(int) (s >> 32)] = 0;
        int l = liberties(c, group(c, x, y, p));
        return l == 0 ? null : new Position(captured.size(), l);
    }

    private static int adjacent(int[][] b, int x, int y, int p) {
        int n = 0;
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (inBounds(b, nx, ny) && b[ny][nx] == p) n++;
        }
        return n;
    }

    private static Set<Long> group(int[][] b, int sx, int sy, int p) {
        Set<Long> s = new HashSet<>();
        Deque<Long> q = new ArrayDeque<>();
        q.add(key(sx, sy));
        while (!q.isEmpty()) {
            long stone = q.removeFirst();
            if (!s.add(stone)) continue;
            int x = (int) (stone >> 32), y = (int) stone;
            for (int[] d : DIRECTIONS) {
                int nx = x + d[0], ny = y + d[1];
                if (inBounds(b, nx, ny) && b[ny][nx] == p) q.add(key(nx, ny));
            }
        }
        return s;
    }

    private static int liberties(int[][] b, Set<Long> g) {
        Set<Long> l = new HashSet<>();
        for (long s : g) {
            int x = (int) (s >> 32), y = (int) s;
            for (int[] d : DIRECTIONS) {
                int nx = x + d[0], ny = y + d[1];
                if (inBounds(b, nx, ny) && b[ny][nx] == 0) l.add(key(nx, ny));
            }
        }
        return l.size();
    }

    private static int[][] copy(int[][] b) {
        int[][] c = new int[b.length][];
        for (int y = 0; y < b.length; y++) c[y] = b[y].clone();
        return c;
    }

    private static boolean inBounds(int[][] b, int x, int y) { return y >= 0 && y < b.length && x >= 0 && x < b[0].length; }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static int other(int p) { return p == 1 ? 2 : 1; }
    private record Position(int captured, int liberties) {}
    private record ScoredMove(Move move, int score) {}
}