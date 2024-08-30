# TencentKona-17 任务二报告

完成时间：2024/09/01

## 设计

### 1. 扩展WhiteBox API

添加获取G1GC Old Region对象存活信息的方法

```c++
WB_ENTRY(jobjectArray, WB_GetG1OldRegionObjectDetails(JNIEnv* env, jobject o)) {
    if (!UseG1GC) {
        THROW_MSG_NULL(vmSymbols::java_lang_UnsupportedOperationException(),
                       "G1 GC is not enabled");
    }

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    int numRegions = g1h->num_regions();

    jobjectArray result = env->NewObjectArray(numRegions, env->FindClass("[Ljava/lang/Object;"), NULL);
    if (result == NULL) {
        return NULL; // Out of memory error thrown
    }

    for (int i = 0; i < numRegions; i++) {
        HeapRegion* region = g1h->region_at(i);
        if (region->is_old()) {
            // 创建一个Java对象数组来存储类型和大小信息
            jintArray typeCounts = env->NewIntArray(1); // 仅存储对象的存活字节数
            if (typeCounts == NULL) {
                return NULL; // Out of memory error thrown
            }

            // 填充对象存活字节数
            jint* data = env->GetIntArrayElements(typeCounts, NULL);
            data[0] = region->live_bytes(); // 存活对象总大小
            env->ReleaseIntArrayElements(typeCounts, data, 0);

            // 将数组存入结果集
            env->SetObjectArrayElement(result, i, typeCounts);
        }
    }

    return result;
}
WB_END
```

####  解释代码

- **UseG1GC检查**：首先检查是否启用了G1GC，如果没有启用，则抛出一个`UnsupportedOperationException`。
- **G1CollectedHeap**：通过`G1CollectedHeap`实例获取所有G1GC区域的信息。
- **新建数组**：创建一个数组`result`，用于存储每个Old Region的存活对象字节数。
- **遍历所有Region**：遍历所有的G1GC区域，并为每个Old Region记录存活对象的字节数。
- **返回数组**：返回包含所有Old Region存活数据的数组。



### 2. 在`whitebox.java`中声明新的JNI方法

添加对应的`native`方法声明。

```java
public class WhiteBox {
    static {
        System.loadLibrary("whitebox");
    }

    // 获取G1 Old Region中对象的详细信息
    public native Object[] getG1OldRegionObjectDetails();
}

```

### 3. 设计测试用例

编写`jtreg`测试用例，验证G1GC的行为，并通过Old Region的存活情况理解G1GC的运行机制。

而LRU缓存是一个常见的缓存算法，淘汰最近最少使用的缓存条目。我们可以使用`LinkedHashMap`来实现LRU缓存。

```java
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

```

## 结果【待补充】
