import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;
import org.apache.hadoop.util.*;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.*;
import java.net.URI;
import java.util.*;

public class KMeansClustering extends Configured implements Tool {

    // Mapper Class
    public static class KMeansMapper extends Mapper<LongWritable, Text, IntWritable, Text> {
        private List<Double[]> centroids;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            URI[] cacheFiles = context.getCacheFiles();

            if (cacheFiles != null && cacheFiles.length > 0) {
                Path path = new Path(cacheFiles[0].getPath());
                Configuration conf = context.getConfiguration();
                FileSystem fs = FileSystem.get(conf);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path)));

                String line;
                centroids = new ArrayList<>();

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    String vectorPart;

                    if (line.contains("\t")) {
                        // 制表符分隔：ID<TAB>vector
                        String[] parts = line.split("\t", 2);
                        vectorPart = parts[1];
                    } else if (line.contains(":")) {
                        // id: 格式：ID: vector
                        String[] parts = line.split(":", 2);
                        vectorPart = parts[1].trim();
                    } else {
                        // 纯向量格式：vec,vec,...
                        vectorPart = line;
                    }

                    String[] values = vectorPart.split(",");
                    Double[] centroid = Arrays.stream(values)
                            .map(Double::parseDouble)
                            .toArray(Double[]::new);

                    centroids.add(centroid);
                }

                reader.close();
            }
        }

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString().trim();
            line = line.replaceAll("^[^:]+:\\s*", "").trim();  // 去除可能的 id:

            String[] sampleStr = line.split(",");
            Double[] sample = Arrays.stream(sampleStr).map(Double::parseDouble).toArray(Double[]::new);

            int closestClusterId = -1;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < centroids.size(); i++) {
                Double[] centroid = centroids.get(i);
                double distance = calculateEuclideanDistance(sample, centroid);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestClusterId = i;
                }
            }

            context.write(new IntWritable(closestClusterId), new Text(line));
        }

        private double calculateEuclideanDistance(Double[] point1, Double[] point2) {
            double sum = 0.0;
            for (int i = 0; i < point1.length; i++) {
                sum += Math.pow(point1[i] - point2[i], 2);
            }
            return Math.sqrt(sum);
        }
    }

    // Reducer Class
    public static class KMeansReducer extends Reducer<IntWritable, Text, NullWritable, Text> {
        @Override
        public void reduce(IntWritable clusterId, Iterable<Text> samples, Context context)
                throws IOException, InterruptedException {

            List<Double[]> points = new ArrayList<>();

            for (Text sample : samples) {
                String line = sample.toString().trim();
                line = line.replaceAll("^[^:]+:\\s*", "").trim();  // 去除可能的 id:

                String[] sampleStr = line.split(",");
                Double[] point = Arrays.stream(sampleStr).map(Double::parseDouble).toArray(Double[]::new);
                points.add(point);
            }

            if (!points.isEmpty()) {
                int dimension = points.get(0).length;
                Double[] newCentroid = new Double[dimension];
                Arrays.fill(newCentroid, 0.0);

                for (Double[] point : points) {
                    for (int i = 0; i < dimension; i++) {
                        newCentroid[i] += point[i];
                    }
                }

                for (int i = 0; i < dimension; i++) {
                    newCentroid[i] /= points.size();
                }

                StringBuilder centroidStr = new StringBuilder();
                for (double val : newCentroid) {
                    centroidStr.append(val).append(",");
                }

                String formattedVector = centroidStr.toString().replaceAll(",$", "");

                // 输出格式：ID<TAB>value,value,...
                String outputLine = clusterId.get() + "\t" + formattedVector;

                context.write(NullWritable.get(), new Text(outputLine));
            }
        }
    }

    // Driver Class
    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: KMeansClustering <input-data> <initial-centers> <output-dir> <max-iterations>");
            return -1;
        }

        String inputPath = args[0];
        String initialCenters = args[1];  // 初始质心文件路径
        String outputPath = args[2];      // 输出目录
        int maxIterations = Integer.parseInt(args[3]);  // 最大迭代次数

        Configuration conf = getConf();

        FileSystem fs = FileSystem.get(conf);
        Path outPath = new Path(outputPath);
        if (fs.exists(outPath)) {
            fs.delete(outPath, true); // 清空输出目录
        }

        int iteration = 0;
        Path currentCenters = new Path(initialCenters);

        while (iteration < maxIterations) {
            System.out.println("Iteration " + (iteration + 1));

            Job job = Job.getInstance(conf, "KMeans Iteration " + (iteration + 1));
            job.setJarByClass(KMeansClustering.class);

            job.setMapperClass(KMeansMapper.class);
            job.setReducerClass(KMeansReducer.class);

            // 设置 Map 阶段输出类型
            job.setMapOutputKeyClass(IntWritable.class);
            job.setMapOutputValueClass(Text.class);

            // 设置 Reduce 阶段输出类型
            job.setOutputKeyClass(NullWritable.class);
            job.setOutputValueClass(Text.class);

            job.setInputFormatClass(TextInputFormat.class);
            job.setOutputFormatClass(TextOutputFormat.class);

            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, new Path(outputPath + "/iter_" + (iteration + 1)));

            // 添加当前质心文件到分布式缓存
            job.addCacheFile(currentCenters.toUri());

            boolean success = job.waitForCompletion(true);
            if (!success) {
                System.err.println("Job failed at iteration " + (iteration + 1));
                return 1;
            }

            // 更新质心路径为本次输出
            currentCenters = new Path(outputPath + "/iter_" + (iteration + 1) + "/part-r-00000");

            iteration++;
        }

        System.out.println("KMeans clustering completed after " + iteration + " iterations.");
        return 0;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new KMeansClustering(), args);
        System.exit(exitCode);
    }
}