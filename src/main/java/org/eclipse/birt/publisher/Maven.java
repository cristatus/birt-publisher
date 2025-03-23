package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven repository client to resolve and publish artifacts
 *
 * <p>This class uses the Aether library to resolve and publish Maven artifacts. It can be used to
 * resolve artifacts from a local or remote repository, and to publish artifacts to a remote
 * repository.
 */
public class Maven {

  private static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

  private static final Logger log = LoggerFactory.getLogger(Maven.class);

  private final RepositorySystem repositorySystem;
  private final RepositorySystemSession session;
  private final RemoteRepository central;

  private String id;
  private String repo;
  private String username;
  private String password;

  /**
   * Create a Maven instance
   *
   * @param local the local repository path
   */
  public Maven(Path local) {
    var repositorySystem = new RepositorySystemSupplier().get();
    var session =
        new SessionBuilderSupplier(repositorySystem)
            .get()
            .withLocalRepositoryBaseDirectories(local)
            .build();
    var central = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL).build();

    this.repositorySystem = repositorySystem;
    this.session = session;
    this.central = central;
  }

  /**
   * Resolve an artifact
   *
   * @param artifact the artifact to resolve
   * @return true if the artifact is resolved, false otherwise
   */
  public boolean resolve(Artifact artifact) {
    log.debug("Resolving {}", artifact);
    var request = new ArtifactRequest().setArtifact(artifact).addRepository(central);
    try {
      var result = repositorySystem.resolveArtifact(session, request);
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
    var artifact = new DefaultArtifact(coordinates);
    return resolve(artifact);
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
    var artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
    return resolve(artifact);
  }

  /**
   * Set the repository credentials
   *
   * @param id the repository id
   * @param repo the repository url
   * @param username the username
   * @param password the password
   */
  public Maven repository(String id, String repo, String username, String password) {
    this.id = id;
    this.repo = repo;
    this.username = username;
    this.password = password;
    return this;
  }

  /**
   * Publish a list of artifacts to the remote repository
   *
   * @param artifacts the list of artifacts to publish
   * @throws DeploymentException if an error occurs while publishing the artifacts
   */
  private void publish(List<Artifact> artifacts) throws DeploymentException {
    if (repo == null) {
      throw new IllegalStateException("Repository credentials not set");
    }

    for (var artifact : artifacts) {
      log.debug("Publishing {}", artifact.getPath());
    }

    var builder = new RemoteRepository.Builder(id, "default", repo);

    if (username != null && password != null) {
      builder.setAuthentication(
          new AuthenticationBuilder().addUsername(username).addPassword(password).build());
    }

    var repository = builder.build();
    var request = new DeployRequest();

    request.setArtifacts(artifacts);
    request.setRepository(repository);

    repositorySystem.deploy(session, request);

    log.debug("Published {} artifacts", artifacts.size());
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
  public void publish(Path pom, Path jar, Path sourceJar) throws IOException {
    var model = readPom(pom);
    var groupId = model.getGroupId();
    var artifactId = model.getArtifactId();
    var version = model.getVersion();
    var artifacts = new ArrayList<Artifact>();

    var isPom = "pom".equals(model.getPackaging());
    var addJar = jar != null && Files.exists(jar) && !isPom;
    var addSourceJar = sourceJar != null && Files.exists(sourceJar) && !isPom;

    artifacts.add(new DefaultArtifact(groupId, artifactId, "pom", version).setPath(pom));

    if (addJar) {
      artifacts.add(new DefaultArtifact(groupId, artifactId, "jar", version).setPath(jar));
    }
    if (addSourceJar) {
      artifacts.add(
          new DefaultArtifact(groupId, artifactId, "sources", "jar", version).setPath(sourceJar));
    }

    try {
      publish(artifacts);
    } catch (DeploymentException e) {
      throw new IOException(e);
    }
  }
}
