package org.eclipse.birt.publisher;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class Analyzer {

  public static List<Path> findClassPath() {
    var classPath = System.getProperty("java.class.path");
    var classList = Arrays.asList(classPath.split(File.pathSeparator));
    return classList.stream()
        .filter(path -> path.endsWith(".jar"))
        .map(Path::of)
        .filter(Files::exists)
        .toList();
  }

  private static List<Path> findDeps(Path jarPath, List<Path> classPath) {
    var tool = ToolProvider.findFirst("jdeps").orElseThrow();
    var out = new StringWriter();
    var err = new StringWriter();

    var jars = new ArrayList<>(classPath);

    if (!jars.contains(jarPath)) {
      jars.add(jarPath);
    }

    var cp = jars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
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

  public static List<JarLib> findLibs() {
    var jars = findClassPath();
    var libs = new LinkedHashMap<Path, JarLib>();

    // Initialize libs with jars
    for (var jar : jars) {
      libs.put(jar, new JarLib(jar));
    }

    // Process jars in parallel
    jars.parallelStream()
        .forEach(
            jar -> {
              try {
                var deps = findDeps(jar, jars);
                var lib = libs.get(jar);
                for (var dep : deps) {
                  var depLib = libs.get(dep);
                  if (depLib != null) {
                    lib.dependsOn(depLib);
                  }
                }
              } catch (Exception e) {
                // Ignore errors
              }
            });

    return libs.values().stream().toList();
  }

  private static String quote(String str) {
    return "\"" + str + "\"";
  }

  private static void dots(Writer writer, List<JarLib> libs, Set<JarLib> visited) {
    var out = new PrintWriter(writer);
    for (var lib : libs) {
      if (visited.contains(lib)) {
        continue;
      }
      visited.add(lib);
      out.println(String.format("  %s [label=%s];", quote(lib.name), quote(lib.name)));
      for (var dep : lib.deps) {
        out.println(String.format("  %s -> %s;", quote(lib.name), quote(dep.name)));
        dots(writer, dep.deps, visited);
      }
    }
  }

  public static void dots(Writer writer, List<JarLib> libs) {
    var out = new PrintWriter(writer);
    out.println("digraph G {");
    out.println("  splines=sfdp;");
    out.println("  splines=ortho;");
    out.println("  overlap=flase;");
    out.println("  node[shape=box];");
    dots(writer, libs, new HashSet<>());
    out.println("}");
  }

  public static class JarLib {

    public final Path jar;

    public final String name;

    public final List<JarLib> deps;

    public JarLib(Path jar) {
      this.jar = jar;
      this.name = jar.getFileName().toString();
      this.deps = new ArrayList<>();
    }

    public void dependsOn(JarLib dep) {
      if (!deps.contains(dep)) {
        deps.add(dep);
      }
    }

    public void pprint(Writer writer) {
      pprint(new PrintWriter(writer), 0, new HashSet<>());
    }

    private void pprint(PrintWriter out, int level, Set<JarLib> visited) {
      var indent = "  ".repeat(level);
      out.println(indent + name);

      if (visited.contains(this)) {
        return;
      }

      visited.add(this);

      for (var dep : deps) {
        dep.pprint(out, level + 1, visited);
      }
    }

    @Override
    public int hashCode() {
      return jar.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || (obj instanceof JarLib other && jar.equals(other.jar));
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
