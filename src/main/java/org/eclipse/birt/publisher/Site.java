package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.birt.publisher.metadata.Artifact;
import org.eclipse.birt.publisher.metadata.InstallableUnit;
import org.eclipse.birt.publisher.metadata.MavenCoordinates;
import org.eclipse.birt.publisher.metadata.ProvidedCapability;
import org.eclipse.birt.publisher.metadata.RequiredCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Site {

  private static final Logger log = LoggerFactory.getLogger(Site.class);

  private List<InstallableUnit> units;
  private List<Artifact> artifacts;

  private final String name;
  private final String url;

  public Site(String name, String url) {
    this.name = name;
    this.url = url;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }

  public List<InstallableUnit> getUnits() {
    return units;
  }

  public List<Artifact> getArtifacts() {
    return artifacts;
  }

  public InstallableUnit findUnit(String id) {
    for (var unit : units) {
      if (unit.id.equals(id)) {
        return unit;
      }
    }
    return null;
  }

  public InstallableUnit findUnit(RequiredCapability requirement) {
    for (var unit : units) {
      if (unit.isMatch(requirement)) {
        return unit;
      }
    }
    return null;
  }

  public Artifact findArtifact(String id) {
    for (var artifact : artifacts) {
      if (artifact.id.equals(id)) {
        return artifact;
      }
    }
    return null;
  }

  public void load(Path base) throws IOException {
    log.info("Loading site {}", name);

    // Create site directory
    var path = base.resolve(name);

    // Make sure the directory exists
    Files.createDirectories(path);

    var contentJar = path.resolve("content.jar");
    var contentXml = path.resolve("content.xml");

    var artifactsJar = path.resolve("artifacts.jar");
    var artifactsXml = path.resolve("artifacts.xml");

    if (Files.notExists(contentXml)) {
      Client.download(url + "/content.jar", contentJar);
      Client.extract(contentJar, path);
    }

    if (Files.notExists(artifactsXml)) {
      Client.download(url + "/artifacts.jar", artifactsJar);
      Client.extract(artifactsJar, path);
    }

    // Parse the XML files
    this.units = parse(contentXml, "unit", this::parseUnit);
    this.artifacts = parse(artifactsXml, "artifact", this::parseArtifact);
  }

  private Document initDocument(Path path) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      var builder = factory.newDocumentBuilder();
      var document = builder.parse(path.toFile());
      document.getDocumentElement().normalize();
      return document;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse XML file", e);
    }
  }

  private <T> List<T> parse(Path xml, String element, Function<Element, T> parser) {
    var doc = initDocument(xml);
    var root = doc.getDocumentElement();
    return parse(root, element, parser);
  }

  private <T> List<T> parse(Element root, String element, Function<Element, T> parser) {
    var nodes = root.getElementsByTagName(element);
    var items = new ArrayList<T>();
    for (var i = 0; i < nodes.getLength(); i++) {
      var item = parser.apply((Element) nodes.item(i));
      if (item != null) {
        items.add(item);
      }
    }
    return items;
  }

  private InstallableUnit parseUnit(Element element) {
    var props = parseProperties(element);
    var unit = new InstallableUnit();

    unit.id = element.getAttribute("id");
    unit.version = element.getAttribute("version");
    unit.name = getProperty(props, "org.eclipse.equinox.p2.name");
    unit.description = getProperty(props, "org.eclipse.equinox.p2.description");
    unit.maven = parseMaven(props);
    unit.properties = props;
    unit.requires = parseRequired(element);
    unit.provides = parseProvided(element);
    unit.artifacts = parse(element, "artifact", this::parseArtifact);
    unit.site = this;

    return unit;
  }

  private Artifact parseArtifact(Element element) {
    var props = parseProperties(element);
    var artifact = new Artifact();

    artifact.id = element.getAttribute("id");
    artifact.version = element.getAttribute("version");
    artifact.maven = parseMaven(props);
    artifact.properties = props;
    artifact.sha1 = getProperty(props, "download.checksum.sha-1");
    artifact.sha256 = getProperty(props, "download.checksum.sha-256");
    artifact.sha512 = getProperty(props, "download.checksum.sha-512");
    artifact.size = getProperty(props, "download.size");
    artifact.site = this;

    var file = String.format("%s_%s.jar", artifact.id, artifact.version);
    var folder = "org.eclipse.update.feature".equals(artifact.classifier) ? "features" : "plugins";

    artifact.url = String.format("%s/%s/%s", url, folder, file);
    artifact.file = String.format("%s/%s/%s", name, folder, file);

    return artifact;
  }

  private List<ProvidedCapability> parseProvided(Element element) {
    var nodes = element.getElementsByTagName("provided");
    var provides = new ArrayList<ProvidedCapability>();

    for (var i = 0; i < nodes.getLength(); i++) {
      var elem = (Element) nodes.item(i);
      var name = elem.getAttribute("name");
      var namespace = elem.getAttribute("namespace");
      var version = elem.getAttribute("version");
      var provided = new ProvidedCapability();

      provided.name = name;
      provided.namespace = namespace;
      provided.version = version;

      provides.add(provided);
    }

    return provides;
  }

  private List<RequiredCapability> parseRequired(Element element) {
    var nodes = element.getElementsByTagName("required");
    var requires = new ArrayList<RequiredCapability>();

    for (var i = 0; i < nodes.getLength(); i++) {
      var elem = (Element) nodes.item(i);
      var name = elem.getAttribute("name");
      var namespace = elem.getAttribute("namespace");
      var range = elem.getAttribute("range");
      var optional = "true".equals(elem.getAttribute("optional"));
      var required = new RequiredCapability();

      required.name = name;
      required.namespace = namespace;
      required.range = range;
      required.optional = optional;

      requires.add(required);
    }

    return requires;
  }

  private MavenCoordinates parseMaven(Map<String, String> props) {
    var mavenGroupId = getProperty(props, "maven-groupId", "maven-wrapped-groupId");
    var mavenArtifactId = getProperty(props, "maven-artifactId", "maven-wrapped-artifactId");
    var mavenVersion = getProperty(props, "maven-version", "maven-wrapped-version");
    var mavenClassifier = getProperty(props, "maven-classifier", "maven-wrapped-classifier");
    var mavenType = getProperty(props, "maven-type", "maven-wrapped-type");

    if (mavenGroupId == null || mavenArtifactId == null) {
      return null;
    }

    // Remove -SNAPSHOT
    if (mavenVersion != null && mavenVersion.endsWith("-SNAPSHOT")) {
      mavenVersion = mavenVersion.substring(0, mavenVersion.length() - 9);
    }

    var maven = new MavenCoordinates();
    maven.groupId = mavenGroupId;
    maven.artifactId = mavenArtifactId;
    maven.version = mavenVersion;
    maven.classifier = mavenClassifier;
    maven.type = mavenType;

    return maven;
  }

  private String getProperty(Map<String, String> props, String key, String... altKeys) {
    var value = props.get(key);
    if (value != null && value.isBlank()) value = null;
    if (value == null) {
      for (var altKey : altKeys) {
        value = props.get(altKey);
        if (value != null && value.isBlank()) value = null;
        if (value != null) break;
      }
    }
    return value;
  }

  private Map<String, String> parseProperties(Element element) {
    var nodes = element.getElementsByTagName("property");
    var props = new HashMap<String, String>();
    for (var i = 0; i < nodes.getLength(); i++) {
      var prop = (Element) nodes.item(i);
      var name = prop.getAttribute("name");
      var value = prop.getAttribute("value");
      props.put(name, value);
    }

    // Resolve df_LT
    for (var entry : props.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();
      if (value.startsWith("%")) {
        var defaultKey = "df_LT." + value.substring(1);
        var defaultValue = props.get(defaultKey);
        if (defaultValue != null) {
          props.put(key, defaultValue);
        }
      }
    }

    return props;
  }
}
