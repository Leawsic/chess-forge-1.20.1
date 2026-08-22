package site.leawsic.chess.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 围棋 AI（一步前瞻的启发式落子）。
 *
 * <p>围棋没有可靠的手写局面评估函数，深搜收益很低，因此这里不做树搜索，
 * 而是把提子、救子、打吃、眼位、边线等要素编成权重打分，在近似最优的点位中随机选择。
 *
 * <p>难度通过放宽「可接受分差」实现：档位越低容忍区间越大，
 * 于是 AI 会从更多平庸的点位里随机挑选，表现得更松散。
 */
public final class GoAi {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private GoAi() {}

    /** 为 {@code p} 方选点，{@code koX}/{@code koY} 为禁着点（劫），无处可下返回 {@code null}（弃一手）。 */
    public static Move chooseMove(int[][] b, int p, int koX, int koY) {
        return chooseMove(b, p, koX, koY, AiDifficulty.HARD);
    }

    public static Move chooseMove(int[][] b, int p, int koX, int koY, AiDifficulty difficulty) {
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
        int tolerance = tolerance(difficulty);
        List<Move> pool = new ArrayList<>();
        for (ScoredMove s : moves) if (s.score >= best - tolerance) pool.add(s.move);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** 可接受分差：越大则候选池越宽，落子越随意。 */
    private static int tolerance(AiDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 6_000;
            case NORMAL -> 2_200;
            case HARD -> 900;
        };
    }

    private static int score(int[][] b, int x, int y, int p, int op, Position pos) {
        int score = pos.captured * 3_000 + pos.liberties * 40;
        // 落下后只剩一气且没吃到子，等于送死。
        if (pos.captured == 0 && pos.liberties == 1) score -= 9_000;
        // 填自己的真眼是纯粹的损失，且可能把活棋做死。
        if (isOwnEye(b, x, y, p)) score -= 12_000;

        int atari = 0, rescue = 0;
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (!inBounds(b, nx, ny)) continue;
            int neighbour = b[ny][nx];
            if (neighbour == op) {
                // 把对方一块棋逼到一气就是打吃。
                if (liberties(b, group(b, nx, ny, op)) == 1) atari += 2_400;
            } else if (neighbour == p) {
                Set<Long> own = group(b, nx, ny, p);
                // 自己已被打吃的块，接上去能长气就值得救。
                if (liberties(b, own) == 1 && pos.liberties > 1) rescue += own.size() * 1_800 + 1_200;
            }
        }
        score += atari + rescue;
        score += adjacent(b, x, y, p) * 120 + adjacent(b, x, y, op) * 80;

        int rows = b.length, cols = b[0].length;
        int edge = Math.min(Math.min(x, cols - 1 - x), Math.min(y, rows - 1 - y));
        // 第一线效率低，第三、四线是布局要点。
        if (edge == 0) score -= 900;
        else if (edge == 1) score -= 250;
        else if (edge == 2 || edge == 3) score += 260;
        score -= Math.abs(x - cols / 2) + Math.abs(y - rows / 2);
        return score;
    }

    /**
     * 判断该空点是否为 {@code p} 的真眼：四邻全是自己的子，
     * 且斜角上对方的子不足以破眼（边角允许一个，中腹允许一个）。
     */
    private static boolean isOwnEye(int[][] b, int x, int y, int p) {
        int op = other(p);
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (!inBounds(b, nx, ny)) continue;
            if (b[ny][nx] != p) return false;
        }
        int hostile = 0, diagonals = 0;
        for (int[] d : DIAGONALS) {
            int nx = x + d[0], ny = y + d[1];
            if (!inBounds(b, nx, ny)) continue;
            diagonals++;
            if (b[ny][nx] == op) hostile++;
        }
        // 边上或角上的眼只要有一个斜角被占就不再成眼。
        return diagonals < 4 ? hostile == 0 : hostile <= 1;
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
