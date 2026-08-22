package site.leawsic.chess.config;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 五子棋 AI。
 *
 * <p>威胁分类的思路参考 xechat-idea 的 ZhiZhangAIService（棋型分级 + 危险等级驱动的
 * 着法生成 + 迭代加深算杀），但棋型识别没有用字符串匹配：每个方向的九格窗口被编码成
 * 三进制下标，查一张静态初始化时递推出来的等级表。跳三、跳四这类带断点的棋型因此可以
 * 被精确识别，且单方向识别只需一次数组访问。
 *
 * <p>搜索分三层：先做必胜/必防的直接判定，再做 VCF（连续冲四）算杀，
 * 最后是带置换表、杀手启发、历史启发的主要变例搜索。
 */
public final class GomokuAi {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private static final int WINDOW = 9, HALF = WINDOW / 2, TABLE_SIZE = 19683;
    private static final int L_NONE = 0, L_ONE = 1, L_CLOSED_TWO = 2, L_OPEN_TWO = 3,
            L_CLOSED_THREE = 4, L_OPEN_THREE = 5, L_FOUR = 6, L_OPEN_FOUR = 7, L_FIVE = 8;
    /** 单方向棋型分值，索引为等级。 */
    private static final int[] LEVEL_SCORE = {0, 8, 40, 380, 620, 5_400, 7_600, 100_000, 2_000_000};
    /** 跨方向组合棋型分值。 */
    private static final int SCORE_FIVE = 2_000_000, SCORE_OPEN_FOUR = 300_000,
            SCORE_DOUBLE_FOUR = 260_000, SCORE_FOUR_THREE = 240_000, SCORE_DOUBLE_THREE = 40_000;

    private static final int WIN = 10_000_000;
    private static final int ROOT_WIDTH = 16, NODE_WIDTH = 9, LIMIT_DEPTH = 10, VCF_DEPTH = 11;
    private static final int TT_MASK = (1 << 16) - 1;
    private static final int FLAG_ALPHA = 1, FLAG_BETA = 2, FLAG_PV = 3;
    private static final int NEIGHBOR_RADIUS = 2;

    private static final int[] POW3 = new int[WINDOW];
    private static final byte[] LEVEL = new byte[TABLE_SIZE];

    static {
        POW3[0] = 1;
        for (int i = 1; i < WINDOW; i++) POW3[i] = POW3[i - 1] * 3;
        int[] cells = new int[WINDOW];
        // 高等级棋型是「再落一子后能形成什么」的递推结果，所以按己方子数从多到少填表。
        for (int stones = WINDOW; stones >= 1; stones--) {
            for (int idx = 0; idx < TABLE_SIZE; idx++) {
                decode(idx, cells);
                if (cells[HALF] != 1) continue;
                int count = 0;
                for (int cell : cells) if (cell == 1) count++;
                if (count != stones) continue;
                LEVEL[idx] = (byte) classify(idx, cells);
            }
        }
    }

    private static void decode(int idx, int[] cells) {
        for (int i = 0; i < WINDOW; i++) { cells[i] = idx % 3; idx /= 3; }
    }

    /** 窗口中是否存在一条包含中心点的连五。 */
    private static boolean isFive(int[] cells) {
        for (int start = 0; start <= HALF; start++) {
            boolean five = true;
            for (int i = start; i < start + 5; i++) if (cells[i] != 1) { five = false; break; }
            if (five) return true;
        }
        return false;
    }

    private static int classify(int idx, int[] cells) {
        if (isFive(cells)) return L_FIVE;
        int fivePoints = 0;
        for (int i = 0; i < WINDOW; i++) if (cells[i] == 0 && LEVEL[idx + POW3[i]] == L_FIVE) fivePoints++;
        // 两个成五点即活四：对手无法同时封堵。
        if (fivePoints >= 2) return L_OPEN_FOUR;
        if (fivePoints == 1) return L_FOUR;
        boolean openThree = false, closedThree = false;
        for (int i = 0; i < WINDOW; i++) {
            if (cells[i] != 0) continue;
            int level = LEVEL[idx + POW3[i]];
            if (level == L_OPEN_FOUR) openThree = true;
            else if (level == L_FOUR) closedThree = true;
        }
        if (openThree) return L_OPEN_THREE;
        if (closedThree) return L_CLOSED_THREE;
        boolean openTwo = false, closedTwo = false;
        for (int i = 0; i < WINDOW; i++) {
            if (cells[i] != 0) continue;
            int level = LEVEL[idx + POW3[i]];
            if (level == L_OPEN_THREE) openTwo = true;
            else if (level == L_CLOSED_THREE) closedTwo = true;
        }
        if (openTwo) return L_OPEN_TWO;
        if (closedTwo) return L_CLOSED_TWO;
        return L_ONE;
    }

