import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ComplexGCTest2 {
    private static final Logger logger = Logger.getLogger(ComplexGCTest2.class.getName());

    private static final int NUM_THREADS = 8; // 实现线程并发
    private static final int TEST_DURATION_MS = 60000; // 长时间运行

    // 随机分配和释放内存对象，内存对象的大小在 512KB 到 5MB 之间变化。
    private static final int MIN_OBJECT_SIZE = 512 * 1024;  // 512KB
    private static final int MAX_OBJECT_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Random RANDOM = new Random();

    // 用于存储已分配对象的线程安全列表和已分配内存的原子计数器
    private static final List<byte[]> LIST = new ArrayList<>();
    private static final AtomicInteger ALLOCATED_MEMORY = new AtomicInteger();

    public static void main(String[] args) {
        // 通过 JVM 的 MemoryMXBean 实时监控和打印堆内存的使用情况，帮助分析 GC 的内存管理策略。
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // 创建线程以模拟并发内存分配
        for (int t = 0; t < NUM_THREADS; t++) {
            new Thread(() -> {
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < TEST_DURATION_MS) {
                    allocateOrDeallocateMemory();
                    simulateWork();

                    // 定期打印内存使用情况
                    if (RANDOM.nextInt(100) < 5) { // 5% 几率打印内存使用情况
                        printMemoryUsage(memoryMXBean);
                    }
                }
            }).start();
        }

        try {
            Thread.sleep(TEST_DURATION_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void allocateOrDeallocateMemory() {
        if (RANDOM.nextBoolean()) {
            int objectSize = MIN_OBJECT_SIZE + RANDOM.nextInt(MAX_OBJECT_SIZE - MIN_OBJECT_SIZE);
            byte[] array = new byte[objectSize];
            synchronized (LIST) {
                LIST.add(array);
                ALLOCATED_MEMORY.addAndGet(objectSize);
            }
            logger.log(Level.FINE, "Allocated: {0}KB, Total allocated: {1}KB", new Object[]{objectSize / 1024, ALLOCATED_MEMORY.get() / 1024});
        } else {
            synchronized (LIST) {
                if (!LIST.isEmpty()) {
                    int index = RANDOM.nextInt(LIST.size());
                    int size = LIST.get(index).length;
                    LIST.remove(index);
                    ALLOCATED_MEMORY.addAndGet(-size);
                    logger.log(Level.FINE, "Deallocated: {0}KB, Total allocated: {1}KB", new Object[]{size / 1024, ALLOCATED_MEMORY.get() / 1024});
                }
            }
        }
    }

    private static void simulateWork() {
        try {
            Thread.sleep(RANDOM.nextInt(10)); // 模拟工作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printMemoryUsage(MemoryMXBean memoryMXBean) {
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        logger.log(Level.INFO, "Heap Memory: {0}MB used, {1}MB max",
                new Object[]{heapMemoryUsage.getUsed() / 1024 / 1024, heapMemoryUsage.getMax() / 1024 / 1024});
    }
}

