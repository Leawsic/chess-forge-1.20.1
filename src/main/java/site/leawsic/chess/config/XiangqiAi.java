package site.leawsic.chess.config;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 中国象棋 AI。
 *
 * <p>搜索框架参考 XiangQi Wizard Light 的组织方式（迭代加深 + 主要变例搜索 +
 * 置换表 + 杀手启发 + 历史启发 + 空着裁剪 + 静态搜索），但棋盘表示沿用本模组的
 * {@code int[ROWS][COLS]}，规则以 {@link XiangqiConfig} 为准。
 *
 * <p>相比逐格调用 {@code isLegalMove} 的朴素写法，这里做了两个关键优化：
 * 走法按棋子类型直接枚举，照面检测只从将/帅所在格发射线，
 * 因此同样时间内可搜索的节点数高一到两个数量级。
 */
public final class XiangqiAi {
    public record Move(int fromX, int fromY, int toX, int toY) {}

    private static final int COLS = XiangqiConfig.COLS;
    private static final int ROWS = XiangqiConfig.ROWS;
    private static final int SQUARES = ROWS * COLS;
    private static final int MATE = 30_000, WIN = MATE - 300;
    private static final int LIMIT_DEPTH = 40, MAX_MOVES = 128, NULL_REDUCTION = 2;
    private static final int TT_MASK = (1 << 17) - 1;
    private static final int FLAG_ALPHA = 1, FLAG_BETA = 2, FLAG_PV = 3;

