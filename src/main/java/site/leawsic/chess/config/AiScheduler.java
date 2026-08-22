package site.leawsic.chess.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 思考调度器。
 *
 * <p>搜索本身在这些线程上执行，绝不能放到服务器主线程，否则每步棋都会造成明显卡顿。
 * 提交的任务只负责计算，结果需由调用方切回主线程再应用到棋盘。
 */
public final class AiScheduler {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chess-ai-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        // 低于普通优先级，保证搜索不会和服务器主线程抢 CPU。
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        return thread;
    });

    private AiScheduler() {}

    /** 延迟一小段随机时间后在 AI 线程上执行 {@code task}，让落子看起来像在思考。 */
    public static void think(Runnable task) {
        EXECUTOR.schedule(task, ThreadLocalRandom.current().nextLong(120L, 321L), TimeUnit.MILLISECONDS);
    }
}
