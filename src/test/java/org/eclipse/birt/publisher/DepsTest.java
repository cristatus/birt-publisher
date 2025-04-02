package org.eclipse.birt.publisher;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class DepsTest {

  private void print(Path jar, Map<Path, List<Path>> deps, int level, Set<Path> visited) {
    if (visited.contains(jar)) {
      return;
    }
    visited.add(jar);

    var name = jar.getFileName().toString();
    var indent = "  ".repeat(level);

    System.out.println(indent + name);

    var paths = deps.get(jar);
    if (paths != null) {
      for (var path : paths) {
        print(path, deps, level + 1, visited);
      }
    }
  }

  private void print(Path jar, Map<Path, List<Path>> deps) {
    print(jar, deps, 0, new HashSet<>());
  }

  @Test
  public void test() {
    var analyzer = new Analyzer();
    var jars = analyzer.findJars();
    var deps = new HashMap<Path, List<Path>>();

    for (var jar : jars) {
      try {
        deps.put(jar, analyzer.findDeps(jar));
      } catch (Exception e) {
        // Ignore errors
      }
    }

    deps.forEach(
        (jar, paths) -> {
          if (paths != null) {
            print(jar, deps);
          }
        });
  }
}
