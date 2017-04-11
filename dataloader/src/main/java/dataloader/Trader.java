package dataloader;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import com.github.ruediste.salta.jsr330.AbstractModule;
import com.github.ruediste.salta.jsr330.Injector;
import com.github.ruediste.salta.jsr330.Salta;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;

import ch.ruediste.remoteHz.common.ICallable;
import dataloader.TradeDataRepository.TradeDataPoint;
import dataloader.ga.GeneticAlgorithm;
import dataloader.ga.GeneticAlgorithm.OptimizationResult;
import dataloader.ga.GeneticAlgorithm.ParameterAccessor;
import dataloader.ga.GeneticAlgorithm.Speciem;

public class Trader implements ICallable<OptimizationResult<Trader.AlgoSpeciem>>, HazelcastInstanceAware {

    @Inject
    Provider<MeanReversionAlgorithm> algoProvider;

    @Inject
    TradeDataRepository repo;

    @Inject
    Injector injector;

    Random random = new Random();

    private HazelcastInstance hz;

    private static class InjectorHolder {
        public static Injector injector = Salta.createInjector(new AbstractModule() {

            @Override
            protected void configure() throws Exception {

            }
        });
    }

    @Override
    public OptimizationResult<AlgoSpeciem> call() throws Exception {
        InjectorHolder.injector.injectMembers(this);
        GeneticAlgorithm<AlgoSpeciem> ga = new GeneticAlgorithm<AlgoSpeciem>();
        ga.speciemFactory = () -> new AlgoSpeciem();
        OptimizationResult<AlgoSpeciem> result = ga.run();
        return result;
    }

    public class AlgoSpeciem extends Speciem<AlgoSpeciem> {

        private static final long serialVersionUID = 1L;
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
            List<Runnable> tasks = new ArrayList<>();
            while (time.isBefore(end)) {
                Instant tmp = time.plus(Duration.ofDays(random.nextInt(step) + 1));
                // Instant tmp = time;
                MeanReversionAlgorithm algoTmp = algo;
                Future<AlgoData> dataFuture = hz.getExecutorService("default").submit((Callable & Serializable) () -> {
                    return evaluateAlgo(algoTmp, tmp, tmp.plus(Duration.ofDays(28)),
                            InjectorHolder.injector.getInstance(TradeDataRepository.class));
                });
                tasks.add(() -> {
                    try {
                        AlgoData data = dataFuture.get();
                        // AlgoData data = evaluateAlgo(algo, tmp,
                        // tmp.plus(Duration.ofDays(28)));
                        double countMean = data.countStat.getMean();
                        if (countMean == 0)
                            countMean = 1;
                        fitnessStats.addValue(
                                data.gainStat.getMean() / countMean - data.gainStat.getStandardDeviation() * 0.05);
                        gainStat.addValue(data.gainStat.getMean());
                        countStat.addValue(data.countStat.getMean());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                time = time.plus(Duration.ofDays(step));
            }
            fitness = fitnessStats.getMean();
        }

    }

    private static class AlgoData implements Serializable {
        private static final long serialVersionUID = 1L;
        SummaryStatistics gainStat = new SummaryStatistics();
        SummaryStatistics countStat = new SummaryStatistics();
    }

    private static AlgoData evaluateAlgo(TradingAlgorithm algo, Instant start, Instant end, TradeDataRepository repo) {
        algo.train(start, repo);
        algo.prepare(start, repo);
        AlgoData data = new AlgoData();
        List<Runnable> tasks = new ArrayList<>();
        while (!start.isAfter(end)) {
            Instant startFinal = start;
            Instant next = start.plus(Duration.ofDays(1));
            TradeDataPoint nextPoint = repo.getDataMap().get(next);
            TradeDataPoint currentPoint = repo.getDataMap().get(start);
            if (currentPoint != null && nextPoint != null) {
                // Future<Integer> countFuture =
                // hz.getExecutorService("default")
                // .submit(RemoteHzCallable.create(new
                // EvaluateAlgoCallable(startFinal, algo)));

                tasks.add(() -> {
                    double diff = nextPoint.settle - currentPoint.settle;
                    int count = algo.evaluate(startFinal, repo);
                    // int count;
                    // try {
                    // count = countFuture.get();
                    // } catch (Exception e) {
                    // throw new RuntimeException(e);
                    // }
                    data.gainStat.addValue(diff * count);
                    data.countStat.addValue(Math.abs(count));
                });
            }
            start = next;
        }
        tasks.forEach(Runnable::run);

        return data;
    }

    private static class EvaluateAlgoCallable implements Callable<Integer>, Serializable {

        private static final long serialVersionUID = 1L;
        private TradingAlgorithm algo;
        private Instant start;

        public EvaluateAlgoCallable(Instant start, TradingAlgorithm algo) {
            this.start = start;
            this.algo = algo;
        }

        @Inject
        TradeDataRepository repo;

        @Override
        public Integer call() throws Exception {
            InjectorHolder.injector.injectMembers(this);
            return algo.evaluate(start, repo);
        }

    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
