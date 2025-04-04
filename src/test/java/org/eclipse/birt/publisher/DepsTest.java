package org.eclipse.birt.publisher;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DepsTest {

  @Test
  @Disabled
  public void test() {
    var libs = Analyzer.findLibs();

    // find birt libs
    var birt = libs.stream().filter(lib -> lib.name.contains("birt")).toList();

    // Generate graphviz dot file
    try (var writer = Files.newBufferedWriter(Path.of("target", "deps.dot"))) {
      Analyzer.dots(writer, birt);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Generate tree view
    try (var writer = Files.newBufferedWriter(Path.of("target", "deps.txt"))) {
      for (var lib : birt) {
        for (var dep : lib.deps) {
          dep.pprint(writer);
          writer.write("\n");
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
