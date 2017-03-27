package dataloader;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

public class Util {
    public static String dbName = "aTimeSeries";

    public static InfluxDB influxDB = InfluxDBFactory.connect("http://localhost:8086", "foo", "foo");

}