    private static final long[] ZOBRIST_BLACK = new long[512], ZOBRIST_WHITE = new long[512];
    private static final long ZOBRIST_SIDE;

    static {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 512; i++) { ZOBRIST_BLACK[i] = random.nextLong(); ZOBRIST_WHITE[i] = random.nextLong(); }
        ZOBRIST_SIDE = random.nextLong();
    }

    private final int[][] board;
    private final int rows, cols, size;
    private final long budgetNanos;
    private final int depthLimit;
    private final float blunderChance;
    private final boolean allowVcf;
    private final long[] ttKey = new long[TT_MASK + 1];
    private final int[] ttData = new int[TT_MASK + 1];
    private final int[] ttMove = new int[TT_MASK + 1];
    private final int[] history;
    private final int[] neighbors;
    private final int[][] killers = new int[LIMIT_DEPTH + 2][2];
    private final int[][] moveBuffer = new int[LIMIT_DEPTH + 2][ROOT_WIDTH];
    private final int[][] scoreBuffer = new int[LIMIT_DEPTH + 2][ROOT_WIDTH];
    /** {@link #scan} 的输出缓冲，只在一次 scan 调用与紧随其后的取用之间有效。 */
    private final int[] scanPoints, scanScores;
    /** 根节点每个着法的得分，供难度降级时挑选次优着法。 */
    private final int[] rootPoints, rootScores;
    private int rootCount;
    private int scanCount, scanMyFives, scanTheirFives, scanMyOpenFours, scanTheirOpenFours, scanTheirFours;
    private long zobrist, deadline;
    private int ply, rootMove = -1;
    private boolean aborted;

    private GomokuAi(int[][] board, AiDifficulty difficulty) {
        this.board = board;
        this.rows = board.length;
        this.cols = board[0].length;
        this.size = rows * cols;
        this.budgetNanos = difficulty.budgetNanos();
        this.depthLimit = Math.min(LIMIT_DEPTH, difficulty.maxDepth());
        this.blunderChance = difficulty.blunderChance();
        // 算杀过于凌厉，简单难度下关闭，否则新手几乎不可能赢。
        this.allowVcf = difficulty != AiDifficulty.EASY;
        this.history = new int[size];
        this.neighbors = new int[size];
        this.scanPoints = new int[size];
        this.scanScores = new int[size];
        this.rootPoints = new int[ROOT_WIDTH];
        this.rootScores = new int[ROOT_WIDTH];
        for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) if (board[y][x] != 0) markNeighbors(x, y, 1);
    }

    /** 为 {@code ai} 方（1 黑 2 白）选择落点，棋盘已满返回 {@code null}。 */
    public static Move chooseMove(int[][] board, int ai) {
        return chooseMove(board, ai, AiDifficulty.HARD);
    }

    public static Move chooseMove(int[][] board, int ai, AiDifficulty difficulty) {
        int[][] working = new int[board.length][];
        for (int y = 0; y < board.length; y++) working[y] = board[y].clone();
        GomokuAi engine = new GomokuAi(working, difficulty);
        int point = engine.search(ai);
        int cols = board[0].length;
        if (point >= 0 && board[point / cols][point % cols] == 0) return new Move(point % cols, point / cols, ai);
        for (int y = 0; y < board.length; y++) for (int x = 0; x < cols; x++) if (board[y][x] == 0) return new Move(x, y, ai);
        return null;
    }

    private int search(int ai) {
        deadline = System.nanoTime() + budgetNanos;
        zobrist = computeZobrist(ai);
        int opponent = other(ai);

        boolean empty = true;
        for (int i = 0; i < size && empty; i++) if (neighbors[i] != 0) empty = false;
        if (empty) return (rows / 2) * cols + cols / 2;

        scan(ai, opponent);
        // 自己能成五直接落子；对手能成五必须封堵；自己能成活四同样是必胜手。
        // 这三项即使在简单难度下也不放弃，否则 AI 会显得完全不懂规则。
        int immediate = firstWithLevel(ai, L_FIVE);
        if (immediate >= 0) return immediate;
        immediate = firstWithLevel(opponent, L_FIVE);
        if (immediate >= 0) return immediate;
        immediate = firstWithLevel(ai, L_OPEN_FOUR);
        if (immediate >= 0) return immediate;

        if (allowVcf) {
            int forced = vcf(ai, VCF_DEPTH);
            if (forced >= 0) return forced;
        }

        int best = -1;
        for (int depth = 2; depth <= depthLimit; depth++) {
            rootMove = best;
            int score = searchRoot(ai, depth);
            if (aborted || rootMove < 0) break;
            best = rootMove;
            if (score >= WIN - LIMIT_DEPTH || score <= -WIN + LIMIT_DEPTH) break;
            if (System.nanoTime() > deadline) break;
        }
        if (best >= 0) return applyBlunder(best);
        int count = generate(ai, 0, ROOT_WIDTH, -1);
        return count > 0 ? moveBuffer[0][0] : firstEmpty();
    }

    /**
     * 低难度下按概率放弃最优落点，改选分值第 2 或第 3 的落点。
     * 相比完全随机落子，这更像人类漏看了某个威胁。
     */
    private int applyBlunder(int best) {
        if (blunderChance <= 0 || rootCount < 2) return best;
        if (ThreadLocalRandom.current().nextFloat() >= blunderChance) return best;
        int second = -1, secondScore = Integer.MIN_VALUE, third = -1, thirdScore = Integer.MIN_VALUE;
        for (int i = 0; i < rootCount; i++) {
            if (rootPoints[i] == best) continue;
            if (rootScores[i] > secondScore) {
                third = second; thirdScore = secondScore;
                second = rootPoints[i]; secondScore = rootScores[i];
            } else if (rootScores[i] > thirdScore) {
                third = rootPoints[i]; thirdScore = rootScores[i];
            }
        }
        if (second < 0) return best;
        // 已知必败的落点不选，失误也该有底线。
        if (secondScore <= -WIN + LIMIT_DEPTH) return best;
        if (third >= 0 && thirdScore > -WIN + LIMIT_DEPTH && ThreadLocalRandom.current().nextBoolean()) return third;
        return second;
    }

    private int searchRoot(int ai, int depth) {
        int opponent = other(ai);
        int count = generate(ai, 0, ROOT_WIDTH, rootMove);
        if (count == 0) { rootMove = -1; return 0; }
        int bestScore = -WIN, bestMove = -1, tiedCount = 0;
        int[] tied = new int[count];
        rootCount = 0;
        for (int i = 0; i < count; i++) {
            int point = pick(0, count, i);
            int score;
            if (levelAt(point, ai) == L_FIVE) {
                score = WIN;
            } else {
                place(point, ai);
                ply = 1;
                if (bestMove < 0) score = -searchFull(opponent, depth - 1, -WIN, -bestScore);
                else {
                    score = -searchFull(opponent, depth - 1, -bestScore - 1, -bestScore);
                    if (!aborted && score > bestScore) score = -searchFull(opponent, depth - 1, -WIN, -bestScore);
                }
                ply = 0;
                remove(point, ai);
            }
            if (aborted) break;
            if (rootCount < rootPoints.length) { rootPoints[rootCount] = point; rootScores[rootCount] = score; rootCount++; }
            if (score > bestScore) {
                bestScore = score;
                bestMove = point;
                tiedCount = 0;
                tied[tiedCount++] = point;
            } else if (score == bestScore && tiedCount < tied.length) {
                tied[tiedCount++] = point;
            }
        }
        if (bestMove < 0) rootMove = -1;
        else if (!aborted) rootMove = tiedCount > 1 ? tied[ThreadLocalRandom.current().nextInt(tiedCount)] : bestMove;
        return bestScore;
    }

    private int searchFull(int mover, int depth, int alpha, int beta) {
        if (checkTime()) return 0;
        if (depth <= 0 || ply >= depthLimit || ply >= LIMIT_DEPTH) return evaluate(mover);

        int index = (int) (zobrist & TT_MASK);
        int hashMove = -1;
        if (ttKey[index] == zobrist) {
            hashMove = ttMove[index];
            int data = ttData[index];
            int storedDepth = data & 0xff, flag = (data >>> 8) & 0x3, value = data >> 10;
            if (storedDepth >= depth) {
                if (flag == FLAG_PV) return value;
                if (flag == FLAG_BETA && value >= beta) return value;
                if (flag == FLAG_ALPHA && value <= alpha) return value;
            }
        }

        int opponent = other(mover);
        int count = generate(mover, ply, NODE_WIDTH, hashMove);
        if (count == 0) return evaluate(mover);
        int bestScore = -WIN, bestMove = -1, originalAlpha = alpha;
        for (int i = 0; i < count; i++) {
            int point = pick(ply, count, i);
            int score;
            if (levelAt(point, mover) == L_FIVE) {
                score = WIN - ply;
            } else {
                place(point, mover);
                ply++;
                if (bestMove < 0) score = -searchFull(opponent, depth - 1, -beta, -alpha);
                else {
                    score = -searchFull(opponent, depth - 1, -alpha - 1, -alpha);
                    if (!aborted && score > alpha && score < beta) score = -searchFull(opponent, depth - 1, -beta, -alpha);
                }
                ply--;
                remove(point, mover);
            }
            if (aborted) return 0;
            if (score > bestScore) {
                bestScore = score;
                bestMove = point;
                if (score >= beta) {
                    store(index, depth, FLAG_BETA, score, point);
                    recordBest(point, depth);
                    return score;
                }
                if (score > alpha) alpha = score;
            }
        }
        store(index, depth, alpha > originalAlpha ? FLAG_PV : FLAG_ALPHA, bestScore, bestMove);
        if (bestMove >= 0 && alpha > originalAlpha) recordBest(bestMove, depth);
        return bestScore;
    }

    // ------------------------------------------------------------------- 算杀

    /**
     * VCF（连续冲四）算杀：只走能成四的点，对手每步都被迫封堵，分支极窄，
     * 因此能在远超普通搜索的深度上找出必胜序列。返回制胜的第一手，找不到返回 -1。
     */
    private int vcf(int me, int depth) {
        if (depth <= 0 || checkTime()) return -1;
        int opponent = other(me);
        scan(me, opponent);
        if (scanMyFives > 0) return firstWithLevel(me, L_FIVE);
        // 对手也能成五时我方已无先手可言。
        if (scanTheirFives > 0) return -1;
        int[] attacks = collectAtLeast(me, L_FOUR);
        for (int attack : attacks) {
            place(attack, me);
            boolean wins = vcfBlocked(me, opponent, depth - 1);
            remove(attack, me);
            if (aborted) return -1;
            if (wins) return attack;
        }
        return -1;
    }

    /** 我方刚落下一个四，检查对手所有被迫应手是否都挡不住。 */
    private boolean vcfBlocked(int me, int opponent, int depth) {
        scan(opponent, me);
        // 对手能先成五，本条线失败。
        if (scanMyFives > 0) return false;
        int blocks = scanTheirFives;
        // 无处可挡（活四）即已获胜。
        if (blocks == 0) return false;
        if (blocks > 1) return true;
        int block = firstWithLevel(me, L_FIVE);
        if (block < 0) return false;
        place(block, opponent);
        int next = vcf(me, depth - 1);
        remove(block, opponent);
        return next >= 0;
    }

    // ------------------------------------------------------------- 着法生成

    /**
     * 单趟扫描所有「空且邻近有子」的点，记录双方最高棋型等级与组合分值，
     * 并统计各类威胁数量。生成、评估、算杀共用这一趟扫描的结果。
     */
    private void scan(int mover, int opponent) {
        scanCount = 0;
        scanMyFives = scanTheirFives = scanMyOpenFours = scanTheirOpenFours = scanTheirFours = 0;
        for (int point = 0; point < size; point++) {
            if (neighbors[point] == 0) continue;
            int x = point % cols, y = point / cols;
            if (board[y][x] != 0) continue;
            int mine = analyse(x, y, mover), theirs = analyse(x, y, opponent);
            int myLevel = mine & 0xf, myScore = mine >>> 4;
            int theirLevel = theirs & 0xf, theirScore = theirs >>> 4;
            if (myLevel == L_FIVE) scanMyFives++;
            else if (myLevel == L_OPEN_FOUR) scanMyOpenFours++;
            if (theirLevel == L_FIVE) scanTheirFives++;
            else if (theirLevel == L_OPEN_FOUR) scanTheirOpenFours++;
            else if (theirLevel == L_FOUR) scanTheirFours++;
            scanPoints[scanCount] = point | (myLevel << 16) | (theirLevel << 20);
            scanScores[scanCount] = myScore * 3 / 2 + theirScore;
            scanCount++;
        }
    }

    /**
     * 生成 {@code mover} 方的候选着法写入 {@code moveBuffer[depth]}。
     * 存在强制威胁（成五、活四、冲四）时只保留应对手段，否则按潜力排序取前 {@code limit} 个。
     */
    private int generate(int mover, int depth, int limit, int hashMove) {
        scan(mover, other(mover));
        int requiredMy = -1, requiredTheir = -1;
        boolean allowMyFour = false;
        if (scanMyFives > 0) requiredMy = L_FIVE;
        else if (scanTheirFives > 0) requiredTheir = L_FIVE;
        else if (scanMyOpenFours > 0) requiredMy = L_OPEN_FOUR;
        else if (scanTheirOpenFours > 0) { requiredTheir = L_OPEN_FOUR; allowMyFour = true; }
        else if (scanTheirFours > 0) { requiredTheir = L_FOUR; allowMyFour = true; }

        int[] out = moveBuffer[depth];
        int[] scores = scoreBuffer[depth];
        int killer1 = killers[depth][0], killer2 = killers[depth][1];
        int n = 0;
        for (int i = 0; i < scanCount && n < limit; i++) {
            int packed = scanPoints[i];
            int point = packed & 0xffff, myLevel = (packed >>> 16) & 0xf, theirLevel = (packed >>> 20) & 0xf;
            if (requiredMy >= 0 && myLevel != requiredMy) continue;
            if (requiredTheir >= 0 && theirLevel != requiredTheir && !(allowMyFour && myLevel >= L_FOUR)) continue;
            int score = scanScores[i] + history[point];
            if (point == hashMove) score += 1 << 22;
            else if (point == killer1) score += 1 << 20;
            else if (point == killer2) score += 1 << 19;
            out[n] = point;
            scores[n] = score;
            n++;
        }
        // 强制条件过严导致无着可走时退回普通候选，保证搜索不会空转。
        if (n == 0 && (requiredMy >= 0 || requiredTheir >= 0)) {
            for (int i = 0; i < scanCount && n < limit; i++) {
                out[n] = scanPoints[i] & 0xffff;
                scores[n] = scanScores[i];
                n++;
            }
        }
        return n;
    }

    private int firstWithLevel(int player, int wanted) {
        for (int point = 0; point < size; point++) {
            if (neighbors[point] == 0 || board[point / cols][point % cols] != 0) continue;
            if (levelAt(point, player) == wanted) return point;
        }
        return -1;
    }

    private int[] collectAtLeast(int player, int minLevel) {
        int[] buffer = new int[scanCount + 1];
        int n = 0;
        for (int point = 0; point < size; point++) {
            if (neighbors[point] == 0 || board[point / cols][point % cols] != 0) continue;
            if (levelAt(point, player) >= minLevel && n < buffer.length) buffer[n++] = point;
        }
        int[] result = new int[n];
        System.arraycopy(buffer, 0, result, 0, n);
        return result;
    }

    // --------------------------------------------------------------- 局面评估

    /**
     * 以已落子的棋型分之差衡量局面，行棋方因握有先手而加权。
     * 只在每条连子的起点计分，避免同一条线被重复累加。
     */
    private int evaluate(int mover) {
        int mine = 0, theirs = 0;
        for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) {
            int player = board[y][x];
            if (player == 0) continue;
            int total = 0;
            for (int[] d : DIRECTIONS) {
                int px = x - d[0], py = y - d[1];
                if (inBounds(px, py) && board[py][px] == player) continue;
                total += LEVEL_SCORE[lineLevel(x, y, player, d[0], d[1])];
            }
            if (player == mover) mine += total; else theirs += total;
        }
        return mine * 3 / 2 - theirs;
    }

    /** 该点对 {@code player} 的最高单方向等级。 */
    private int levelAt(int point, int player) {
        int x = point % cols, y = point / cols, best = L_NONE;
        for (int[] d : DIRECTIONS) {
            int level = lineLevel(x, y, player, d[0], d[1]);
            if (level == L_FIVE) return L_FIVE;
            if (level > best) best = level;
        }
        return best;
    }

    /** 同时算出该点的最高等级与组合分值，低 4 位为等级，其余为分值。 */
    private int analyse(int x, int y, int player) {
        int best = L_NONE, fours = 0, openFours = 0, openThrees = 0, sum = 0;
        for (int[] d : DIRECTIONS) {
            int level = lineLevel(x, y, player, d[0], d[1]);
            if (level == L_FIVE) return (SCORE_FIVE << 4) | L_FIVE;
            if (level > best) best = level;
            if (level == L_OPEN_FOUR) { openFours++; fours++; }
            else if (level == L_FOUR) fours++;
            else if (level == L_OPEN_THREE) openThrees++;
            sum += LEVEL_SCORE[level];
        }
        int score;
        if (openFours > 0) score = SCORE_OPEN_FOUR;
        else if (fours >= 2) score = SCORE_DOUBLE_FOUR;
        else if (fours == 1 && openThrees >= 1) score = SCORE_FOUR_THREE;
        else if (openThrees >= 2) score = SCORE_DOUBLE_THREE;
        else score = sum;
        return (score << 4) | best;
    }

    /** 取该方向九格窗口的棋型等级，窗口中心一律视为已落 {@code player} 的子。 */
    private int lineLevel(int x, int y, int player, int dx, int dy) {
        int idx = POW3[HALF];
        for (int k = -HALF; k <= HALF; k++) {
            if (k == 0) continue;
            int nx = x + dx * k, ny = y + dy * k, code;
            if (!inBounds(nx, ny)) code = 2;
            else {
                int value = board[ny][nx];
                code = value == 0 ? 0 : value == player ? 1 : 2;
            }
            if (code != 0) idx += code * POW3[k + HALF];
        }
        return LEVEL[idx];
    }

    // --------------------------------------------------------------- 工具方法

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

    private void recordBest(int point, int depth) {
        history[point] += depth * depth;
        int[] slot = killers[Math.min(ply, LIMIT_DEPTH + 1)];
        if (slot[0] != point) { slot[1] = slot[0]; slot[0] = point; }
    }

    private void store(int index, int depth, int flag, int value, int move) {
        if (ttKey[index] == zobrist && (ttData[index] & 0xff) > depth) return;
        int clamped = Math.max(-(1 << 20), Math.min(1 << 20, value));
        ttKey[index] = zobrist;
        ttData[index] = (clamped << 10) | (flag << 8) | Math.min(depth, 0xff);
        ttMove[index] = move;
    }

    private void place(int point, int player) {
        board[point / cols][point % cols] = player;
        zobrist ^= (player == 1 ? ZOBRIST_BLACK : ZOBRIST_WHITE)[point] ^ ZOBRIST_SIDE;
        markNeighbors(point % cols, point / cols, 1);
    }

    private void remove(int point, int player) {
        board[point / cols][point % cols] = 0;
        zobrist ^= (player == 1 ? ZOBRIST_BLACK : ZOBRIST_WHITE)[point] ^ ZOBRIST_SIDE;
        markNeighbors(point % cols, point / cols, -1);
    }

    /** 维护「附近有子」计数，使候选点筛选无需每次做邻域扫描。 */
    private void markNeighbors(int x, int y, int delta) {
        for (int dy = -NEIGHBOR_RADIUS; dy <= NEIGHBOR_RADIUS; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= rows) continue;
            for (int dx = -NEIGHBOR_RADIUS; dx <= NEIGHBOR_RADIUS; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= cols || (dx == 0 && dy == 0)) continue;
                neighbors[ny * cols + nx] += delta;
            }
        }
    }

    private long computeZobrist(int mover) {
        long key = mover == 1 ? 0L : ZOBRIST_SIDE;
        for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) {
            int value = board[y][x];
            if (value != 0) key ^= (value == 1 ? ZOBRIST_BLACK : ZOBRIST_WHITE)[y * cols + x];
        }
        return key;
    }

    private boolean checkTime() {
        if (aborted) return true;
        if (System.nanoTime() > deadline) aborted = true;
        return aborted;
    }

    private int firstEmpty() {
        for (int point = 0; point < size; point++) if (board[point / cols][point % cols] == 0) return point;
        return -1;
    }

    private boolean inBounds(int x, int y) { return x >= 0 && x < cols && y >= 0 && y < rows; }
    private static int other(int player) { return player == 1 ? 2 : 1; }
}
