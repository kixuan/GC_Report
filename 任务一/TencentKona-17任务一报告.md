# TencentKona-17 任务一报告

姓名：周炫

完成时间：2024/08/21

## 1. 编译和构建JDK

编译和构建命令：

```bash
bash configure --with-boot-jdk=/opt/jdk-17 --with-debug-level=release --with-target-bits=64 --with-jvm-variants=server
```

输出如下，JVM features显示打开Shenandoah GC等各GC：

![image-20240818104718305](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240818104718305.png)

如果要禁用某个GC则修改`--with-jvm-features`参数，如：

```bash
bash configure --with-jvm-features="-shenandoahgc"
```



## 2. 通过不同GC参数打印GC日志

任务要求：写一个测试用例，通过不同的GC参数（Serial GC，Parallel Scavenge，G1GC，ZGC，Shenandoah GC），通过打印GC日志完整的展示GC的各个阶段 (比如，统一的 –Xmx –Xms)

展示：GC暂停时间，测试完成时间，GC吞吐率

### 测试用例

```java
public class ComplexGCTest {
    private static final int OBJECT_SIZE = 1024 * 1024; // 1MB
    private static final int NUM_OBJECTS = 50000; // 创建对象数
    private static final int NUM_THREADS = 8; // 并发线程数

    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        Random random = new Random();

        // 创建线程以模拟并发内存分配
        for (int t = 0; t < NUM_THREADS; t++) {
            new Thread(() -> {
                for (int i = 0; i < NUM_OBJECTS; i++) {
                    if (random.nextBoolean()) {
                        list.add(new byte[OBJECT_SIZE]); // 分配内存
                    } else {
                        synchronized (list) {
                            if (!list.isEmpty()) {
                                list.remove(random.nextInt(list.size())); // 解除分配内存
                            }
                        }
                    }
                    if (i % 100 == 0) {
                        try {
                            Thread.sleep(10); // 模拟工作
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }).start();
        }

        try {
            Thread.sleep(60000); // 运行 60 秒，方便观察
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 脚本

编写`gc_test1.bash`脚本设置jvm不同参数（如-XMX、-XMS等），打印GC日志，并将相关指标输出到`gc_summary.txt`方便统一观察

```bash
#!/bin/bash

# Java应用程序的Jar文件
JAR_FILE="ComplexGCTest.jar"

# 统一设置JVM参数配置
XMX="-Xmx2G"
XMS="-Xms2G"

# GC参数列表
GC_OPTIONS=(
  "-XX:+UseSerialGC"
  "-XX:+UseParallelGC"
  "-XX:+UseG1GC"
  "-XX:+UseZGC"
  "-XX:+UseShenandoahGC"
)

# 输出结果目录
LOG_DIR="gc_test1"
mkdir -p $LOG_DIR

# 初始化表格文件
OUTPUT_FILE="$LOG_DIR/gc_summary.txt"
echo -e "GC Option\tTotal GC Pause Time (ms)\tTotal Elapsed Time (s)\tGC Throughput (%)" > $OUTPUT_FILE

