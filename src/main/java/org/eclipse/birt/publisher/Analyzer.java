package org.eclipse.birt.publisher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Analyze jar files and their dependencies. */
public class Analyzer {

  /** Find all jar files in the classpath. */
  public List<Path> findJars() {
    var classPath = System.getProperty("java.class.path");
    var classList = List.of(classPath.split(System.getProperty("path.separator")));
    return classList.stream().filter(path -> path.endsWith(".jar")).map(Path::of).toList();
  }

  /**
   * Find dependencies of a jar file.
   *
   * <p>This method uses the jdeps tool to analyze the jar file and find its dependencies.
   *
   * @param jarPath the jar file to analyze
   * @param classPath the classpath to use for analysis
   * @return a list of jar files that the given jar file depends on
   */
  public List<Path> findDeps(Path jarPath, List<Path> classPath) {
    var tool = ToolProvider.findFirst("jdeps").orElseThrow();
    var out = new StringWriter();
    var err = new StringWriter();

    var jars = new ArrayList<>(classPath);

    if (!jars.contains(jarPath)) {
      jars.add(jarPath);
    }

    var cp =
        jars.stream()
            .map(Path::toString)
            .collect(Collectors.joining(System.getProperty("path.separator")));

    tool.run(
        new PrintWriter(out),
        new PrintWriter(err),
        "--class-path",
        cp,
        "--multi-release",
        "base",
        "--ignore-missing-deps",
        "-q",
        "-s",
        jarPath.toString());

    return out.toString()
        .lines()
        .filter(line -> line.contains("->"))
        .map(line -> line.split("->")[1].trim())
        .filter(path -> path.endsWith(".jar"))
        .map(Path::of)
        .toList();
  }

  /**
   * Find dependencies of a jar file using the classpath.
   *
   * @param jarPath the jar file to analyze
   * @return a list of jar files that the given jar file depends on
   * @see #findDeps(Path, List)
   */
  public List<Path> findDeps(Path jarPath) {
    var classPath = findJars();
    return findDeps(jarPath, classPath);
  }
}