    private static final int[][] GENERAL_STEPS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
    private static final int[][] ADVISOR_STEPS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ELEPHANT_STEPS = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    private static final int[][] HORSE_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};

    /** 子力价值，索引为 {@code Math.abs(piece)}。 */
    private static final int[] VALUE = {0, 0, 220, 220, 440, 900, 450, 100};
    /** 位置价值：行（按己方视角从底线数起）与列分量之和。 */
    private static final int[] ROOK_R = {8, 12, 18, 24, 28, 30, 28, 24, 20, 16}, ROOK_F = {0, 8, 16, 24, 30, 30, 24, 16, 8};
    private static final int[] CANNON_R = {0, 4, 10, 18, 26, 32, 34, 28, 20, 12}, CANNON_F = {0, 4, 8, 12, 14, 14, 12, 8, 4};
    private static final int[] HORSE_R = {0, 6, 12, 18, 24, 26, 24, 18, 12, 6}, HORSE_F = {0, 10, 18, 24, 26, 26, 24, 18, 10};
    private static final int[] ELEPHANT_R = {26, 12, 18, 6, 8, 0, 0, 0, 0, 0}, ELEPHANT_F = {0, 0, 0, 8, 12, 8, 0, 0, 0};
    private static final int[] ADVISOR_R = {30, 16, 8, 0, 0, 0, 0, 0, 0, 0}, ADVISOR_F = {0, 0, 0, 14, 18, 14, 0, 0, 0};
    private static final int[] GENERAL_R = {30, 12, 4, 0, 0, 0, 0, 0, 0, 0}, GENERAL_F = {0, 0, 0, 12, 16, 12, 0, 0, 0};
    private static final int[] SOLDIER_R = {0, 0, 0, 14, 22, 40, 60, 90, 120, 150}, SOLDIER_F = {0, 0, 2, 8, 10, 8, 2, 0, 0};

    /** Zobrist 键，索引为 {@link #pieceKey(int)}。 */
    private static final long[][] ZOBRIST = new long[16][SQUARES];
    private static final long ZOBRIST_SIDE;

    static {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (long[] table : ZOBRIST) for (int i = 0; i < SQUARES; i++) table[i] = random.nextLong();
        ZOBRIST_SIDE = random.nextLong();
    }

    private final int[][] board;
    private final long budgetNanos;
    private final int depthLimit;
    private final float blunderChance;
    private final long[] ttKey = new long[TT_MASK + 1];
    private final int[] ttData = new int[TT_MASK + 1];
    private final int[] ttMove = new int[TT_MASK + 1];
    private final int[] history = new int[SQUARES * SQUARES];
    private final int[][] killers = new int[LIMIT_DEPTH][2];
    private final int[][] moveBuffer = new int[LIMIT_DEPTH][MAX_MOVES];
    private final int[][] scoreBuffer = new int[LIMIT_DEPTH][MAX_MOVES];
    private final long[] pathKeys = new long[LIMIT_DEPTH];
    /** 根节点每个合法走法的得分，供难度降级时挑选次优着法。 */
    private final int[] rootMoves = new int[MAX_MOVES];
    private final int[] rootScores = new int[MAX_MOVES];
    private int rootCount;
    private long zobrist;
    private long deadline;
    private int ply;
    private int rootMove;
    private boolean aborted;

    private XiangqiAi(int[][] board, AiDifficulty difficulty) {
        this.board = board;
        this.budgetNanos = difficulty.budgetNanos();
        this.depthLimit = Math.min(LIMIT_DEPTH, difficulty.maxDepth());
        this.blunderChance = difficulty.blunderChance();
    }

    /** 为 {@code side} 方选择一步走法，返回 {@code null} 表示无合法走法（被杀或困毙）。 */
    public static Move chooseMove(int[][] board, int side) {
        return chooseMove(board, side, AiDifficulty.HARD);
    }

    public static Move chooseMove(int[][] board, int side, AiDifficulty difficulty) {
        int[][] working = new int[board.length][];
        for (int y = 0; y < board.length; y++) working[y] = board[y].clone();
        Move chosen = new XiangqiAi(working, difficulty).search(side);
        // 生成器与规则表理论上等价，但棋盘完整性不能依赖这个假设，落子前再校验一次。
        return chosen != null && isFullyLegal(board, side, chosen) ? chosen : fallbackMove(board, side);
    }

    private Move search(int side) {
        zobrist = computeZobrist(side);
        deadline = System.nanoTime() + budgetNanos;
        rootMove = 0;
        int best = 0;
        for (int depth = 1; depth <= depthLimit; depth++) {
            rootMove = best;
            int score = searchRoot(side, depth);
            if (aborted) break;
            best = rootMove;
            if (best == 0) break;
            if (score > WIN || score < -WIN) break;
            if (System.nanoTime() > deadline) break;
        }
        if (best == 0) return null;
        int played = applyBlunder(best);
        return decode(played);
    }

    /**
     * 低难度下按概率放弃最优着法。取分值排名第 2、3 位的着法之一，
     * 而不是完全随机走子 —— 次优着法看起来像漏看，随机着法看起来像坏了。
     */
    private int applyBlunder(int best) {
        if (blunderChance <= 0 || rootCount < 2) return best;
        if (ThreadLocalRandom.current().nextFloat() >= blunderChance) return best;
        int secondMove = 0, secondScore = Integer.MIN_VALUE, thirdMove = 0, thirdScore = Integer.MIN_VALUE;
        for (int i = 0; i < rootCount; i++) {
            if (rootMoves[i] == best) continue;
            if (rootScores[i] > secondScore) {
                thirdMove = secondMove; thirdScore = secondScore;
                secondMove = rootMoves[i]; secondScore = rootScores[i];
            } else if (rootScores[i] > thirdScore) {
                thirdMove = rootMoves[i]; thirdScore = rootScores[i];
            }
        }
        if (secondMove == 0) return best;
        // 送将或被杀的走法不选，失误也该有底线。
        if (secondScore < -WIN) return best;
        if (thirdMove != 0 && thirdScore > -WIN && ThreadLocalRandom.current().nextBoolean()) return thirdMove;
        return secondMove;
    }

    private int searchRoot(int side, int depth) {
        int count = generate(side, ply, false);
        orderMoves(side, ply, count, rootMove);
        int bestScore = -MATE, bestMove = 0, legal = 0;
        rootCount = 0;
        // 同分走法收集起来随机取一个，避免同一局面永远走出同一步。
        int[] tied = new int[MAX_MOVES];
        int tiedCount = 0;
        for (int i = 0; i < count; i++) {
            int move = pick(ply, count, i);
            int captured = make(move);
            if (generalAttacked(side)) { unmake(move, captured); continue; }
            legal++;
            int score;
            if (Math.abs(captured) == XiangqiConfig.GENERAL) {
                score = MATE - ply;
            } else {
                pathKeys[ply] = zobrist;
                ply++;
                if (bestMove == 0) {
                    score = -searchFull(-side, depth - 1, -MATE, MATE, true);
                } else {
                    score = -searchFull(-side, depth - 1, -bestScore - 1, -bestScore, true);
                    if (!aborted && score > bestScore) score = -searchFull(-side, depth - 1, -MATE, -bestScore, true);
                }
                ply--;
            }
            unmake(move, captured);
            if (aborted) break;
            if (rootCount < MAX_MOVES) { rootMoves[rootCount] = move; rootScores[rootCount] = score; rootCount++; }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                tiedCount = 0;
                tied[tiedCount++] = move;
            } else if (score == bestScore && tiedCount < MAX_MOVES) {
                tied[tiedCount++] = move;
            }
        }
        if (legal == 0) { rootMove = 0; return -MATE; }
        if (!aborted && bestMove != 0) {
            rootMove = tiedCount > 1 ? tied[ThreadLocalRandom.current().nextInt(tiedCount)] : bestMove;
            recordBest(rootMove, depth);
        }
        return bestScore;
    }

    private int searchFull(int side, int depth, int alpha, int beta, boolean allowNull) {
        if (depth <= 0) return quiescence(side, alpha, beta);
        if (checkTime()) return 0;
        if (ply > 0 && isRepetition()) return 0;
        if (ply >= depthLimit + 4 || ply >= LIMIT_DEPTH - 2) return evaluate(side);

        int index = (int) (zobrist & TT_MASK);
        int hashMove = 0;
        if (ttKey[index] == zobrist) {
            hashMove = ttMove[index];
            int data = ttData[index];
            int storedDepth = data & 0xff, flag = (data >>> 8) & 0x3, value = data >> 10;
            if (storedDepth >= depth && Math.abs(value) < WIN) {
                if (flag == FLAG_PV) return value;
                if (flag == FLAG_BETA && value >= beta) return value;
                if (flag == FLAG_ALPHA && value <= alpha) return value;
            }
        }

        boolean inCheck = generalAttacked(side);
        // 空着裁剪：不在被将、不在残局、剩余深度足够时才用。
        if (allowNull && !inCheck && depth >= NULL_REDUCTION + 1 && beta < WIN && hasHeavyMaterial(side)) {
            zobrist ^= ZOBRIST_SIDE;
            pathKeys[ply] = zobrist;
            ply++;
            int nullScore = -searchFull(-side, depth - NULL_REDUCTION - 1, -beta, 1 - beta, false);
            ply--;
            zobrist ^= ZOBRIST_SIDE;
            if (aborted) return 0;
            if (nullScore >= beta) return nullScore;
        }

        int count = generate(side, ply, false);
        orderMoves(side, ply, count, hashMove);
        int bestScore = -MATE, bestMove = 0, legal = 0, originalAlpha = alpha;
        for (int i = 0; i < count; i++) {
            int move = pick(ply, count, i);
            int captured = make(move);
            if (generalAttacked(side)) { unmake(move, captured); continue; }
            legal++;
            int score;
            if (Math.abs(captured) == XiangqiConfig.GENERAL) {
                score = MATE - ply;
            } else {
                // 被将时延伸一层，防止在将军序列中做出错误评估。
                int childDepth = inCheck ? depth : depth - 1;
                pathKeys[ply] = zobrist;
                ply++;
                if (bestMove == 0) {
                    score = -searchFull(-side, childDepth, -beta, -alpha, true);
                } else {
                    score = -searchFull(-side, childDepth, -alpha - 1, -alpha, true);
                    if (!aborted && score > alpha && score < beta) score = -searchFull(-side, childDepth, -beta, -alpha, true);
                }
                ply--;
            }
            unmake(move, captured);
            if (aborted) return 0;
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                if (score >= beta) {
                    store(index, depth, FLAG_BETA, score, move);
                    recordBest(move, depth);
                    return score;
                }
                if (score > alpha) alpha = score;
            }
        }
        if (legal == 0) return -MATE + ply;
        store(index, depth, alpha > originalAlpha ? FLAG_PV : FLAG_ALPHA, bestScore, bestMove);
        if (bestMove != 0 && alpha > originalAlpha) recordBest(bestMove, depth);
        return bestScore;
    }

    private int quiescence(int side, int alpha, int beta) {
        if (checkTime()) return 0;
        if (ply >= LIMIT_DEPTH - 2) return evaluate(side);
        boolean inCheck = generalAttacked(side);
        int bestScore = -MATE;
        if (!inCheck) {
            int stand = evaluate(side);
            if (stand >= beta) return stand;
            bestScore = stand;
            if (stand > alpha) alpha = stand;
        }
        // 被将时必须搜索全部走法，否则会漏掉解将手。
        int count = generate(side, ply, !inCheck);
        orderMoves(side, ply, count, 0);
        int legal = 0;
        for (int i = 0; i < count; i++) {
            int move = pick(ply, count, i);
            int captured = make(move);
            if (generalAttacked(side)) { unmake(move, captured); continue; }
            legal++;
            int score;
            if (Math.abs(captured) == XiangqiConfig.GENERAL) {
                score = MATE - ply;
            } else {
                pathKeys[ply] = zobrist;
                ply++;
                score = -quiescence(-side, -beta, -alpha);
                ply--;
            }
            unmake(move, captured);
            if (aborted) return 0;
            if (score > bestScore) {
                bestScore = score;
                if (score >= beta) return score;
                if (score > alpha) alpha = score;
            }
        }
        if (inCheck && legal == 0) return -MATE + ply;
        return bestScore;
    }

    // ---------------------------------------------------------------- 走法生成

    /** 生成 {@code side} 方的伪合法走法，{@code capturesOnly} 为真时只生成吃子。 */
    private int generate(int side, int depth, boolean capturesOnly) {
        int[] out = moveBuffer[depth];
        int n = 0;
        for (int y = 0; y < ROWS && n < MAX_MOVES - 24; y++) {
            for (int x = 0; x < COLS && n < MAX_MOVES - 24; x++) {
                int piece = board[y][x];
                if (piece == 0 || XiangqiConfig.color(piece) != side) continue;
                switch (Math.abs(piece)) {
                    case XiangqiConfig.GENERAL -> n = generalMoves(out, n, x, y, side, capturesOnly);
                    case XiangqiConfig.ADVISOR -> n = stepMoves(out, n, x, y, side, ADVISOR_STEPS, true, capturesOnly);
                    case XiangqiConfig.ELEPHANT -> n = elephantMoves(out, n, x, y, side, capturesOnly);
                    case XiangqiConfig.HORSE -> n = horseMoves(out, n, x, y, side, capturesOnly);
                    case XiangqiConfig.ROOK -> n = rookMoves(out, n, x, y, side, capturesOnly);
                    case XiangqiConfig.CANNON -> n = cannonMoves(out, n, x, y, side, capturesOnly);
                    default -> n = soldierMoves(out, n, x, y, side, capturesOnly);
                }
            }
        }
        return n;
    }

    private int generalMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        for (int[] step : GENERAL_STEPS) {
            int tx = x + step[0], ty = y + step[1];
            if (!inPalace(tx, ty, side)) continue;
            n = add(out, n, x, y, tx, ty, side, capturesOnly);
        }
        // 飞将：同一列上直接吃掉对方将/帅。
        for (int dir = -1; dir <= 1; dir += 2) {
            for (int ty = y + dir; ty >= 0 && ty < ROWS; ty += dir) {
                int target = board[ty][x];
                if (target == 0) continue;
                if (Math.abs(target) == XiangqiConfig.GENERAL && XiangqiConfig.color(target) != side) n = add(out, n, x, y, x, ty, side, capturesOnly);
                break;
            }
        }
        return n;
    }

    private int stepMoves(int[] out, int n, int x, int y, int side, int[][] steps, boolean palaceOnly, boolean capturesOnly) {
        for (int[] step : steps) {
            int tx = x + step[0], ty = y + step[1];
            if (palaceOnly ? !inPalace(tx, ty, side) : !inBounds(tx, ty)) continue;
            n = add(out, n, x, y, tx, ty, side, capturesOnly);
        }
        return n;
    }

    private int elephantMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        for (int[] step : ELEPHANT_STEPS) {
            int tx = x + step[0], ty = y + step[1];
            if (!inBounds(tx, ty) || crossedRiver(ty, side)) continue;
            if (board[y + step[1] / 2][x + step[0] / 2] != 0) continue;
            n = add(out, n, x, y, tx, ty, side, capturesOnly);
        }
        return n;
    }

    private int horseMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        for (int[] step : HORSE_STEPS) {
            int tx = x + step[0], ty = y + step[1];
            if (!inBounds(tx, ty)) continue;
            int legX = Math.abs(step[0]) == 2 ? x + step[0] / 2 : x;
            int legY = Math.abs(step[1]) == 2 ? y + step[1] / 2 : y;
            if (board[legY][legX] != 0) continue;
            n = add(out, n, x, y, tx, ty, side, capturesOnly);
        }
        return n;
    }

    private int rookMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        for (int[] step : GENERAL_STEPS) {
            for (int tx = x + step[0], ty = y + step[1]; inBounds(tx, ty); tx += step[0], ty += step[1]) {
                int target = board[ty][tx];
                if (target == 0) {
                    if (!capturesOnly) n = add(out, n, x, y, tx, ty, side, false);
                    continue;
                }
                if (XiangqiConfig.color(target) != side) n = add(out, n, x, y, tx, ty, side, capturesOnly);
                break;
            }
        }
        return n;
    }

    private int cannonMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        for (int[] step : GENERAL_STEPS) {
            int tx = x + step[0], ty = y + step[1];
            for (; inBounds(tx, ty) && board[ty][tx] == 0; tx += step[0], ty += step[1]) {
                if (!capturesOnly) n = add(out, n, x, y, tx, ty, side, false);
            }
            if (!inBounds(tx, ty)) continue;
            // 越过炮架后寻找第一个棋子，是敌子则可吃。
            for (tx += step[0], ty += step[1]; inBounds(tx, ty); tx += step[0], ty += step[1]) {
                int target = board[ty][tx];
                if (target == 0) continue;
                if (XiangqiConfig.color(target) != side) n = add(out, n, x, y, tx, ty, side, capturesOnly);
                break;
            }
        }
        return n;
    }

    private int soldierMoves(int[] out, int n, int x, int y, int side, boolean capturesOnly) {
        int forward = side == XiangqiConfig.RED ? -1 : 1;
        n = add(out, n, x, y, x, y + forward, side, capturesOnly);
        if (crossedRiver(y, side)) {
            n = add(out, n, x, y, x - 1, y, side, capturesOnly);
            n = add(out, n, x, y, x + 1, y, side, capturesOnly);
        }
        return n;
    }

    private int add(int[] out, int n, int fx, int fy, int tx, int ty, int side, boolean capturesOnly) {
        if (n >= MAX_MOVES || !inBounds(tx, ty)) return n;
        int target = board[ty][tx];
        if (target != 0 && XiangqiConfig.color(target) == side) return n;
        if (capturesOnly && target == 0) return n;
        out[n++] = (fy * COLS + fx) | ((ty * COLS + tx) << 8);
        return n;
    }

    // ---------------------------------------------------------------- 走法排序

    private void orderMoves(int side, int depth, int count, int hashMove) {
        int[] moves = moveBuffer[depth];
        int[] scores = scoreBuffer[depth];
        int killer1 = killers[depth][0], killer2 = killers[depth][1];
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            int from = move & 0xff, to = (move >>> 8) & 0xff;
            int victim = board[to / COLS][to % COLS];
            int attacker = board[from / COLS][from % COLS];
            int score;
            if (move == hashMove) score = 1 << 24;
            else if (victim != 0) score = (1 << 20) + VALUE[Math.abs(victim)] * 16 - VALUE[Math.abs(attacker)];
            else if (move == killer1) score = (1 << 19) + 2;
            else if (move == killer2) score = (1 << 19) + 1;
            else score = history[from * SQUARES + to] + pst(attacker, to % COLS, to / COLS, side) - pst(attacker, from % COLS, from / COLS, side);
            scores[i] = score;
        }
    }

    /** 选择序：每次取剩余走法中分值最高的一个，避免整表排序。 */
    private int pick(int depth, int count, int index) {
        int[] moves = moveBuffer[depth];
        int[] scores = scoreBuffer[depth];
        int best = index;
        for (int i = index + 1; i < count; i++) if (scores[i] > scores[best]) best = i;
        if (best != index) {
            int move = moves[best]; moves[best] = moves[index]; moves[index] = move;
            int score = scores[best]; scores[best] = scores[index]; scores[index] = score;
        }
        return moves[index];
    }

    private void recordBest(int move, int depth) {
        int from = move & 0xff, to = (move >>> 8) & 0xff;
        history[from * SQUARES + to] += depth * depth;
        int[] slot = killers[Math.min(ply, LIMIT_DEPTH - 1)];
        if (slot[0] != move) { slot[1] = slot[0]; slot[0] = move; }
    }

    private void store(int index, int depth, int flag, int value, int move) {
        int stored = ttData[index];
        if (ttKey[index] == zobrist && (stored & 0xff) > depth) return;
        ttKey[index] = zobrist;
        ttData[index] = (Math.max(-WIN, Math.min(WIN, value)) << 10) | (flag << 8) | Math.min(depth, 0xff);
        ttMove[index] = move;
    }

    // ---------------------------------------------------------------- 局面操作

    private int make(int move) {
        int from = move & 0xff, to = (move >>> 8) & 0xff;
        int fx = from % COLS, fy = from / COLS, tx = to % COLS, ty = to / COLS;
        int piece = board[fy][fx], captured = board[ty][tx];
        if (captured != 0) zobrist ^= ZOBRIST[pieceKey(captured)][to];
        zobrist ^= ZOBRIST[pieceKey(piece)][from] ^ ZOBRIST[pieceKey(piece)][to] ^ ZOBRIST_SIDE;
        board[ty][tx] = piece;
        board[fy][fx] = 0;
        return captured;
    }

    private void unmake(int move, int captured) {
        int from = move & 0xff, to = (move >>> 8) & 0xff;
        int fx = from % COLS, fy = from / COLS, tx = to % COLS, ty = to / COLS;
        int piece = board[ty][tx];
        board[fy][fx] = piece;
        board[ty][tx] = captured;
        zobrist ^= ZOBRIST[pieceKey(piece)][from] ^ ZOBRIST[pieceKey(piece)][to] ^ ZOBRIST_SIDE;
        if (captured != 0) zobrist ^= ZOBRIST[pieceKey(captured)][to];
    }

    private long computeZobrist(int side) {
        long key = side == XiangqiConfig.RED ? 0L : ZOBRIST_SIDE;
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
            int piece = board[y][x];
            if (piece != 0) key ^= ZOBRIST[pieceKey(piece)][y * COLS + x];
        }
        return key;
    }

    private boolean isRepetition() {
        for (int i = ply - 2; i >= 0; i -= 2) if (pathKeys[i] == zobrist) return true;
        return false;
    }

    // ---------------------------------------------------------------- 将军检测

    /**
     * 判断 {@code side} 方的将/帅是否正被攻击。只从将的位置发射线，
     * 士象无法攻到对方九宫故不检测。
     */
    private boolean generalAttacked(int side) {
        int gx = -1, gy = -1;
        int lowY = side == XiangqiConfig.RED ? ROWS - 3 : 0;
        for (int y = lowY; y < lowY + 3 && gx < 0; y++) for (int x = 3; x <= 5; x++) {
            if (board[y][x] == side * XiangqiConfig.GENERAL) { gx = x; gy = y; break; }
        }
        if (gx < 0) {
            for (int y = 0; y < ROWS && gx < 0; y++) for (int x = 0; x < COLS; x++) {
                if (board[y][x] == side * XiangqiConfig.GENERAL) { gx = x; gy = y; break; }
            }
        }
        if (gx < 0) return true;

        int enemy = -side;
        for (int[] step : GENERAL_STEPS) {
            int blockers = 0;
            for (int x = gx + step[0], y = gy + step[1]; inBounds(x, y); x += step[0], y += step[1]) {
                int piece = board[y][x];
                if (piece == 0) continue;
                blockers++;
                if (blockers == 1) {
                    if (XiangqiConfig.color(piece) != enemy) continue;
                    int type = Math.abs(piece);
                    if (type == XiangqiConfig.ROOK) return true;
                    if (type == XiangqiConfig.GENERAL) return true;
                    if (type == XiangqiConfig.SOLDIER && soldierAttacks(x, y, gx, gy, enemy)) return true;
                } else {
                    if (XiangqiConfig.color(piece) == enemy && Math.abs(piece) == XiangqiConfig.CANNON) return true;
                    break;
                }
            }
        }
        for (int[] step : HORSE_STEPS) {
            int hx = gx + step[0], hy = gy + step[1];
            if (!inBounds(hx, hy)) continue;
            int piece = board[hy][hx];
            if (piece == 0 || XiangqiConfig.color(piece) != enemy || Math.abs(piece) != XiangqiConfig.HORSE) continue;
            int legX = Math.abs(step[0]) == 2 ? gx + step[0] / 2 : hx;
            int legY = Math.abs(step[1]) == 2 ? gy + step[1] / 2 : hy;
            if (board[legY][legX] == 0) return true;
        }
        return false;
    }

    private static boolean soldierAttacks(int sx, int sy, int tx, int ty, int side) {
        if (sx == tx && ty - sy == (side == XiangqiConfig.RED ? -1 : 1)) return true;
        return sy == ty && Math.abs(sx - tx) == 1 && crossedRiver(sy, side);
    }

    // ---------------------------------------------------------------- 局面评估

    private int evaluate(int side) {
        int score = 0, redGuards = 0, blackGuards = 0, redHeavy = 0, blackHeavy = 0;
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
            int piece = board[y][x];
            if (piece == 0) continue;
            int owner = XiangqiConfig.color(piece), type = Math.abs(piece);
            int value = VALUE[type] + pst(piece, x, y, owner);
            score += owner == side ? value : -value;
            if (type == XiangqiConfig.ADVISOR || type == XiangqiConfig.ELEPHANT) {
                if (owner == XiangqiConfig.RED) redGuards++; else blackGuards++;
            } else if (type == XiangqiConfig.ROOK || type == XiangqiConfig.CANNON || type == XiangqiConfig.HORSE) {
                if (owner == XiangqiConfig.RED) redHeavy++; else blackHeavy++;
            }
        }
        // 缺士象在对方大子多时格外危险。
        int redExposure = (4 - redGuards) * blackHeavy * 12;
        int blackExposure = (4 - blackGuards) * redHeavy * 12;
        score += side == XiangqiConfig.RED ? blackExposure - redExposure : redExposure - blackExposure;
        return score;
    }

    private boolean hasHeavyMaterial(int side) {
        int heavy = 0;
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
            int piece = board[y][x];
            if (piece == 0 || XiangqiConfig.color(piece) != side) continue;
            int type = Math.abs(piece);
            if (type == XiangqiConfig.ROOK || type == XiangqiConfig.CANNON || type == XiangqiConfig.HORSE) heavy++;
        }
        return heavy >= 2;
    }

    private static int pst(int piece, int x, int y, int side) {
        int r = side == XiangqiConfig.RED ? ROWS - 1 - y : y;
        return switch (Math.abs(piece)) {
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

    // ---------------------------------------------------------------- 工具方法

    private boolean checkTime() {
        if (aborted) return true;
        if (System.nanoTime() > deadline) aborted = true;
        return aborted;
    }

    private static Move decode(int move) {
        int from = move & 0xff, to = (move >>> 8) & 0xff;
        return new Move(from % COLS, from / COLS, to % COLS, to / COLS);
    }

    private static int pieceKey(int piece) { return piece > 0 ? Math.abs(piece) : Math.abs(piece) + 8; }
    private static boolean inBounds(int x, int y) { return x >= 0 && x < COLS && y >= 0 && y < ROWS; }
    private static boolean inPalace(int x, int y, int side) { return x >= 3 && x <= 5 && (side == XiangqiConfig.RED ? y >= 7 && y <= 9 : y <= 2); }
    private static boolean crossedRiver(int y, int side) { return side == XiangqiConfig.RED ? y <= 4 : y >= 5; }

    private static boolean isFullyLegal(int[][] board, int side, Move move) {
        if (!XiangqiConfig.inBounds(move.fromX(), move.fromY()) || !XiangqiConfig.inBounds(move.toX(), move.toY())) return false;
        int piece = board[move.fromY()][move.fromX()];
        if (piece == 0 || XiangqiConfig.color(piece) != side) return false;
        if (XiangqiConfig.color(board[move.toY()][move.toX()]) == side) return false;
        if (!XiangqiConfig.isLegalMove(board, move.fromX(), move.fromY(), move.toX(), move.toY())) return false;
        int captured = board[move.toY()][move.toX()];
        board[move.toY()][move.toX()] = piece;
        board[move.fromY()][move.fromX()] = 0;
        boolean safe = !XiangqiConfig.isInCheck(board, side);
        board[move.fromY()][move.fromX()] = piece;
        board[move.toY()][move.toX()] = captured;
        return safe;
    }

    /** 生成器出现意外时的保险绳：按规则表扫描出任意一步合法走法。 */
    private static Move fallbackMove(int[][] board, int side) {
        for (int fy = 0; fy < ROWS; fy++) for (int fx = 0; fx < COLS; fx++) {
            if (XiangqiConfig.color(board[fy][fx]) != side) continue;
            for (int ty = 0; ty < ROWS; ty++) for (int tx = 0; tx < COLS; tx++) {
                Move move = new Move(fx, fy, tx, ty);
                if (isFullyLegal(board, side, move)) return move;
            }
        }
        return null;
    }
}
