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

