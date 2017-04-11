package dataloader;

import java.io.Serializable;
import java.time.Instant;

public interface TradingAlgorithm extends Cloneable, Serializable {

    void train(Instant lastInstantToInclude, TradeDataRepository repo);

    void prepare(Instant nextEvaluation, TradeDataRepository repo);

    int evaluate(Instant lastInstantToInclude, TradeDataRepository repo);

}
