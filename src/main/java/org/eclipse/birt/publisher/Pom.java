package org.eclipse.birt.publisher;

import java.io.IOException;
import org.eclipse.birt.publisher.Config.InfoConfig;
import org.eclipse.birt.publisher.metadata.ResolvedUnit;

public class Pom {

  private final StringBuilder sb = new StringBuilder();
  private final ResolvedUnit unit;

  private InfoConfig info;

  private String group;

  private boolean pom;
  private boolean bom;

  public Pom(ResolvedUnit unit) {
    this.unit = unit;
  }

  public Pom group(String group) {
    this.group = group;
    return this;
  }

  public Pom pom(boolean pom) {
    this.pom = pom;
    return this;
  }

  public Pom bom(boolean bom) {
    this.bom = bom;
    return this;
  }

  public Pom info(InfoConfig info) {
    this.info = info;
    return this;
  }

  private void buildHead() {
    var maven = unit.maven;
    var groupId = group == null || group.isEmpty() ? maven.groupId : group;
    var artifactId = maven.artifactId;
    var version = maven.version;
    var packaging = bom ? "bom" : pom ? "pom" : "jar";

    var name = unit.name;
    var description = unit.description;

    append(sb, 0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    append(sb, 0, "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"");
    append(sb, 0, "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    append(
        sb,
        0,
        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">");
    append(sb, 1, "<modelVersion>4.0.0</modelVersion>");
    append(sb, 1, "groupId", groupId);
    append(sb, 1, "artifactId", artifactId);
    append(sb, 1, "version", version);
    append(sb, 1, "packaging", packaging);
    append(sb, 1, "name", name);
    append(sb, 1, "description", description);
  }

  private String findInfo() {
    try (var is = Pom.class.getResourceAsStream("/info.txt")) {
      return is == null ? null : new String(is.readAllBytes());
    } catch (IOException e) {
      return null;
    }
  }

  private void buildDetails() {
    if (info == null) {
      return;
    }
    var text = findInfo();
    if (text == null) {
      return;
    }

    var name = info.name;
    var scm = info.scm;
    var tag = unit.maven.version;

    append(sb, 1, text.replace("{name}", name).replace("{tag}", tag).replace("{scm}", scm).trim());
  }

  private void buildDependencies() {
    if (unit.dependencies.isEmpty() && unit.optionalDependencies.isEmpty()) {
      return;
    }
    if (bom) append(sb, 1, "<dependencyManagement>");
    appendDependencies(sb, bom ? 2 : 1, unit);
    if (bom) append(sb, 1, "</dependencyManagement>");
  }

  private void buildTail() {
    sb.append("</project>\n");
  }

  public String build() {
    this.buildHead();
    this.buildDetails();
    this.buildDependencies();
    this.buildTail();
    return sb.toString();
  }

  private void appendDependencies(StringBuilder sb, int level, ResolvedUnit unit) {
    var dependencies = unit.dependencies;
    var optionalDependencies = unit.optionalDependencies;

    if (dependencies.isEmpty() && optionalDependencies.isEmpty()) {
      return;
    }

    append(sb, level, "<dependencies>");
    dependencies.forEach(x -> appendDependency(sb, level + 1, x, false));
    optionalDependencies.forEach(x -> appendDependency(sb, level + 1, x, true));
    append(sb, level, "</dependencies>");
  }

  private void appendDependency(StringBuilder sb, int level, ResolvedUnit unit, boolean optional) {
    var groupId = unit.maven.groupId;
    var artifactId = unit.maven.artifactId;
    var version = unit.maven.version;

    append(sb, level, "<dependency>");
    append(sb, level + 1, "groupId", groupId);
    append(sb, level + 1, "artifactId", artifactId);
    append(sb, level + 1, "version", version);
    if (optional) {
      append(sb, level + 1, "optional", "true");
    }
    append(sb, level, "</dependency>");
  }

  private void append(StringBuilder sb, int level, String text) {
    if (text != null) {
      sb.append(indent(text, level)).append("\n");
    }
  }

  private void append(StringBuilder sb, int level, String element, String text) {
    if (text == null) {
      return;
    }
    var addNewLine = text.contains("\n");
    sb.append("  ".repeat(level));
    sb.append("<").append(element).append(">");
    if (addNewLine) {
      sb.append("\n");
      sb.append(indent(text, level + 1));
    } else {
      sb.append(text);
    }
    sb.append("</").append(element).append(">\n");
  }

  private String indent(String text, int level) {
    return text == null ? null : text.replaceAll("(?m)^", "  ".repeat(level));
  }
}
