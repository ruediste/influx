package dataloader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

@Singleton
public class TradeDataRepository {

    public class TradeDataPoint {
        Instant time;
        double open;
        double settle;
        double high;
        double low;

        @Override
        public String toString() {
            return "(" + time + "," + open + "," + settle + "," + high + "," + low + ")";
        }
    }

    private List<TradeDataPoint> dataList = new ArrayList<>();
    private TreeMap<Instant, TradeDataPoint> dataMap = new TreeMap<>();

    @PostConstruct
    public void postConstruct() {
        Query query = new Query("SELECT Open, Settle, High, Low FROM aTimeSeries..CH2016 ", Util.dbName);
        QueryResult results = Util.influxDB.query(query);

        for (List<Object> list : results.getResults().get(0).getSeries().get(0).getValues()) {
            Instant time = Instant.parse((CharSequence) list.get(0));

            Double open = (Double) list.get(1);
            Double settle = (Double) list.get(2);
            Double high = (Double) list.get(3);
            Double low = (Double) list.get(4);
            if (open != null && settle != null && high != null && low != null) {
                TradeDataPoint point = new TradeDataPoint();
                point.time = time;
                point.open = open;
                point.settle = settle;
                point.high = high;
                point.low = low;
                dataList.add(point);
                dataMap.put(point.time, point);
            }
        }
    }

    public List<TradeDataPoint> getData() {
        return dataList;
    }

    public TreeMap<Instant, TradeDataPoint> getDataMap() {
        return dataMap;
    }
}
