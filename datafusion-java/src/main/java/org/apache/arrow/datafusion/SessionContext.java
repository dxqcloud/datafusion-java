package org.apache.arrow.datafusion;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.apache.arrow.vector.VectorSchemaRoot;

/** A session context holds resources and is the entrance for obtaining {@link DataFrame} */
public interface SessionContext extends AutoCloseable, NativeProxy {

  /** Obtain the {@link DataFrame} by running the {@code sql} against the datafusion library */
  CompletableFuture<DataFrame> sql(String sql);

  /** Registering a csv file with the context */
  CompletableFuture<Void> registerCsv(String name, Path path);

  /** Registering a parquet file with the context */
  CompletableFuture<Void> registerParquet(String name, Path path);

  CompletableFuture<Void> registerTable(String name, VectorSchemaRoot schemaRoot);

  /** Get the runtime associated with this context */
  Runtime getRuntime();
}