for GC in "${GC_OPTIONS[@]}"; do
    GC_NAME=$(echo $GC | tr -d '+')
    LOG_FILE="$LOG_DIR/gc_log_${GC_NAME}.log"
    
    echo "Running with GC: $GC"
    
    # 使用 /usr/bin/time 来记录时间，并启用GC日志
    /usr/bin/time -v java $XMX $XMS $GC -XX:+PrintGCDetails -Xlog:gc* -jar $JAR_FILE > $LOG_FILE 2>&1
    
    # 提取GC日志中的暂停时间和总耗时
    PAUSE_TIME=$(grep -Eo "[0-9]+(\.[0-9]+)?ms" $LOG_FILE | sed 's/ms//' | awk '{sum+=$1} END {print sum}')
    
    # 提取以秒为单位的总耗时
    ELAPSED_TIME=$(grep "Elapsed (wall clock) time" $LOG_FILE | awk '{print $8}' | awk -F':' '{ if (NF==3) {print ($1 * 3600) + ($2 * 60) + $3} else {print ($1 * 60) + $2} }')
    
    # 调试输出
    echo "Debug: PAUSE_TIME='$PAUSE_TIME', ELAPSED_TIME='$ELAPSED_TIME'"
    
    # 验证PAUSE_TIME和ELAPSED_TIME是否有效
    if [[ -z "$PAUSE_TIME" || "$PAUSE_TIME" == "0" ]]; then 
        echo "Error: Invalid PAUSE_TIME value. Exiting."
        exit 1
    fi
    if [[ -z "$ELAPSED_TIME" || "$ELAPSED_TIME" == "0" ]]; then 
        echo "Error: Invalid ELAPSED_TIME value. Exiting."
        exit 1
    fi
    
    # 计算吞吐率
    THROUGHPUT=$(echo "scale=2; 100 * (1 - ($PAUSE_TIME / ($ELAPSED_TIME * 1000)))" | bc)

    # 将统计数据写入文件
    echo -e "$GC\t$PAUSE_TIME\t$ELAPSED_TIME\t$THROUGHPUT" >> $OUTPUT_FILE

    echo "Finished running with GC: $GC"
done

echo "Results saved to $OUTPUT_FILE"

