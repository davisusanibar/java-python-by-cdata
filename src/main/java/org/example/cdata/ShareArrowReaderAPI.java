package org.example.cdata;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;

import org.apache.arrow.adapter.jdbc.ArrowVectorIterator;
import org.apache.arrow.adapter.jdbc.JdbcFieldInfo;
import org.apache.arrow.adapter.jdbc.JdbcToArrow;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfig;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfigBuilder;
import org.apache.arrow.adapter.jdbc.JdbcToArrowUtils;
import org.apache.arrow.c.ArrowArrayStream;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.ibatis.jdbc.ScriptRunner;

public class ShareArrowReaderAPI {
    final static BufferAllocator allocator = new RootAllocator();
    static Connection connection;
    static ScriptRunner runnerDDLDML;
    static ArrowVectorIterator arrowVectorIterator;
    static ArrowReader arrowReader;

    public static void main(String[] args) throws IOException, SQLException {
        ShareArrowReaderAPI.simulateAsAJavaConsumers();
    }

    public static BufferAllocator getAllocatorForJavaConsumers() {
        return allocator;
    }

    public static ArrowReader getArrowReaderForJavaConsumers(int batchSize, boolean reuseVSR) {
        try {
            connection = DriverManager.getConnection("jdbc:h2:mem:h2-jdbc-adapter");
            runnerDDLDML = new ScriptRunner(connection);
            runnerDDLDML.setLogWriter(null);
            runnerDDLDML.runScript(new BufferedReader(
                    new FileReader("./src/main/resources/h2-ddl.sql")));
            runnerDDLDML.runScript(new BufferedReader(
                    new FileReader("./src/main/resources/h2-dml.sql")));
            final JdbcToArrowConfig config = new JdbcToArrowConfigBuilder(allocator,
                    JdbcToArrowUtils.getUtcCalendar())
                    .setTargetBatchSize(batchSize)
                    .setReuseVectorSchemaRoot(reuseVSR)
                    .setArraySubTypeByColumnNameMap(
                            new HashMap() {{
                                put("LIST_FIELD19",
                                        new JdbcFieldInfo(Types.INTEGER));
                            }}
                    )
                    .build();
            final ResultSet resultSetConvertToParquet;
            String query = "SELECT int_field1, bool_field2, bigint_field5, char_field16, list_field19 FROM TABLE1";
            resultSetConvertToParquet = connection.createStatement().executeQuery(query);
            arrowVectorIterator = JdbcToArrow.sqlToArrowVectorIterator(
                    resultSetConvertToParquet, config);
            arrowReader = new JDBCReader(allocator, arrowVectorIterator, config);
            return arrowReader;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void closeAllocatorForJavaConsumers() throws SQLException, IOException {
        runnerDDLDML.closeConnection();
        connection.close();
        arrowVectorIterator.close();
        arrowReader.close();
    }

    public static void simulateAsAJavaConsumers() throws IOException, SQLException {
        try (ArrowArrayStream arrowArrayStream = ArrowArrayStream.allocateNew(allocator)) {
            Data.exportArrayStream(allocator, getArrowReaderForJavaConsumers(/*batchSize*/ 2, /*reuseVSR*/ true), arrowArrayStream);
            try (ArrowReader arrowReader = Data.importArrayStream(allocator, arrowArrayStream)) {
                while (arrowReader.loadNextBatch()) {
                    System.out.println(arrowReader.getVectorSchemaRoot().contentToTSVString());
                }
            }
        }
        closeAllocatorForJavaConsumers();
        allocator.close();
    }
}

class JDBCReader extends ArrowReader {
    private final ArrowVectorIterator iter;
    private final JdbcToArrowConfig config;
    private VectorSchemaRoot root;
    private boolean firstRoot = true;

    public JDBCReader(BufferAllocator allocator, ArrowVectorIterator iter, JdbcToArrowConfig config) {
        super(allocator);
        this.iter = iter;
        this.config = config;
    }

    @Override
    public boolean loadNextBatch() throws IOException {
        if (firstRoot) {
            firstRoot = false;
            return true;
        }
        else {
            if (iter.hasNext()) {
                if (root != null && !config.isReuseVectorSchemaRoot()) {
                    root.close();
                }
                else {
                    root.allocateNew();
                }
                root = iter.next();
                return root.getRowCount() != 0;
            }
            else {
                return false;
            }
        }
    }

    @Override
    public long bytesRead() {
        return -666;
    }

    @Override
    protected void closeReadSource() throws IOException {
        if (root != null && !config.isReuseVectorSchemaRoot()) {
            root.close();
        }
    }

    @Override
    protected Schema readSchema() throws IOException {
        return null;
    }

    @Override
    public VectorSchemaRoot getVectorSchemaRoot() throws IOException {
        if (root == null) {
            root = iter.next();
        }
        return root;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}