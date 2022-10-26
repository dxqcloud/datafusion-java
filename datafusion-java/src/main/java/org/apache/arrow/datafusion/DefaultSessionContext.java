package org.apache.arrow.datafusion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultSessionContext extends AbstractProxy implements SessionContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSessionContext.class);

  static native void querySql(
      long runtime, long context, String sql, ObjectResultCallback callback);

  static native void registerCsv(
      long runtime, long context, String name, String path, Consumer<String> callback);

  static native void registerParquet(
      long runtime, long context, String name, String path, Consumer<String> callback);

  static native void registerTable(
      long runtime,
      long context,
      String name,
      byte[] data,
      Consumer<String> print,
      Consumer<String> callback);

  static native void deregisterTable(
      long runtime, long context, String name, Consumer<String> callback);

  @Override
  public CompletableFuture<DataFrame> sql(String sql) {
    long runtime = getRuntime().getPointer();
    CompletableFuture<DataFrame> future = new CompletableFuture<>();
    querySql(
        runtime,
        getPointer(),
        sql,
        (errMessage, dataframeId) -> {
          if (null != errMessage && !errMessage.equals("")) {
            future.completeExceptionally(new RuntimeException(errMessage));
          } else {
            DefaultDataFrame frame = new DefaultDataFrame(DefaultSessionContext.this, dataframeId);
            future.complete(frame);
          }
        });
    return future;
  }

  @Override
  public CompletableFuture<Void> registerCsv(String name, Path path) {
    long runtime = getRuntime().getPointer();
    CompletableFuture<Void> future = new CompletableFuture<>();
    registerCsv(
        runtime,
        getPointer(),
        name,
        path.toAbsolutePath().toString(),
        (errMessage) -> voidCallback(future, errMessage));
    return future;
  }

  @Override
  public CompletableFuture<Void> registerParquet(String name, Path path) {
    long runtime = getRuntime().getPointer();
    CompletableFuture<Void> future = new CompletableFuture<>();
    registerParquet(
        runtime,
        getPointer(),
        name,
        path.toAbsolutePath().toString(),
        (errMessage) -> voidCallback(future, errMessage));
    return future;
  }

  public CompletableFuture<Void> registerTable(String name, VectorSchemaRoot schemaRoot) {
    long runtime = getRuntime().getPointer();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ArrowFileWriter writer =
        new ArrowFileWriter(schemaRoot, null, Channels.newChannel(outputStream));
    try {
      writer.start();
      writer.writeBatch();
      writer.end();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    byte[] bytes = outputStream.toByteArray();
    System.out.println("register_size:" + bytes.length + " row count:" + schemaRoot.getRowCount());
    CompletableFuture<Void> future = new CompletableFuture<>();
    registerTable(
        runtime,
        getPointer(),
        name,
        bytes,
        (msg -> System.out.println("message from jin: " + msg)),
        (errMessage) -> voidCallback(future, errMessage));
    return future;
  }

  public CompletableFuture<Void> deregisterTable(String name) {
    long runtime = getRuntime().getPointer();
    CompletableFuture<Void> future = new CompletableFuture<>();
    deregisterTable(
        runtime,
        getPointer(),
        name,
        (errMessage) -> voidCallback(future, errMessage));
    return future;
  }

  private void voidCallback(CompletableFuture<Void> future, String errMessage) {
    if (null != errMessage && !errMessage.equals("")) {
      future.completeExceptionally(new RuntimeException(errMessage));
    } else {
      future.complete(null);
    }
  }

  @Override
  public Runtime getRuntime() {
    return runtime;
  }

  private final TokioRuntime runtime;

  DefaultSessionContext(long pointer) {
    super(pointer);
    this.runtime = TokioRuntime.create();
    registerChild(runtime);
  }

  @Override
  void doClose(long pointer) throws Exception {
    SessionContexts.destroySessionContext(pointer);
  }
}
