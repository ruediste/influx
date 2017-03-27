package dataloader;

import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

public class Extractor {

    private interface OpenConsumer {
        void accept(Instant time, double open);
    }

    @SuppressWarnings("unused")
    public static void main(String... args) {
        process(new OpenConsumer() {

            @Override
            public void accept(Instant time, double open) {
                // TODO Auto-generated method stub

            }
        });
        if (false)
            process(new OpenConsumer() {
                Double lastOpen;
                Instant lastTime;

                @Override
                public void accept(Instant time, double open) {
                    if (lastTime != null && lastTime.plus(Period.ofDays(1)).equals(time)) {
                        Util.influxDB.write(Util.dbName, "autogen",
                                Point.measurement("diff").time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                                        .tag("days", Integer.toString(1)).addField("value", open - lastOpen).build());

                    }
                    lastTime = time;
                    lastOpen = open;
                }
            });
        if (false) {
            int windowSize = 40;
            process(new OpenConsumer() {
                DescriptiveStatistics stats = new DescriptiveStatistics(windowSize);

                @Override
                public void accept(Instant time, double open) {
                    stats.addValue(open);
                    Util.influxDB.write(Util.dbName, "autogen",
                            Point.measurement("slidingMean").time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                                    .tag("windowSize", Integer.toString(windowSize)).addField("value", stats.getMean())
                                    .build());
                }
            });
        }
    }

    public static void process(OpenConsumer consumer) {
        Query query = new Query("SELECT Open FROM aTimeSeries..CH2016 ", Util.dbName);
        QueryResult results = Util.influxDB.query(query);
        for (List<Object> list : results.getResults().get(0).getSeries().get(0).getValues()) {
            Instant time = Instant.parse((CharSequence) list.get(0));
            Double open = (Double) list.get(1);
            if (open != null)
                consumer.accept(time, open);
        }
    }
}
