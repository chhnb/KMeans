# Hadoop K-means聚类算法实现

## 项目简介

本项目是基于Hadoop MapReduce框架实现的K-means聚类算法。K-means是一种常用的聚类分析算法，用于将数据集划分为K个不同的簇，使得同一簇内的数据点相似度较高，而不同簇之间的数据点相似度较低。本项目通过Hadoop分布式计算框架实现了该算法，可以处理大规模数据集。

## 数据说明

### 输入数据

输入数据位于 `/home/chh/project_hadoop_2/input/` 目录下，包含以下文件：

- `initial_centers.data`：初始质心数据，每行表示一个质心，格式为 `ID: val1, val2, ..., valN`
- `dataset.data`：完整数据集
- `dataset_mini.data`：小型测试数据集

数据格式示例：
```
0: 12, 5, 9, 6, 6, 10, 10, 10, 8, 3, 6, 11, 4, 8, 10, 5
1: 16, 19, 22, 19, 13, 21, 16, 17, 21, 17, 15, 15, 14, 17, 18, 20
...
```

每行数据表示一个16维的向量，前面的数字（如0:, 1:）表示数据点的ID。

## 算法实现

本项目使用MapReduce模型实现K-means算法：

1. **Mapper阶段**：
   - 从分布式缓存读取当前质心
   - 计算每个数据点到所有质心的欧氏距离
   - 将数据点分配给距离最近的质心
   - 输出 <质心ID, 数据点> 键值对

2. **Reducer阶段**：
   - 收集分配给同一质心的所有数据点
   - 计算这些数据点的平均值作为新的质心
   - 输出 <null, 新质心> 键值对

3. **迭代过程**：
   - 重复执行上述MapReduce过程，直到达到预设的迭代次数（本项目设为10次）
   - 每次迭代的输出作为下一次迭代的输入质心

## 文件夹结构

- `src`：源代码目录，包含 `KMeansClustering.java`
- `bin`：编译后的类文件和JAR包
- `input`：输入数据目录
- `output`：本地输出结果目录
- `lib`：依赖库目录

## 运行方法

项目提供了运行脚本 `run_kemans_with_jar.sh`，执行以下步骤：

1. 清理HDFS上的旧数据
2. 将输入数据上传到HDFS
3. 提交K-means聚类任务
4. 查看最终输出结果

运行命令：
```bash
./run_kemans_with_jar.sh
```

脚本内容：
```bash
#!/bin/bash

echo "===== 清理旧的 HDFS 数据 ====="
hdfs dfs -rm -r /kmeans_input /kmeans_output || true

echo "===== 上传输入数据到 HDFS ====="
hdfs dfs -put input /kmeans_input

echo "===== 提交 K-Means 聚类任务 ====="
hadoop jar bin/KMeansClustering.jar KMeansClustering \
    /kmeans_input/dataset.data /kmeans_input/initial_centers.data /kmeans_output 10

echo "===== 查看最终输出结果 ====="
hdfs dfs -cat /kmeans_output/iter_10/part-r-00000
```

## 输出结果

最终的聚类结果存储在HDFS的 `/kmeans_output/iter_10/part-r-00000` 文件中，格式为：
```
质心ID\tab新质心坐标（逗号分隔的值）
```

每行表示一个质心，第一列是质心ID，后面是该质心的坐标值（16维向量）。

结果目前从HDFS下载到当前文件 `output` 目录下。



