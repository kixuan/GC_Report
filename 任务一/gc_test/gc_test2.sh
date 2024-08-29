#!/bin/bash

# Java应用程序的Jar文件
JAR_FILE="ComplexGCTest2.jar"

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
LOG_DIR="gc_test3"
mkdir -p $LOG_DIR

# 初始化表格文件
OUTPUT_FILE="$LOG_DIR/gc_summary.txt"
echo -e "GC Option\tTotal GC Pause Time (ms)\tMax GC Pause Time (ms)\tMin GC Pause Time (ms)\tFull GC Count\tSpecific Events" > $OUTPUT_FILE

# 运行测试并收集日志
for GC in "${GC_OPTIONS[@]}"; do
    GC_NAME=$(echo $GC | tr -d '+' | tr -d ':')
    LOG_FILE="$LOG_DIR/gc_log_${GC_NAME}.log"
    
    echo "Running with GC: $GC"
    
    # 使用 /usr/bin/time 来记录时间，并启用GC日志
    /usr/bin/time -v java $XMX $XMS $GC -Xlog:gc*:file=$LOG_FILE:time -jar $JAR_FILE > "$LOG_DIR/${GC_NAME}_output.txt" 2>&1
    
    # 提取GC日志中的暂停时间信息
    PAUSE_TIMES=$(grep -Eo "[0-9]+(\.[0-9]+)?ms" $LOG_FILE | sed 's/ms//')
    TOTAL_PAUSE_TIME=$(echo "$PAUSE_TIMES" | awk '{sum+=$1} END {print sum}')
    MAX_PAUSE_TIME=$(echo "$PAUSE_TIMES" | awk 'BEGIN {max=0} {if ($1>max) max=$1} END {print max}')
    MIN_PAUSE_TIME=$(echo "$PAUSE_TIMES" | awk 'BEGIN {min=999999} {if ($1<min) min=$1} END {print min}')
    
    # 计算Full GC的次数
    FULL_GC_COUNT=$(grep -E -c "(Full GC|Pause Full|Full\s\(Ergonomics\))" $LOG_FILE)

    
    # 特定事件分析
    SPECIFIC_EVENTS=""
    case $GC in
        "-XX:+UseG1GC")
            SPECIFIC_EVENTS=$(grep -E "To-space exhausted|Full GC" $LOG_FILE | wc -l)
            ;;
        "-XX:+UseZGC")
            SPECIFIC_EVENTS=$(grep -c "Allocation Stall" $LOG_FILE)
            ;;
        "-XX:+UseShenandoahGC")
            SPECIFIC_EVENTS=$(grep -E "Pacing|Degenerated GC|Full GC" $LOG_FILE | wc -l)
            ;;
        *)
            SPECIFIC_EVENTS="N/A"
            ;;
    esac
    
    # 将统计数据写入文件
    echo -e "$GC\t$TOTAL_PAUSE_TIME\t$MAX_PAUSE_TIME\t$MIN_PAUSE_TIME\t$FULL_GC_COUNT\t$SPECIFIC_EVENTS" >> $OUTPUT_FILE

    echo "Finished running with GC: $GC"
done

echo "Results saved to $OUTPUT_FILE"

