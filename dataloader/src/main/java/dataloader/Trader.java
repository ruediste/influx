package dataloader;

import java.time.Duration;
import java.time.Instant;

import javax.inject.Inject;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import com.github.ruediste.salta.jsr330.AbstractModule;
import com.github.ruediste.salta.jsr330.Salta;

import dataloader.TradeDataRepository.TradeDataPoint;

public class Trader {
    public static void main(String... args) {
        new Trader().main();
    }

    @Inject
    MeanReversionAlgorithm algo;

    @Inject
    TradeDataRepository repo;

    private void main() {
        Salta.createInjector(new AbstractModule() {

            @Override
            protected void configure() throws Exception {

            }
        }).injectMembers(this);

        Instant start = Instant.parse("2014-06-01T00:00:00Z");
        Instant end = Instant.parse("2016-01-01T00:00:00Z");
        Instant time = start;
        SummaryStatistics stats = new SummaryStatistics();
        while (time.isBefore(end)) {
            double result = evaluateAlgo(time, time.plus(Duration.ofDays(14)));
            System.out.println(time + "- Result: " + result);
            stats.addValue(result);
            time = time.plus(Duration.ofDays(7));
        }
        System.out.println(stats);
    }

    private double evaluateAlgo(Instant time, Instant end) {
        algo.train(Instant.now());
        algo.prepare(time);
        double result = 0;
        while (!time.isAfter(end)) {
            Instant next = time.plus(Duration.ofDays(1));
            TradeDataPoint nextPoint = repo.getDataMap().get(next);
            TradeDataPoint currentPoint = repo.getDataMap().get(time);
            if (currentPoint != null && nextPoint != null) {
                double diff = nextPoint.settle - currentPoint.settle;
                int count = algo.evaluate(time);
                result += diff * count;
                // System.out.println(time + " - count: " + count + " diff: " +
                // diff + " result: " + result);
            }
            time = next;
        }
        return result;
    }
}
