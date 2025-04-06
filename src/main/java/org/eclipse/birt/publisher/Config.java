package org.eclipse.birt.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {

  public static final String MAVEN_PROFILE = "maven.profile";
  public static final String MAVEN_REPO_ID = "maven.repo.id";
  public static final String MAVEN_REPO_URL = "maven.repo.url";
  public static final String MAVEN_REPO_USERNAME = "maven.repo.username";
  public static final String MAVEN_REPO_PASSWORD = "maven.repo.password";

  public static final String MAVEN_GROUP = "maven.group";
  public static final String MAVEN_RESOLVE = "maven.resolve";

  public static class InfoConfig {
    public String group;
    public String name;
    public String scm;
    public String id;
  }

  public static class SiteConfig {
    public String name;
    public String url;
  }

  public static class MappingConfig {
    public String pattern;
    public String groupId;
    public String artifactId;
    public String version;
  }

  public static class PublishConfig {
    public String id;
    public String pattern;
  }

  public static class MavenConfig {
    public String repoId;
    public String repoUrl;
    public String profile;
    public String username;
    public String password;
    public String group;
    public Boolean resolve;
  }

  private List<SiteConfig> sites;

  private final List<InfoConfig> details = new ArrayList<>();

  private final List<MappingConfig> mappings = new ArrayList<>();
  private final List<PublishConfig> candidates = new ArrayList<>();
  private final List<PublishConfig> exclude = new ArrayList<>();
  private final List<PublishConfig> publish = new ArrayList<>();

  public List<SiteConfig> getSites() {
    return sites;
  }

  public List<InfoConfig> getDetails() {
    return details;
  }

  public List<MappingConfig> getMappings() {
    return mappings;
  }

  public List<PublishConfig> getCandidates() {
    return candidates;
  }

  public List<PublishConfig> getExclude() {
    return exclude;
  }

  public List<PublishConfig> getPublish() {
    return publish;
  }

  public MavenConfig getMaven() {
    var maven = new MavenConfig();
    maven.repoId = System.getProperty(MAVEN_REPO_ID);
    maven.repoUrl = System.getProperty(MAVEN_REPO_URL);
    maven.profile = System.getProperty(MAVEN_PROFILE);
    maven.username = System.getProperty(MAVEN_REPO_USERNAME);
    maven.password = System.getProperty(MAVEN_REPO_PASSWORD);
    maven.group = System.getProperty(MAVEN_GROUP);
    maven.resolve = Boolean.getBoolean(MAVEN_RESOLVE);
    return maven;
  }

  public static Config load() throws IOException {
    try (var stream = Config.class.getResourceAsStream("/config.json")) {
      return load(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Config load(Path file) throws IOException {
    try (var stream = Files.newBufferedReader(file)) {
      return load(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Config load(InputStream stream) throws IOException {
    return load(new InputStreamReader(stream));
  }

  public static Config load(Reader reader) throws IOException {
    return new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        .readValue(reader, Config.class);
  }
}
