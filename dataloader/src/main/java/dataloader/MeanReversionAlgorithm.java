package dataloader;

import java.time.Instant;

import javax.inject.Inject;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import dataloader.TradeDataRepository.TradeDataPoint;

public class MeanReversionAlgorithm implements TradingAlgorithm {

    @Inject
    TradeDataRepository repo;

    @Override
    public void train(Instant lastInstantToInclude) {

    }

    @Override
    public void prepare(Instant nextEvaluation) {

    }

    @Override
    public int evaluate(Instant lastInstantToInclude) {
        int longMeanLength = 40;
        int shortMeanLength = 10;
        DescriptiveStatistics meanLong = new DescriptiveStatistics(longMeanLength);
        DescriptiveStatistics meanShort = new DescriptiveStatistics(shortMeanLength);
        for (TradeDataPoint point : repo.getData()) {
            if (point.time.isAfter(lastInstantToInclude))
                continue;
            meanLong.addValue(point.settle);
            meanShort.addValue(point.settle);
        }

        if (meanLong.getMean() < meanShort.getMean()) {
            return -1;
        } else
            return 1;
    }

}
