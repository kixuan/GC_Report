/*
 * @test
 * @summary Test G1GC Old Region object details and survival statistics
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UseG1GC TestG1GCOldRegionDetails
 */

import sun.hotspot.WhiteBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class TestG1GCOldRegionDetails {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        int capacity = 1000; // LRU Cache的容量
        LRUCache<Integer, String> cache = new LRUCache<>(capacity);

        // 随机添加元素到LRU缓存
        for (int i = 0; i < 10000; i++) {
            cache.put(i, "Value" + i);
        }

        // 手动控制并发标记阶段
        wb.concurrentGCAcquireControl();
        wb.concurrentGCRunToIdle(); // 让并发GC运行到空闲状态，完成标记
        wb.concurrentGCReleaseControl();

        // 运行GC，确保数据移入Old Generation
        wb.fullGC();
        wb.youngGC();

        // 获取G1 Old Region的对象详细信息
        Object[] oldRegionDetails = wb.getG1OldRegionObjectDetails();
        int totalLiveData = 0;

        for (Object detail : oldRegionDetails) {
            if (detail instanceof int[]) {
                int[] data = (int[]) detail;
                int liveBytes = data[0];

                totalLiveData += liveBytes;

                System.out.println("Live bytes in region: " + liveBytes);
            }
        }

        System.out.println("Total live data in G1GC Old Regions: " + totalLiveData + " bytes");

        // 检查活跃数据是否符合预期
        if (totalLiveData <= 0) {
            throw new AssertionError("Expected more live data in G1GC Old Regions.");
        }

        // 触发混合GC
        wb.youngGC();
        int[] mixedGCResults = wb.getG1MixedGCInfo();

        // 分析混合GC的结果
        for (int i = 0; i < mixedGCResults.length; i++) {
            System.out.println("Mixed GC - Region " + i + " live bytes: " + mixedGCResults[i]);
        }
    }

    // 简单的LRU Cache实现
    public static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;

        public LRUCache(int capacity) {
            super(capacity, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }
}
