package dataloader;

import java.io.File;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.zeroturnaround.exec.ProcessExecutor;

import com.github.ruediste.salta.jsr330.AbstractModule;
import com.github.ruediste.salta.jsr330.Salta;

import dataloader.TradeDataRepository.TradeDataPoint;
import dataloader.ga.GeneticAlgorithm;
import dataloader.ga.GeneticAlgorithm.OptimizationResult;
import dataloader.ga.GeneticAlgorithm.ParameterAccessor;
import dataloader.ga.GeneticAlgorithm.Speciem;

public class Trader {
    public static void main(String... args) throws Exception {
        new Trader().main();
    }

    @Inject
    Provider<MeanReversionAlgorithm> algoProvider;

    @Inject
    TradeDataRepository repo;

    Random random = new Random();

    private void main() throws Exception {
        Salta.createInjector(new AbstractModule() {

            @Override
            protected void configure() throws Exception {

            }
        }).injectMembers(this);

        GeneticAlgorithm<AlgoSpeciem> ga = new GeneticAlgorithm<AlgoSpeciem>();
        ga.speciemFactory = () -> new AlgoSpeciem();
        OptimizationResult<AlgoSpeciem> result = ga.run();

        // write data
        // File dataFile = File.createTempFile("plot", "data");
        // dataFile.deleteOnExit();
        File dataFile = new File("data.dat");
        try (PrintStream out = new PrintStream(dataFile)) {
            for (int i = 0; i < result.fitnesses.size(); i++) {
                double[] gen = result.fitnesses.get(i);
                for (double f : gen) {
                    out.println(i + " " + f);
                }
            }
        }

        // invoke gnuplot
        new ProcessExecutor("gnuplot", "-e", "plot \"" + dataFile.getAbsolutePath() + "\"; pause -1")
                .redirectOutput(System.out).redirectInput(System.in).execute();
    }

    private class AlgoSpeciem extends Speciem<AlgoSpeciem> {

        MeanReversionAlgorithm algo = algoProvider.get();
        int maxLong = 200;
        int maxShort = 200;
        double maxDevFactor = 5;
        private SummaryStatistics gainStat;
        private SummaryStatistics countStat;

        @Override
        public AlgoSpeciem clone() {
            AlgoSpeciem result = super.clone();
            try {
                result.algo = (MeanReversionAlgorithm) algo.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
            return result;
        }

        public AlgoSpeciem() {
            algo.longMeanLength = random.nextInt(maxLong) + 10;
            algo.shortMeanLength = random.nextInt(maxShort) + 1;
            algo.devFactor = random.nextDouble() * maxDevFactor + 0.1;
        }

        private int constrain(int value, int min, int max) {
            if (value < min)
                return min;
            if (value > max)
                return max;
            return value;
        }

        private double constrain(double value, double min, double max) {
            if (value < min)
                return min;
            if (value > max)
                return max;
            return value;
        }

        @Override
        public String toString() {
            return "(long: " + algo.longMeanLength + " short: " + algo.shortMeanLength + " devFactor: " + algo.devFactor
                    + " fitness: " + fitness + "\ngain: " + gainStat + "\ncount: " + countStat + ")";
        }

        @Override
        public List<ParameterAccessor<AlgoSpeciem>> getParameters() {

            return Arrays.asList(new ParameterAccessor<AlgoSpeciem>() {

                @Override
                public void setFrom(AlgoSpeciem other) {
                    algo.longMeanLength = other.algo.longMeanLength;
                    algo.shortMeanLength = other.algo.shortMeanLength;
                }

                @Override
                public void mutate(double distance) {
                    algo.longMeanLength = constrain(
                            (int) Math.round(algo.longMeanLength + distance * maxLong * (random.nextDouble() * 2 - 1)),
                            10, maxLong - 1);
                    algo.shortMeanLength = constrain(
                            (int) Math
                                    .round(algo.shortMeanLength + distance * maxShort * (random.nextDouble() * 2 - 1)),
                            1, maxShort - 1);
                }
            }, new ParameterAccessor<AlgoSpeciem>() {

                @Override
                public void setFrom(AlgoSpeciem other) {
                    algo.devFactor = other.algo.devFactor;
                }

                @Override
                public void mutate(double distance) {
                    algo.devFactor = constrain(algo.devFactor + distance * maxDevFactor * (random.nextDouble() * 2 - 1),
                            0.1, maxDevFactor);
                }
            });
        }

        @Override
        public void calculateFitness() {
            Instant start = Instant.parse("2014-06-01T00:00:00Z");
            Instant end = Instant.parse("2016-01-01T00:00:00Z");
            Instant time = start;
            int step = 7;
            SummaryStatistics fitnessStats = new SummaryStatistics();
            gainStat = new SummaryStatistics();
            countStat = new SummaryStatistics();
            while (time.isBefore(end)) {
                Instant tmp = time.plus(Duration.ofDays(random.nextInt(step) + 1));
                // Instant tmp = time;
                AlgoData data = evaluateAlgo(algo, tmp, tmp.plus(Duration.ofDays(28)));
                double countMean = data.countStat.getMean();
                if (countMean == 0)
                    countMean = 1;
                fitnessStats
                        .addValue(data.gainStat.getMean() / countMean - data.gainStat.getStandardDeviation() * 0.05);
                gainStat.addValue(data.gainStat.getMean());
                countStat.addValue(data.countStat.getMean());
                time = time.plus(Duration.ofDays(step));
            }
            fitness = fitnessStats.getMean();
        }

    }

    private class AlgoData {
        SummaryStatistics gainStat = new SummaryStatistics();
        SummaryStatistics countStat = new SummaryStatistics();
    }

    private AlgoData evaluateAlgo(TradingAlgorithm algo, Instant start, Instant end) {
        algo.train(start);
        algo.prepare(start);
        AlgoData data = new AlgoData();
        while (!start.isAfter(end)) {
            Instant next = start.plus(Duration.ofDays(1));
            TradeDataPoint nextPoint = repo.getDataMap().get(next);
            TradeDataPoint currentPoint = repo.getDataMap().get(start);
            if (currentPoint != null && nextPoint != null) {
                double diff = nextPoint.settle - currentPoint.settle;
                int count = algo.evaluate(start);
                data.gainStat.addValue(diff * count);
                data.countStat.addValue(Math.abs(count));
            }
            start = next;
        }
        return data;
    }
}
