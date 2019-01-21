package app;

import algorithm.STLOpDetection;
import bean.Cell;
import bean.GPS;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import hbase.TrajectoryUtil;
import org.apache.commons.math3.stat.StatUtils;
import util.CommonUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spatial-Temporal Laws
 */
public class STLOp {

    @Parameter(names = {"--city", "-c"}, description = "Which city to be counted (SH/SZ/CD).", required = true, validateWith = CommonUtil.CityValidator.class)
    private String city;
    @Parameter(names = {"--debug", "-d"}, description = "Debug mode.")
    private Boolean debug = false;
    //    @Parameter(names = {"--degree", "-deg"}, description = "Model complexity.")
//    private int degree = 3;
    @Parameter(names = {"-t"}, description = "Threshold of size.")
    private double threshold = 0.8;
//    @Parameter(names = {"-tD"}, description = "Threshold of distance.")
//    private double thresholdDist = 0.7;

    @Parameter(names = {"-s"}, description = "Start cell.", validateWith = CommonUtil.CellValidator.class)
    private String startCell = "[109776,53554]";
    @Parameter(names = {"-e"}, description = "End cell.", validateWith = CommonUtil.CellValidator.class)
    private String endCell = "[109873,53574]";

    public static void main(String... argv) {
        STLOp main = new STLOp();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(argv);
        main.run();
    }

    private void run() {

        Cell startCell = new Cell(this.startCell);
        Cell endCell = new Cell(this.endCell);

        // Origin trajectory.
        Map<String, List<GPS>> trajectoryGPS = TrajectoryUtil.getAllTrajectoryGPSs(startCell, endCell, city);
        System.out.println("trajectoryGPS size: " + trajectoryGPS.size());
        // Trajectory info.
        Map<String, double[]> trajectoryInfoMap = Maps.transformValues(trajectoryGPS, s -> CommonUtil.trajectoryInfo(s, city));

        // Generate training set.
        double[] distArray = trajectoryInfoMap.values().stream().mapToDouble(s -> s[0]).toArray();
        double[] timeArray = trajectoryInfoMap.values().stream().mapToDouble(s -> s[1]).toArray();

        double distance60 = StatUtils.percentile(distArray, 50);
        double time60 = StatUtils.percentile(timeArray, 50);
        double distance95 = StatUtils.percentile(distArray, 90);
        double time95 = StatUtils.percentile(timeArray, 90);
//        System.out.println(distance95 + " " + time95);
        Map<String, List<GPS>> trainTrajectory = Maps.filterKeys(trajectoryGPS, Maps.filterValues(trajectoryInfoMap, s -> s[0] < distance60 || s[1] < time60)::containsKey);
        System.out.println("Train set size:" + trainTrajectory.size());

        // Anomaly Label.
        Set<String> anomalyTrajectory = CommonUtil.anomalyTrajectory(trajectoryGPS, city, debug);
//        Map<String, double[]> tmp2 = Maps.filterValues(trajectoryInfoMap, s -> s[0] > distance95 && s[1] > time95);
//        tmp2.forEach((x, y) -> System.out.println(x + " " + Arrays.toString(y)));
//        Set<String> anomalyTrajectory = tmp2.keySet();


        // Detection
        long start = System.currentTimeMillis();
        STLOpDetection stl = new STLOpDetection();
        Map<String, Double> anomalyScore = trajectoryGPS.entrySet().parallelStream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            Map<String, List<GPS>> tmp = new HashMap<>(trainTrajectory);
            tmp.remove(e.getKey());
            return stl.detect(new ArrayList<>(tmp.values()), e.getValue());
        }));
        long end = System.currentTimeMillis();

        List<Map.Entry<String, Double>> anomalyScoreSorted = anomalyScore.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).collect(Collectors.toList());
        Set<String> STLAnomaly = anomalyScoreSorted.stream().limit((int) (anomalyScoreSorted.size() * threshold)).map(Map.Entry::getKey).collect(Collectors.toSet());

//        Map<String, Integer> anomalyRank = new HashMap<>();
//        for (int i = 0; i < anomalyScoreSorted.size(); i++) {
//            anomalyRank.put(anomalyScoreSorted.get(i).getKey(), i + 1);
//        }
//        for (String id : anomalyTrajectory) {
//            System.out.println(id + " " + anomalyRank.get(id));
//        }

        // Evaluation.
        int TP = Sets.intersection(anomalyTrajectory, STLAnomaly).size();
        int FP = Sets.intersection(Sets.difference(trajectoryGPS.keySet(), anomalyTrajectory), STLAnomaly).size();
        int FN = anomalyTrajectory.size() - TP;
        int TN = trajectoryGPS.size() - anomalyTrajectory.size() - FP;

        CommonUtil.printResult(TP, FP, FN, TN);

        System.out.println("Pre Time: " + (end - start) * 1.0 / trajectoryGPS.size() / 1000);
    }

}
