package dataloader;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;

import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonReader.Token;

import okio.Okio;

public class DataLoaderMain {

    public static void main(String... args) throws Exception {

        // influxDB.createDatabase(dbName);

        BatchPoints batchPoints = BatchPoints.database(Util.dbName).tag("async", "true").retentionPolicy("autogen")
                .consistency(ConsistencyLevel.ALL).build();

        String datasetCode = null;
        List<String> columns = new ArrayList<>();

        JsonReader reader = JsonReader.of(Okio.buffer(Okio.source(new File("CH2016.json"))));
        reader.beginObject();
        if ("dataset".equals(reader.nextName())) {
            reader.beginObject();
            dataset: while (reader.hasNext()) {
                switch (reader.nextName()) {
                case "dataset_code":
                    datasetCode = reader.nextString();
                    break;
                case "column_names":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String column = reader.nextString();
                        columns.add(column);
                    }
                    reader.endArray();
                    break;
                case "data":
                    break dataset;
                default:
                    reader.skipValue();
                    continue;
                }
            }
            reader.beginArray();
            {
                while (reader.hasNext()) {
                    reader.beginArray();
                    Builder point = Point.measurement(datasetCode);
                    for (String column : columns) {
                        if ("date".equalsIgnoreCase(column)) {
                            LocalDate date = LocalDate.from(DateTimeFormatter.ISO_DATE.parse(reader.nextString()));
                            System.out.print(date + " - ");
                            long time = OffsetDateTime.of(date, LocalTime.MIDNIGHT, ZoneOffset.UTC).toInstant()
                                    .toEpochMilli();
                            point.time(time, TimeUnit.MILLISECONDS);
                        } else {
                            Double value;
                            if (reader.peek() == Token.NULL)
                                value = reader.nextNull();
                            else
                                value = reader.nextDouble();
                            System.out.print(column + ":" + value + " ");
                            if (value != null)
                                point.addField(column, value);
                        }
                    }
                    System.out.println();
                    batchPoints.point(point.build());
                    if (batchPoints.getPoints().size() > 100)
                        Util.influxDB.write(batchPoints);
                    reader.endArray();
                }
            }
            reader.endArray();
        }
        reader.close();
        Util.influxDB.write(batchPoints);

        // influxDB.deleteDatabase(dbName);
    }
}
