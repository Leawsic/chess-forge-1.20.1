package site.leawsic.chess.config;

/**
 * AI 难度档位。
 *
 * <p>四档参数各自负责一个方面：搜索时间预算、搜索深度上限、失误率、进攻系数。
 * 失误率让低档 AI 有概率不选最优着法，而不是单纯把搜索削弱到愚蠢 ——
 * 弱化搜索只会让 AI 下出莫名其妙的棋，而随机降级选点更像人类的漏看。
 */
public enum AiDifficulty {
    /** 简单：浅搜索、频繁漏看、偏保守，适合新手。 */
    EASY(0, "easy", 120_000_000L, 2, 0.45F, 1.2F),
    /** 普通：中等搜索，偶尔失误，攻守平衡偏攻。 */
    NORMAL(1, "normal", 450_000_000L, 6, 0.12F, 1.8F),
    /** 困难：全力搜索，不主动失误，强攻。 */
    HARD(2, "hard", 1_200_000_000L, 64, 0.0F, 2.6F);

    private static final AiDifficulty[] VALUES = values();

    private final int id;
    private final String key;
    private final long budgetNanos;
    private final int maxDepth;
    private final float blunderChance;
    private final float attackBias;

    AiDifficulty(int id, String key, long budgetNanos, int maxDepth, float blunderChance, float attackBias) {
        this.id = id;
        this.key = key;
        this.budgetNanos = budgetNanos;
        this.maxDepth = maxDepth;
        this.blunderChance = blunderChance;
        this.attackBias = attackBias;
    }

    public int id() { return id; }
    /** 搜索时间预算（纳秒）。 */
    public long budgetNanos() { return budgetNanos; }
    /** 搜索深度上限，引擎自身的上限更小时以引擎为准。 */
    public int maxDepth() { return maxDepth; }
    /** 放弃最优着法、改选次优着法的概率。 */
    public float blunderChance() { return blunderChance; }
    /**
     * 进攻系数：自己的威胁分相对对手威胁分的权重。
     * 大于 1 表示宁可抢攻也不贴着对手防守，1 为完全对等。
     * 这个系数只影响没有强制威胁时的选点，成五、活四、冲四该防的仍然会防。
     */
    public float attackBias() { return attackBias; }
    /** GUI 按钮与语言文件使用的翻译键。 */
    public String translationKey() { return "gui.chess.difficulty." + key; }

    public static AiDifficulty byId(int id) {
        for (AiDifficulty value : VALUES) if (value.id == id) return value;
        return NORMAL;
    }

    /** 按 EASY → NORMAL → HARD → EASY 循环，供单个 GUI 按钮切换使用。 */
    public AiDifficulty next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
}