```

### 结果

因篇幅原因只展示各GC日志最后部分，具体日志文件见附件文件夹`gc_test1`，总结输出结果`gc_summary.txt`，命令类似`java -Xmx2G -Xms2G -XX:+UseG1GC -XX:+PrintGCDetails -Xlog:gc* -jar ComplexGCTest.jar`

#### 1. **Serial GC**

![image-20240819001624350](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240819001624350.png)

```bash
[1.495s][info   ][gc,start    ] GC(15) Pause Young (Allocation Failure)
[1.495s][info   ][gc,heap     ] GC(15) DefNew: 567252K(629120K)->0K(629120K) Eden: 559060K(559232K)->0K(559232K) From: 8192K(69888K)->0K(69888K)
[1.495s][info   ][gc,heap     ] GC(15) Tenured: 153128K(1398144K)->153128K(1398144K)
[1.495s][info   ][gc,metaspace] GC(15) Metaspace: 211K(448K)->211K(448K) NonClass: 202K(320K)->202K(320K) Class: 9K(128K)->9K(128K)
[1.495s][info   ][gc          ] GC(15) Pause Young (Allocation Failure) 703M->149M(1979M) 0.254ms
[1.495s][info   ][gc,cpu      ] GC(15) User=0.00s Sys=0.00s Real=0.00s
```

分析日志：

日志显示年轻代的内存回收过程，由于内存分配失败而触发，导致Eden区的对象被移动到Survivor区或直接清理。

**GC开始**：`[1.495s]` 此次GC由于“Allocation Failure”（内存分配失败）触发。

**年轻代内存变化**：

- 年轻代内存从 `567252K` -> `0K`，即所有年轻代内存都被清理。
- Eden区内存从 `559060K`（接近满载）减少到 `0K`（完全清空），说明所有的Eden区对象都被清理或移动。
- From区内存从 `8192K` 变为 `0K`，表明From区中的对象全部清理或移动。

**老年代内存变化**：老年代内存保持不变，`153128K`，这说明没有对象被晋升到老年代。

**元空间变化**：元空间没有显著变化，保持在 `211K`，表明没有新的类加载或卸载活动发生。

**GC结束**：总堆内存从 `703M` 减少到 `149M`；GC耗时 `0.254ms`。

**CPU使用情况**：本次GC的CPU时间几乎为零，`User=0.00s`，`Sys=0.00s`，`Real=0.00s`，表示这个GC非常短暂，几乎没有对系统性能产生显著影响。

#### 2. **Parallel GC**

![image-20240819001702057](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240819001702057.png)

分析日志：

```bash
[1.787s][info   ][gc,start    ] GC分析(21) Pause Young (Allocation Failure)
[1.829s][info   ][gc,heap     ] GC(21) PSYoungGen: 557264K(558080K)->140290K(486400K) Eden: 417998K(418304K)->0K(345600K) From: 139266K(139776K)->140290K(140800K)
[1.829s][info   ][gc,heap     ] GC(21) ParOldGen: 308844K(1398272K)->402030K(1398272K)
[1.829s][info   ][gc,metaspace] GC(21) Metaspace: 211K(448K)->211K(448K) NonClass: 202K(320K)->202K(320K) Class: 9K(128K)->9K(128K)
[1.829s][info   ][gc          ] GC(21) Pause Young (Allocation Failure) 845M->529M(1840M) 42.799ms
[1.829s][info   ][gc,cpu      ] GC(21) User=0.08s Sys=0.08s Real=0.04s
```

日志显示年轻代的内存回收过程，由于内存分配失败而触发，导致Eden区的对象被移动到Survivor区或老年代。老年代的内存有所增加，说明有一些长期存活的对象被移入。

**GC开始**：`[1.787s]` 此次GC由于“Allocation Failure”（内存分配失败）触发。

**年轻代内存变化**：

- 年轻代内存从 `557264K` -> `140290K`。
- Eden区内存从 `417998K`（接近满载）减少到 `0K`（完全清空），说明所有的Eden区对象都被清理或移动到Survivor区/老年代。
- From区内存从 `139266K` 增加到 `140290K`。说明GC后一些对象从Eden区被移动到From区。

**老年代内存变化**：老年代内存从 `308844K` 增加到 `402030K`。这说明一些存活下来的对象被移到了老年代，这可能是由于对象的生命周期较长或Eden区已经满了，导致其移动到老年代。

**元空间变化**：元空间没有显著变化，都是211k，说明类加载/卸载活动较少，或者是在这次年轻代GC（Pause Young）中，GC主要关注的是堆内存回收，FullGC才会回收元空间。

**GC结束**：总堆内存从 `845M` 减少到 `529M`；GC耗时 `42.799ms`。

**CPU使用情况**：主要关注Real时间 `0.04s`， 表示实际经过的墙上时间（即用户感受到的暂停时间），说明GC活动是并行的或部分并行的，因为实际暂停时间比用户和系统时间的总和要短。

#### 3. **G1 GC** 

![image-20240819001740566](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240819001740566.png)

分析第一次GC(0)

```bash
[0.221s][info   ][gc,start    ] GC(0) Pause Young (Concurrent Start) (G1 Humongous Allocation)
[0.222s][info   ][gc,task     ] GC(0) Using 4 workers of 4 for evacuation
[0.226s][info   ][gc,phases   ] GC(0)   Pre Evacuate Collection Set: 0.2ms
[0.226s][info   ][gc,phases   ] GC(0)   Merge Heap Roots: 0.2ms
[0.226s][info   ][gc,phases   ] GC(0)   Evacuate Collection Set: 0.5ms
[0.226s][info   ][gc,phases   ] GC(0)   Post Evacuate Collection Set: 3.2ms
[0.226s][info   ][gc,phases   ] GC(0)   Other: 0.9ms
[0.226s][info   ][gc,heap     ] GC(0) Eden regions: 2->0(101)
[0.226s][info   ][gc,heap     ] GC(0) Survivor regions: 0->1(13)
[0.226s][info   ][gc,heap     ] GC(0) Old regions: 0->0
[0.226s][info   ][gc,heap     ] GC(0) Archive regions: 2->2
[0.226s][info   ][gc,heap     ] GC(0) Humongous regions: 920->62
[0.226s][info   ][gc,metaspace] GC(0) Metaspace: 156K(384K)->156K(384K) NonClass: 147K(256K)->147K(256K) Class: 8K(128K)->8K(128K)
[0.226s][info   ][gc          ] GC(0) Pause Young (Concurrent Start) (G1 Humongous Allocation) 922M->63M(2048M) 5.142ms
[0.226s][info   ][gc,cpu      ] GC(0) User=0.00s Sys=0.00s Real=0.00s
```

本次GC是一次典型的因为分配大对象(G1 Humongous Allocation)出发的年轻代GC（Pause Young），GC持续时间: `5.142ms`，GC前后堆内存使用情况: `922M -> 63M (总容量2048M)`；

GC过程中，使用了并行worker来清理年轻代和部对象区域。G1 GC的特点就是垃圾回收被分成多个阶段，每个阶段都有明确的时间记录：

**GC开始**:`[0.221s]` GC开始标志，此次GC是由于巨型对象分配（G1 Humongous Allocation）引发的年轻代暂停。

**任务分配**:`[0.222s]` 使用了4个并行worker来执行evacuation（清理/移动对象）。

**GC阶段**:

- **预清理阶段（Pre Evacuate Collection Set）**: 0.2ms
- **合并堆根阶段（Merge Heap Roots）**: 0.2ms
- **清理集合集（Evacuate Collection Set）**: 0.5ms
- **后清理阶段（Post Evacuate Collection Set）**: 3.2ms
- **其他阶段**: 0.9ms

**堆内存变化**:

- Eden区块: 2个区域 -> 0个区域（最大101个区域）
- Survivor区块: 0个区域 -> 1个区域（最大13个区域）
- Old区块: 保持不变
- Archive区块: 保持不变
- Humongous区块: 920个区域 -> 62个区域

**元空间变化**:Metaspace: 保持在156K，非类部分保持在147K，类部分保持在8K

**GC结束**:`922M -> 63M` 是这次GC后堆内存的变化，耗时 `5.142ms`。

#### 4. **ZGC**

![image-20240819001942144](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240819001942144.png)

```bash
[25.479s][info   ][gc,start    ] GC(140) Garbage Collection (Allocation Stall)
[25.479s][info   ][gc,ref      ] GC(140) Clearing All SoftReferences
[25.479s][info   ][gc,task     ] GC(140) Using 1 workers
[25.480s][info   ][gc,phases   ] GC(140) Pause Mark Start 0.034ms
[25.501s][info   ][gc,phases   ] GC(140) Concurrent Mark 21.262ms
[25.502s][info   ][gc,phases   ] GC(140) Pause Mark End 0.038ms
[25.502s][info   ][gc,phases   ] GC(140) Concurrent Mark Free 0.003ms
[25.508s][info   ][gc,phases   ] GC(140) Concurrent Process Non-Strong References 6.423ms
[25.508s][info   ][gc,phases   ] GC(140) Concurrent Reset Relocation Set 0.010ms
[25.510s][info   ][gc,phases   ] GC(140) Concurrent Select Relocation Set 1.122ms
[25.510s][info   ][gc,phases   ] GC(140) Pause Relocate Start 0.027ms
[25.653s][info   ][gc,phases   ] GC(140) Concurrent Relocate 142.663ms
[25.653s][info   ][gc,load     ] GC(140) Load: 5.01/3.32/2.10
[25.653s][info   ][gc,mmu      ] GC(140) MMU: 2ms/94.6%, 5ms/97.9%, 10ms/98.9%, 20ms/99.5%, 50ms/99.7%, 100ms/99.8%
[25.653s][info   ][gc,marking  ] GC(140) Mark: 1 stripe(s), 2 proactive flush(es), 1 terminate flush(es), 0 completion(s), 0 continuation(s) 
[25.653s][info   ][gc,marking  ] GC(140) Mark Stack Usage: 32M
[25.653s][info   ][gc,nmethod  ] GC(140) NMethods: 104 registered, 0 unregistered
[25.653s][info   ][gc,metaspace] GC(140) Metaspace: 0M used, 0M committed, 1088M reserved
[25.653s][info   ][gc,ref      ] GC(140) Soft: 7 encountered, 5 discovered, 0 enqueued
[25.653s][info   ][gc,ref      ] GC(140) Weak: 37 encountered, 28 discovered, 0 enqueued
[25.653s][info   ][gc,ref      ] GC(140) Final: 0 encountered, 0 discovered, 0 enqueued
[25.653s][info   ][gc,ref      ] GC(140) Phantom: 5 encountered, 2 discovered, 0 enqueued
[25.653s][info   ][gc,reloc    ] GC(140) Small Pages: 5 / 10M, Empty: 0M, Relocated: 0M, In-Place: 0
[25.653s][info   ][gc,reloc    ] GC(140) Medium Pages: 41 / 1312M, Empty: 1088M, Relocated: 80M, In-Place: 0
[25.653s][info   ][gc,reloc    ] GC(140) Large Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0
[25.653s][info   ][gc,reloc    ] GC(140) Forwarding Usage: 0M
[25.653s][info   ][gc,heap     ] GC(140) Min Capacity: 2048M(100%)
[25.653s][info   ][gc,heap     ] GC(140) Max Capacity: 2048M(100%)
[25.653s][info   ][gc,heap     ] GC(140) Soft Max Capacity: 2048M(100%)
[25.653s][info   ][gc,heap     ] GC(140)                Mark Start          Mark End        Relocate Start      Relocate End           High               Low         
[25.653s][info   ][gc,heap     ] GC(140)  Capacity:     2048M (100%)       2048M (100%)       2048M (100%)       2048M (100%)       2048M (100%)       2048M (100%)   
[25.653s][info   ][gc,heap     ] GC(140)      Free:      726M (35%)         534M (26%)        1558M (76%)         662M (32%)        1558M (76%)         470M (23%)    
[25.653s][info   ][gc,heap     ] GC(140)      Used:     1322M (65%)        1514M (74%)         490M (24%)        1386M (68%)        1578M (77%)         490M (24%)    
[25.653s][info   ][gc,heap     ] GC(140)      Live:         -                81M (4%)           81M (4%)           81M (4%)             -                  -          
[25.653s][info   ][gc,heap     ] GC(140) Allocated:         -               192M (9%)          256M (12%)        1287M (63%)            -                  -          
[25.653s][info   ][gc,heap     ] GC(140)   Garbage:         -              1240M (61%)         152M (7%)           16M (1%)             -                  -          
[25.653s][info   ][gc,heap     ] GC(140) Reclaimed:         -                  -              1088M (53%)        1223M (60%)            -                  -          
[25.653s][info   ][gc          ] GC(140) Garbage Collection (Allocation Stall) 1322M(65%)->1386M(68%)

