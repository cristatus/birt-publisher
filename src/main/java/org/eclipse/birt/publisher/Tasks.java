package org.eclipse.birt.publisher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;

public class Tasks {

  @FunctionalInterface
  public static interface Task<T> {
    void run(T item) throws IOException;
  }

  public static <T> void processInParallel(Collection<T> data, Task<T> task) {

    data.parallelStream()
        .forEach(
            item -> {
              try {
                task.run(item);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }
}
