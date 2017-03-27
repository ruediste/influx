package dataloader;

import java.time.Instant;

public interface TradingAlgorithm {

    void train(Instant lastInstantToInclude);

    void prepare(Instant nextEvaluation);

    int evaluate(Instant lastInstantToInclude);
}
