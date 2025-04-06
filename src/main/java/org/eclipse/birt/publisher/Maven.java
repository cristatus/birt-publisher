package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.generator.gnupg.GnupgConfigurationKeys;
import org.eclipse.aether.generator.gnupg.GnupgSignatureArtifactGeneratorFactory;
import org.eclipse.aether.generator.gnupg.loaders.GpgConfLoader;
import org.eclipse.aether.generator.gnupg.loaders.GpgEnvLoader;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.birt.publisher.Config.MavenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Maven {

  private static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

  private static final String MAVEN_USER = System.getProperty("user.home") + "/.m2";
  private static final String MAVEN_HOME = System.getProperty("maven.home");

  private static final String GPG_ENABLED = GnupgConfigurationKeys.CONFIG_PROP_ENABLED;
  private static final String GPG_KEY_FILE = GnupgConfigurationKeys.CONFIG_PROP_KEY_FILE_PATH;
  private static final String GPG_KEY_PASSPHRASE =
      "env." + GnupgConfigurationKeys.RESOLVER_GPG_KEY_PASS;
  private static final String GPG_KEY_FINGERPRINT =
      "env." + GnupgConfigurationKeys.RESOLVER_GPG_KEY_FINGERPRINT;

  private static final Logger log = LoggerFactory.getLogger(Maven.class);

  private final RepositorySystem system;
  private final RepositorySystemSession session;
  private final RemoteRepository central;
  private final RemoteRepository remote;

  private final GnupgSignatureArtifactGeneratorFactory gpgFactory;

  private final Settings settings;

  public Maven(Path base, MavenConfig config) {
    var supplier = new RepositorySystemSupplier();
    var system = supplier.get();
    var session = MavenRepositorySystemUtils.newSession();
    var central = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL).build();
    var local = base.resolve("repo");

    session.setLocalRepositoryManager(
        system.newLocalRepositoryManager(session, new LocalRepository(local)));

    session.setConfigProperty(ConfigurationProperties.INTERACTIVE, false);

    if (config.gpgKey != null) {
      session.setConfigProperty(GPG_ENABLED, Boolean.TRUE);
      session.setConfigProperty(GPG_KEY_FILE, config.gpgKey);
      if (config.gpgPassphrase != null) {
        session.setConfigProperty(GPG_KEY_PASSPHRASE, config.gpgPassphrase);
      }
      if (config.gpgFingerprint != null) {
        session.setConfigProperty(GPG_KEY_FINGERPRINT, config.gpgFingerprint);
      }
    }

    this.gpgFactory =
        new GnupgSignatureArtifactGeneratorFactory(
            supplier.getArtifactPredicateFactory(),
            Map.of(
                GpgEnvLoader.NAME, new GpgEnvLoader(),
                GpgConfLoader.NAME, new GpgConfLoader()));

    this.system = system;
    this.session = session;
    this.central = central;
    this.settings = getSettings();
    this.remote = getRemoteRepository(config, local);
  }

  private RemoteRepository getRemoteRepository(MavenConfig config, Path local) {
    var id = config.repoId;
    var url = config.repoUrl;
    if (url == null) {
      url = local.toUri().toString();
    }

    if (id != null) {
      var repo = findRepository(settings, config.profile, id);
      if (repo != null) {
        url = repo.getUrl();
      }
    }

    return new RemoteRepository.Builder(id, "default", url)
        .setAuthentication(getAuthentication(config))
        .build();
  }

  private Authentication getAuthentication(MavenConfig config) {
    var id = config.repoId;
    var username = config.username;
    var password = config.password;

    if (id != null && (username == null || password == null)) {
      var server =
          settings.getServers().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
      if (server != null) {
        username = server.getUsername();
        password = server.getPassword();
      }
    }

    if (username != null && password != null) {
      return new AuthenticationBuilder().addUsername(username).addPassword(password).build();
    }

    return null;
  }

  private Repository findRepository(Settings settings, String profile, String id) {
    // First check the profile
    var repo =
        settings.getProfiles().stream()
            .filter(p -> p.getId().equals(profile))
            .flatMap(p -> p.getRepositories().stream())
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElse(null);

    if (repo != null) {
      return repo;
    }

    // If not found, check the active profiles
    var active = settings.getActiveProfiles();
    return settings.getProfiles().stream()
        .filter(p -> active.contains(p.getId()))
        .flatMap(p -> p.getRepositories().stream())
        .filter(r -> r.getId().equals(id))
        .findFirst()
        .orElse(null);
  }

  private Settings getSettings() {
    var request = new DefaultSettingsBuildingRequest();
    var userConf = Path.of(MAVEN_USER).resolve("settings.xml");
    var mavenConf = Path.of(MAVEN_HOME).resolve("conf").resolve("settings.xml");

    request.setGlobalSettingsFile(userConf.toFile());
    request.setUserSettingsFile(mavenConf.toFile());

    try {
      return new DefaultSettingsBuilderFactory()
          .newInstance()
          .build(request)
          .getEffectiveSettings();
    } catch (SettingsBuildingException e) {
      throw new RuntimeException("Unable to load settings", e);
    }
  }

  /**
   * Resolve an artifact
   *
   * @param artifact the artifact to resolve
   * @return true if the artifact is resolved, false otherwise
   */
  private boolean resolve(Artifact artifact) {
    log.debug("Resolving {}", artifact);
    var request = new ArtifactRequest().setArtifact(artifact).addRepository(central);
    try {
      var result = system.resolveArtifact(session, request);
      if (result.isResolved()) {
        log.debug("Resolved {}", artifact);
        return true;
      }
    } catch (ArtifactResolutionException e) {
      // ignore
    }
    log.debug("Missing {}", artifact);
    return false;
  }

  /**
   * Resolve an artifact from its coordinates
   *
   * @param coordinates The artifact coordinates in the format {@code
   *     <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
   * @return true if the artifact is resolved, false otherwise
   */
  public boolean resolve(String coordinates) {
    return resolve(new DefaultArtifact(coordinates));
  }

  /**
   * Resolve an artifact from its coordinates
   *
   * @param groupId the groupId
   * @param artifactId the artifactId
   * @param version the version
   * @return true if the artifact is resolved, false otherwise
   */
  public boolean resolve(String groupId, String artifactId, String version) {
    return resolve(new DefaultArtifact(groupId, artifactId, "jar", version));
  }

  /**
   * Publish a list of artifacts to the remote repository
   *
   * @param artifacts the list of artifacts to publish
   * @throws DeploymentException if an error occurs while publishing the artifacts
   */
  private void publish(List<Artifact> artifacts) throws DeploymentException {
    var request = new DeployRequest();

    for (var artifact : artifacts) {
      log.debug("Publishing {}", artifact.getPath());
    }

    request.setArtifacts(artifacts);
    request.setRepository(remote);

    // Sign the artifacts
    var tempFiles = sign(request, artifacts);
    try {
      system.deploy(session, request);
    } finally {
      // Delete the temporary files
      for (var temp : tempFiles) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException e) {
          // Ignore errors
        }
      }
    }

    log.debug("Published {} artifacts", artifacts.size());
  }

  private List<Path> sign(DeployRequest request, List<Artifact> artifacts) {
    if (gpgFactory == null) {
      return List.of();
    }

    try (var signer = gpgFactory.newInstance(session, request)) {
      if (signer == null) return List.of();
      var result = new ArrayList<Path>();
      var signed = signer.generate(artifacts);
      // For some reason, the generated temporary asc files
      // are causing `file not found` errors (may be due to tmpfs?)
      var pomPath =
          artifacts.stream()
              .filter(a -> a.getExtension().equals("pom"))
              .map(Artifact::getPath)
              .findFirst()
              .orElseThrow(() -> new RuntimeException("No pom file found"));
      for (var artifact : signed) {
        var oldPath = artifact.getPath();
        var newPath = pomPath.resolveSibling(oldPath.getFileName());
        try {
          Files.move(oldPath, newPath);
        } catch (IOException e) {
          newPath = oldPath;
        }
        artifact = artifact.setPath(newPath);
        result.add(newPath);
        request.addArtifact(artifact);
      }
      return result;
    }
  }

  private Model readPom(Path pomFile) throws IOException {
    try (var reader = Files.newBufferedReader(pomFile)) {
      return new MavenXpp3Reader().read(reader);
    } catch (XmlPullParserException e) {
      throw new IOException(e);
    }
  }

  /**
   * Publish a Maven artifact
   *
   * @param pom the pom file
   * @param jar the jar file
   * @param sourceJar the source jar file
   * @throws IOException if an error occurs while reading the pom file or publishing the artifact
   */
  public void publish(Path pom, Path jar, Path sourceJar, Path javadocJar) throws IOException {
    var model = readPom(pom);
    var groupId = model.getGroupId();
    var artifactId = model.getArtifactId();
    var version = model.getVersion();
    var artifacts = new ArrayList<Artifact>();

    var isPom = "pom".equals(model.getPackaging());
    var addJar = jar != null && Files.exists(jar) && !isPom;
    var addSourceJar = sourceJar != null && Files.exists(sourceJar) && !isPom;
    var addJavadocJar = javadocJar != null && Files.exists(javadocJar) && !isPom;

    artifacts.add(new DefaultArtifact(groupId, artifactId, "pom", version).setPath(pom));

    if (addJar) {
      artifacts.add(new DefaultArtifact(groupId, artifactId, "jar", version).setPath(jar));
    }
    if (addSourceJar) {
      artifacts.add(
          new DefaultArtifact(groupId, artifactId, "sources", "jar", version).setPath(sourceJar));
    }
    if (addJavadocJar) {
      artifacts.add(
          new DefaultArtifact(groupId, artifactId, "javadoc", "jar", version).setPath(javadocJar));
    }

    try {
      publish(artifacts);
    } catch (DeploymentException e) {
      throw new IOException(e);
    }
  }
}
