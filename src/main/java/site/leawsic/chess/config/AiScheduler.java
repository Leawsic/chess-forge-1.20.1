package site.leawsic.chess.config;
import java.util.concurrent.*;
public final class AiScheduler {private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"chess-ai");t.setDaemon(true);return t;});private AiScheduler(){}public static void think(Runnable task){EXECUTOR.schedule(task,ThreadLocalRandom.current().nextLong(300L,751L),TimeUnit.MILLISECONDS);}}
