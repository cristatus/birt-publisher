package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.birt.publisher.Config.PublishConfig;
import org.eclipse.birt.publisher.metadata.Artifact;
import org.eclipse.birt.publisher.metadata.InstallableUnit;
import org.eclipse.birt.publisher.metadata.MavenCoordinates;
import org.eclipse.birt.publisher.metadata.RequiredCapability;
import org.eclipse.birt.publisher.metadata.ResolvedUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Publisher {

  private static final Logger log = LoggerFactory.getLogger(Publisher.class);

  private final Path base;

  private final Config config;

  private final Maven maven;

  private final List<Site> sites;

  public Publisher(Path base, Config config, Maven maven, List<Site> sites) {
    this.base = base;
    this.config = config;
    this.maven = maven;
    this.sites = sites;
  }

  public void publish() throws IOException {
    // Customize maven group id
    var group = config.getMaven().group;

    // Load sites
    Tasks.processInParallel(sites, x -> x.load(base));

    // Find units to publish
    var units =
        findPublishCandidates().values().stream()
            .filter(x -> !x.external) // exclude external units
            .filter(x -> !x.id.endsWith(".feature.jar")) // exclude feature jars
            .toList();

    // Download them in advance
    var artifacts =
        units.stream()
            .flatMap(unit -> Stream.of(unit.artifact, unit.sourceArtifact))
            .filter(Objects::nonNull)
            .toList();
    Tasks.processInParallel(artifacts, this::download);

    // Update group id
    if (group != null) {
      for (var unit : units) {
        if (unit.maven != null) {
          unit.maven.groupId = group;
        }
      }
    }

    // Publish units
    Tasks.processInParallel(units, x -> publish(x, group));
  }

  private void publish(ResolvedUnit unit, String group) throws IOException {
    // Check if we have maven coordinates
    if (unit.maven == null || unit.maven.groupId == null) {
      log.warn("No maven coordinates found for {}", unit.id);
      return;
    }

    log.info("Publishing {}", unit.id);

    // Publish feature group as pom only
    var isPom = unit.id.endsWith(".feature.group");

    // Find project info
    var info =
        config.getDetails().stream()
            .filter(x -> unit.id.equals(x.id) || unit.maven.groupId.equals(x.group))
            .findFirst()
            .orElse(null);

    var jarFile = download(unit.artifact);
    var sourceFile = download(unit.sourceArtifact);

    var pomName = unit.maven.artifactId + "-" + unit.maven.version + ".pom";
    var pomFile = base.resolve(pomName);
    var pom = new Pom(unit).group(group).pom(isPom).info(info).build();

    var javadocFile = javadoc(unit, pomFile);

    Files.writeString(pomFile, pom);
    try {
      // Publish to maven repository
      maven.publish(pomFile, jarFile, sourceFile, javadocFile);
    } finally {
      Files.deleteIfExists(pomFile);
      if (javadocFile != null) Files.deleteIfExists(javadocFile);
    }
  }

  private Path download(Artifact artifact) {
    if (artifact == null || artifact.file == null) return null;
    var file = base.resolve(artifact.file);
    Client.download(artifact.url, file);
    Client.verify(file, artifact.sha512);
    return file;
  }

  private Path javadoc(ResolvedUnit unit, Path pomFile) throws IOException {
    if (unit.sourceArtifact == null) {
      return null;
    }

    var name = pomFile.getFileName().toString();
    var jarFile = base.resolve(name.replace(".pom", "-javadoc.jar"));

    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

    // Create jar file
    try (var out = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
      out.putNextEntry(new JarEntry("README.txt"));
      out.write("Please refer to the corresponding source jar.".getBytes(StandardCharsets.UTF_8));
    }

    return jarFile;
  }

  private Map<String, ResolvedUnit> findPublishCandidates() {
    var units = new LinkedHashMap<String, ResolvedUnit>();
    for (var plugin : config.getPublish()) {
      var unit =
          sites.stream()
              .map(site -> site.findUnit(plugin.id))
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow(
                  () -> new IllegalArgumentException("No such plugin found: " + plugin.id));
      resolve(unit, units);
    }
    return units;
  }

  private ResolvedUnit resolve(InstallableUnit unit, Map<String, ResolvedUnit> units) {
    var id = unit.id;

    // If already resolved, return
    if (units.containsKey(id)) {
      return units.get(id);
    }

    log.info("Resolving {}", id);

    // Create a new ResolvedUnit
    var resolved = new ResolvedUnit();

    resolved.id = id;
    resolved.version = unit.version;
    resolved.name = unit.name;
    resolved.description = unit.description;

    // Find the maven coordinates
    resolved.maven = findMavenCoordinates(unit);

    // Excluded?
    if (isExcluded(resolved)) {
      log.info("Excluding {}", id);
      return null;
    }

    // Add the unit to the map earlier to avoid infinite recursion
    units.put(id, resolved);

    // Check if we can resolve the maven coordinates
    if (resolve(resolved)) {
      // Artifact found in maven central
      resolved.external = true;
      return resolved;
    }

    // Find artifacts
    resolved.artifact = findArtifact(unit);
    resolved.sourceArtifact = findArtifact(unit, "source");

    // Resolve dependencies
    for (var requirement : unit.requires) {
      var required = findUnit(requirement);
      if (required == null && requirement.optional) {
        // Optional dependency, skip
        continue;
      }
      if (required == null) {
        log.warn("No dependency found for {} in {}", requirement, id);
        continue;
      }

      // Ignore source and jre
      if (required.id.endsWith(".source") || required.id.equals("a.jre.javase")) {
        continue;
      }

      var dependency = resolve(required, units);
      if (dependency == null) {
        log.warn("No dependency found for {} in {}", requirement, id);
        continue;
      }

      // Ignore self dependency
      if (dependency.maven != null && dependency.maven.equals(resolved.maven)) {
        continue;
      }

      if (requirement.optional) {
        resolved.optionalDependencies.add(dependency);
      } else {
        resolved.dependencies.add(dependency);
      }
    }

    return resolved;
  }

  private InstallableUnit findUnit(RequiredCapability requirement) {
    for (var site : sites) {
      var unit = site.findUnit(requirement);
      if (unit != null) return unit;
    }
    return null;
  }

  private boolean resolve(ResolvedUnit unit) {
    if (unit.maven == null) return false;
    if (isCandidate(unit)) return false;
    var canResolve = Boolean.TRUE.equals(config.getMaven().resolve);
    return !canResolve || maven.resolve(unit.maven.toString());
  }

  private boolean isMatched(ResolvedUnit unit, PublishConfig config) {
    var id = unit.id;
    var maven = unit.maven;
    if (config.id != null && config.id.equals(id)) return true;
    if (config.pattern != null) {
      var pattern = Pattern.compile(config.pattern);
      if (maven != null) {
        // Check if the pattern matches the maven coordinates
        var matcher = pattern.matcher(maven.toString());
        if (matcher.matches()) return true;
      } else {
        // Check if the pattern matches the id
        var matcher = pattern.matcher(id);
        if (matcher.matches()) return true;
      }
    }
    return false;
  }

  private boolean isExcluded(ResolvedUnit unit) {
    for (var exclude : config.getExclude()) {
      if (isMatched(unit, exclude)) {
        return true;
      }
    }
    return false;
  }

  private boolean isCandidate(ResolvedUnit unit) {
    for (var candidate : config.getCandidates()) {
      if (isMatched(unit, candidate)) {
        return true;
      }
    }
    return false;
  }

  private MavenCoordinates findMavenCoordinates(InstallableUnit unit) {
    var maven = new MavenCoordinates();
    var artifact = findArtifact(unit);

    var given = artifact == null ? unit.maven : artifact.maven;
    var text = given == null ? unit.id : given.toString();

    if (given != null) {
      maven.groupId = given.groupId;
      maven.artifactId = given.artifactId;
      maven.version = given.version;
      maven.classifier = given.classifier;
      maven.type = given.type;
      maven.properties.putAll(given.properties);
    } else {
      maven.version = unit.version;
    }

    for (var mapping : config.getMappings()) {
      var pattern = Pattern.compile(mapping.pattern);
      var matcher = pattern.matcher(text);
      if (matcher.matches()) {
        if (mapping.groupId != null) {
          maven.groupId = matcher.replaceAll(mapping.groupId);
        }
        if (mapping.artifactId != null) {
          maven.artifactId = matcher.replaceAll(mapping.artifactId);
        }
        if (mapping.version != null) {
          maven.version = matcher.replaceAll(mapping.version);
        }
      }
    }

    return maven.groupId == null ? null : maven;
  }

  private Artifact findArtifact(InstallableUnit unit) {
    return findArtifact(unit, null);
  }

  private Artifact findArtifact(InstallableUnit unit, String classifier) {
    var id = classifier == null ? unit.id : unit.id + "." + classifier;
    return sites.stream()
        .map(site -> site.findArtifact(id))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
