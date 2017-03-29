package dataloader;

import java.time.Instant;

public interface TradingAlgorithm extends Cloneable {

    void train(Instant lastInstantToInclude);

    void prepare(Instant nextEvaluation);

    int evaluate(Instant lastInstantToInclude);

}
