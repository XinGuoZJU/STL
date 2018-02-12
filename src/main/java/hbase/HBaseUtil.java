package hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Created by cbdog94 on 17-3-17.
 */
public class HBaseUtil {

    private static Connection connection;

    public HBaseUtil() {
        try {
            Configuration conf = HBaseConfiguration.create();
            conf.set("hbase.zookeeper.quorum", "192.168.1.10");
            conf.set("hbase.zookeeper.property.clientPort", "2181");
            connection = ConnectionFactory.createConnection(conf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }


    public String getColumnFromHBase(String tableName, byte[] rowKey, byte[] columnFamily, byte[] column) {
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));
            Result resultCell = table.get(new Get(rowKey));
            return Bytes.toString(resultCell.getValue(columnFamily, column));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, String> getColumnFamilyFromHBase(String tableName, byte[] rowKey, byte[] columnFamily) {
        Map<String, String> resultMap = new HashMap<>();
        try {
            Table table = connection.getTable(TableName.valueOf(tableName));

//            Get get = new Get(rowKey).addFamily(columnFamily);
            Result resultCell = table.get(new Get(rowKey).addFamily(columnFamily));

//            NavigableMap<byte[],byte[]> map=resultCell.getFamilyMap(columnFamily);
//            for(map.keySet())
            while (resultCell.advance()) {
                String key = Bytes.toString(CellUtil.cloneQualifier(resultCell.current()));
                String value = Bytes.toString(CellUtil.cloneValue(resultCell.current()));
                resultMap.put(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    public static void close() {
        try {
            if (connection != null)
                connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}