```

本次GC由于内存分配阻塞（Allocation Stall）而触发的，整个过程中涉及并发标记和对象重新定位。

**GC开始**：`[25.479s]` 此次GC由于“Allocation Stall”触发，这是因为JVM无法及时分配内存导致的暂停。

**清除所有软引用（SoftReferences）**：GC尝试清除软引用，以释放更多内存资源。

**GC阶段**：

- **Pause Mark Start**：暂停标记开始，耗时 `0.034ms。`
- **Concurrent Mark**：并发标记阶段(标记哪些对象活跃)，耗时 `21.262ms`
- **Pause Mark End**：暂停标记结束，耗时 `0.038ms。`
- **Concurrent Mark Free**：并发标记清理，耗时 `0.003ms`，用于清理已标记的未使用内存。
- **Concurrent Process Non-Strong References**：并发处理非强引用，耗时 `6.423ms`。
- **Concurrent Reset Relocation Set**：并发重置重新定位集，耗时 `0.010ms`。
- **Concurrent Select Relocation Set**：并发选择重新定位集，耗时 `1.122ms`。
- **Pause Relocate Start**：暂停重新定位开始，耗时 `0.027ms`。

**Concurrent Relocate**：并发重新定位，耗时 `142.663ms`(GC过程中时间最长的阶段)。

**Load**：系统负载分别为 `5.01/3.32/2.10`。

**MMU**：列出了不同时间窗口下的最小维护单位的百分比，这些值非常高，说明系统大部分时间都处于可用状态。

**Mark Stack Usage**：标记堆栈使用量为 `32M`。

**Metaspace**：元空间在这次GC中未使用或提交内存，总共预留了 `1088M`。

**引用处理**：

- **Soft References**：处理了 `7` 个软引用，发现 `5` 个，未入队列。
- **Weak References**：处理了 `37` 个弱引用，发现 `28` 个，未入队列。
- **Phantom References**：处理了 `5` 个虚引用，发现 `2` 个，未入队列。

**内存重新定位**：

- **Small Pages**：小页面重新定位 `5` 个，共计 `10M`。
- **Medium Pages**：中等页面重新定位 `41` 个，共计 `1312M`，清空了 `1088M`。
- **Large Pages**：大页面未发生重新定位。

**堆内存状态**：

- **堆容量**：最小、最大和软最大容量均为 `2048M`。
- **内存使用情况**：GC开始时使用 `1322M`，结束时增加至 `1386M`，表示内存使用略有增加。

**GC完成**：堆内存使用从 `1322M (65%)` 增加到 `1386M (68%)`。



#### 5. **Shenandoah GC**

![image-20240819002026492](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240819002026492.png)

这是一次复杂的垃圾回收过程

**GC开始，并发重置**：GC从并发重置开始，使用了2个工作线程中的1个，耗时`4.294ms`，目的是为标记周期做准备。

**Pause Init Mark**：GC暂停进行了初始标记，使用了2个工作线程中的2个，耗时`0.144ms`，目标是标记所有可达对象，并为后续的并发标记阶段做准备。

**Concurrent Marking**：并发标记阶段使用了2个工作线程中的1个，耗时`20.077ms`，标记了所有存活的对象。

**Pause Final Mark**：在最终标记阶段GC再次暂停，使用了2个工作线程中的2个，耗时`0.841ms`，确认了在并发标记过程中未被标记的对象并将其标记为垃圾。

**Concurrent Weak References**：处理弱引用阶段耗时`0.410ms`，包括发现和排队弱引用，发现了11个弱引用

**Concurrent Cleanup**：清理未使用的对象，将堆内存从`824M`减少到`267M`，耗时`0.537ms`。

**Concurrent Class Unloading**：并发卸载未使用的类，耗时`0.281ms`，主要是为了释放元空间中的类元数据。

**Metaspace**：元空间在此次GC过程中保持不变，显示为`213K`，表明类的加载和卸载活动很少。

**GC结束**：在整个GC周期结束后，堆内存的使用情况从`824M`减少到`267M`，有效释放了大量的内存空间；内存碎片化程度较高，显示为79%外部碎片。

**Trigger**：在日志末尾，触发GC的原因是平均GC时间超过了根据平均分配率计算出的空闲内存消耗时间。系统发现内存的空闲空间不足触发了这次GC。

**性能统计**：日志详细记录了各个GC阶段的时间消耗和并行度，显示了不同阶段的耗时和工作线程的使用情况。例如，并发标记阶段耗时最多为`20.180ms`，且并行效率较高（0.62x）。



#### 6. 总结统计

![image-20240818215246804](https://raw.githubusercontent.com/kixuan/PicGo/main/image-20240818215246804.png)



## 3. 比较各个GC特点

通过**一个测试**用例，比较各个GC的特点。通过GC日志看到各个GC的不同阶段和GC暂停时间的差异

### 测试用例

设计测试用例时考虑的因素：

1. **多线程并发**: 通过 8 个线程并发执行内存分配和释放操作，在多线程并发分配下各GC的暂停时间、吞吐量和内存碎片管理；
2. **内存分配和释放**: 随机地分配和释放内存对象（512KB 到 5MB），容易触发GC不同阶段，尤其是内存话碎片严重时，G1GC会产生Full GC，Shenandoah GC 产生 Degenerated GC；
3. **模拟工作负载**：在每 100 次分配/释放后，线程会sleep(10)，模拟实际应用中线程可能的工作负载和间歇性内存操作；
4. **设置60s的运行时间**：方便观察GC在长时间内的行为演变，更容易暴露 GC 在应对持续高负载场景下的瓶颈或不足。
5. **内存使用情况监控**: 通过 JVM 的 `MemoryMXBean`  和`Logger` 实时监控和打印堆内存的使用情况，帮助分析 GC 的内存管理策略。

```java
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
        // 通过 JVM 的 MemoryMXBean 实时监控和打印堆内存情况
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
```

### 脚本

编写`gc_test2.bash`：几乎和上一个`gc_test1.bash`脚本一致，统计信息修改为Total GC Pause Time 、Max GC Pause Time 、Min GC Pause Time 、Full GC Count和Specific Events（如G1GC的To-space exhausted 、ZGC的Allocation Stall），以便更好比较不同GC的特点

### 结果及分析



![image-20240829232754345](E:/Download/PicGo/picture/image-20240829232754345.png)

#### 1. **Serial GC分析**:

- **特点**: Serial GC 是最简单 GC ，所有 GC 事件都是单线程执行，在垃圾收集过程中会暂停所有应用线程，所以在多核系统上的表现较差。

- **Total GC Pause Time: 7486.73 ms**【相对较短】总暂停时间较短，说明在这种负载下，Serial GC的性能还算不错，但受限于单线程的回收策略，GC频率较高。

  **Max GC Pause Time: 281.944 ms** 虽然在某些时刻暂停时间较长，但在这次测试中最大暂停时间并不是非常高，表明Serial GC在短时间内可以完成大部分回收工作。

  **Min GC Pause Time: 0.072 ms** 说明Serial GC在处理较小的内存回收时非常快速。

  **Full GC Count: 38** 这个次数较高，表明在压力测试中，Serial GC在分配和回收内存上可能存在瓶颈。

  **Specific Events: N/A** 脚本没有编写获取特定事件

  **适用场景**: Serial GC适合内存较小、应用负载较轻的场景，如桌面应用或小型服务。它的单线程特性使其在低并发的环境中表现尚可。

#### 2. **Parallel GC分析**:

- **特点**: Parallel GC 是一个多线程的垃圾收集器，设计目标是最大化吞吐量，通过并行处理来减少垃圾收集的时间，但同时会暂停应用程序的所有线程。这种设计适用于 CPU 密集型应用，能够充分利用多核处理器的优势。

- **Total GC Pause Time: 24174.9 ms**【最长】总暂停时间非常长，表明尽管Parallel GC设计上减少了单次暂停时间，但频繁的GC操作导致了总暂停时间过长。

  **Max GC Pause Time: 630.984 ms** 单次最大暂停时间相对较高，表明在高负载下Parallel GC的表现并不理想。

  **Min GC Pause Time: 0.021 ms** 最小暂停时间非常短，说明在处理小内存回收时，Parallel GC表现良好。

  **Full GC Count: 86**【次数最高】Full GC次数很高，表明在高并发情况下，Parallel GC的内存回收策略可能引发频繁的内存耗尽情况。

  **Specific Events: N/A** 脚本没有编写获取特定事件

  **适用场景**: Parallel GC适合高吞吐量应用，尤其是在多核处理器上。但总暂停时间过长，可能不适合对延迟敏感的应用。

#### 3. **G1 GC** 分析:

- **特点**: G1 GC 就是为了解决 Full GC 长暂停时间而设计的，通过将堆分割为多个小区 + 并发优先回收那些包含大量垃圾的区域，尽量减少停顿。

- **Total GC Pause Time: 19523.5 ms** 总暂停时间较长，但明显优于Parallel GC，说明G1 GC在处理大内存回收时较为高效。

  **Max GC Pause Time: 32.380 ms**【较低】最大暂停时间非常短，符合G1 GC低暂停时间的设计目标。

  **Min GC Pause Time: 0.0 ms** 表明某些回收操作非常迅速，甚至接近零停顿。

  **Full GC Count: 0** 没有触发Full GC，表明G1 GC成功避免了长时间的垃圾收集停顿。

  **Specific Events: 0** 没有特定事件，表明G1 GC在这次测试中表现稳定。

- **适用场景**: G1 GC 适合低延迟、高并发的应用场景，尤其是对暂停时间敏感的场景，如企业级 Java 应用服务器。

#### 4. **ZGC分析**:

- **特点**: ZGC 是一个低延迟的垃圾收集器，能够在非常大的堆内存中工作，且将暂停时间控制在 10ms 以内。ZGC 的核心设计思想是避免 Full GC，通过并发标记、并发转移和并发压缩来管理内存。

- **Total GC Pause Time: 37677 ms**【相对较长】总暂停时间虽然较长，但ZGC的目标主要是减少单次停顿而非总停顿时间。

  **Max GC Pause Time: 100 ms** 单次最大暂停时间较低，说明ZGC在处理大内存回收时表现出色。

  **Min GC Pause Time: 0.000 ms** 最小暂停时间非常短，表明ZGC在处理小内存回收时效率极高。

  **Full GC Count: 0** 没有Full GC，说明ZGC成功避免了长时间停顿。

  **Specific Events: 88**【最高】报告了许多Allocation Stall事件，表明在高负载情况下，ZGC可能会遇到内存分配的瓶颈，还需要进一步优化。

- **适用场景**: ZGC 适合对延迟非常敏感的应用，如金融交易系统或实时数据处理应用，但需要注意内存压力大的情况下可能会触发 Allocation Stall 。

#### 5. **Shenandoah GC分析**:

- **特点**: Shenandoah GC 是另一个低延迟的 GC，设计目标是将 GC 暂停时间最小化到ms级别。

- **Total GC Pause Time: 1885.93 ms**【最短】总暂停时间非常短，说明Shenandoah GC在高并发的情况下表现极佳。

  **Max GC Pause Time: 16.708 ms**【最低】单次最大暂停时间是所有GC中最短的，非常适合延迟敏感的应用。

  **Min GC Pause Time: 0.006 ms** 最小暂停时间非常短，进一步证明了Shenandoah GC的低延迟特性。

  **Full GC Count: 4** Full GC次数虽然有4次，但数量不算高，在高负载下表现尚可。

  **Specific Events: 12** 报告了一些Pacing和Degenerated GC事件，这些事件可能在高负载下影响GC性能。

- **适用场景**: Shenandoah GC 适合对响应时间要求高的应用，如在线交易系统和在线游戏服务器，尤其适合大内存的 JVM 实例。



## 总结

1. **低延迟表现**: Shenandoah GC 和 ZGC 都表现出了较好的低延迟能力，Shenandoah 在总暂停时间上表现更好一点，但在极端内存压力下需要处理 Full GC。ZGC 也会遇到了一些 Allocation Stall 事件，总体上比较平衡。
2. **高吞吐量表现**: Parallel GC 总暂停时间较高，但单次暂停时间上表现良好，适合高吞吐量任务。
3. **通用场景**: G1 GC 在各方面表现均衡，适合通用的高并发低延迟场景，如企业级 Java 应用服务器；而 Serial GC 因为高暂停时间适合低并发的小型应用。
3. 使用GCEasy等专门的GC日志分析更合适本次任务，也可以更加总览全局地观察整个GC过程。