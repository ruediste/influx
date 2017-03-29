package dataloader;

import java.time.Duration;
import java.time.Instant;

import javax.inject.Inject;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.commons.math.stat.descriptive.rank.Max;

public class MeanReversionAlgorithm implements TradingAlgorithm, Cloneable {

    @Inject
    TradeDataRepository repo;
    public int longMeanLength = 40;
    public int shortMeanLength = 10;
    public double devFactor = 1;

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public void train(Instant lastInstantToInclude) {

    }

    @Override
    public void prepare(Instant nextEvaluation) {

    }

    @Override
    public int evaluate(Instant lastInstantToInclude) {
        SummaryStatistics longStat = new SummaryStatistics();
        SummaryStatistics shortStat = new SummaryStatistics();
        longStat.setSumLogImpl(new Max());
        shortStat.setSumLogImpl(new Max());
        repo.getDataMap().subMap(lastInstantToInclude.minus(Duration.ofDays(longMeanLength)), lastInstantToInclude)
                .values().forEach(point -> longStat.addValue(point.settle));
        repo.getDataMap().subMap(lastInstantToInclude.minus(Duration.ofDays(shortMeanLength)), lastInstantToInclude)
                .values().forEach(point -> shortStat.addValue(point.settle));

        double dev = devFactor * longStat.getStandardDeviation();
        double diff = longStat.getMean() - shortStat.getMean();
        return (int) Math.floor(diff / dev);
    }

}
