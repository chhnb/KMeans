#!/bin/bash

echo "===== 清理旧的 HDFS 数据 ====="
hdfs dfs -rm -r /kmeans_input /kmeans_output || true

echo "===== 上传输入数据到 HDFS ====="
hdfs dfs -put input /kmeans_input

echo "===== 编译 Java 源码 ====="
mkdir -p bin/
javac -cp $(hadoop classpath) -d bin/ src/KMeansClustering.java

echo "===== 打包成 JAR 文件 ====="
jar -cvf bin/KMeansClustering.jar -C bin/ .

echo "===== 提交 K-Means 聚类任务 ====="
hadoop jar bin/KMeansClustering.jar KMeansClustering \
    /kmeans_input/dataset_mini.data /kmeans_input/initial_centers.data /kmeans_output 10

echo "===== 查看最终输出结果 ====="
hdfs dfs -cat /kmeans_output/iter_10/part-r-00000