package org.apache.arrow.datafusion.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import org.apache.arrow.datafusion.DataFrame;
import org.apache.arrow.datafusion.SessionContext;
import org.apache.arrow.datafusion.SessionContexts;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMain {

  private static final Logger logger = LoggerFactory.getLogger(ExampleMain.class);

  public static void main(String[] args) throws Exception {
    try (SessionContext context = SessionContexts.create();
        BufferAllocator allocator = new RootAllocator()) {
      loadConstant(context).join();

      context.registerCsv("test_csv", Paths.get("src/main/resources/test_table.csv")).join();
      context.sql("select * from test_csv limit 3").thenComposeAsync(DataFrame::show).join();

      context
          .registerParquet(
              "test_parquet", Paths.get("src/main/resources/aggregate_test_100.parquet"))
          .join();
      context.sql("select * from test_parquet limit 5").thenComposeAsync(DataFrame::show).join();

      context
          .sql("select * from test_csv")
          .thenComposeAsync(df -> df.collect(allocator))
          .thenAccept(ExampleMain::consumeReader)
          .join();

      Path tempPath = Files.createTempDirectory("datafusion-examples");

      context
          .sql("select * from test_parquet limit 3")
          .thenComposeAsync(df -> df.writeCsv(tempPath.resolve("csv-out")))
          .join();

      context
          .sql("select * from test_parquet limit 3")
          .thenComposeAsync(df -> df.writeParquet(tempPath.resolve("parquet-out")))
          .join();

      context
          .sql("select * from test_parquet limit 3")
          .thenComposeAsync(df -> df.registerTable(context, "test_parquet_limited"))
          .join();

      context.sql("select * from test_parquet_limited").thenComposeAsync(DataFrame::show).join();
    }
  }

  private static void consumeReader(ArrowReader reader) {
    try {
      VectorSchemaRoot root = reader.getVectorSchemaRoot();
      while (reader.loadNextBatch()) {
        VarCharVector nameVector = (VarCharVector) root.getVector(0);
        logger.info(
            "name vector size {}, row count {}, value={}",
            nameVector.getValueCount(),
            root.getRowCount(),
            nameVector);
        BigIntVector ageVector = (BigIntVector) root.getVector(1);
        logger.info(
            "age vector size {}, row count {}, value={}",
            ageVector.getValueCount(),
            root.getRowCount(),
            ageVector);
      }
      reader.close();
    } catch (IOException e) {
      logger.warn("got IO Exception", e);
    }
  }

  private static CompletableFuture<Void> loadConstant(SessionContext context) {
    return context
        .sql("select 1 + 2")
        .thenComposeAsync(
            dataFrame -> {
              logger.info("successfully loaded data frame {}", dataFrame);
              return dataFrame.show();
            });
  }
}
