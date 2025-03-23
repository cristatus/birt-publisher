package org.eclipse.birt.publisher.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MavenCoordinates {

  public String groupId;

  public String artifactId;

  public String version;

  public String classifier;

  public String type;

  public Map<String, String> properties = new HashMap<>();

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version, classifier);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof MavenCoordinates
        && Objects.equals(groupId, ((MavenCoordinates) obj).groupId)
        && Objects.equals(artifactId, ((MavenCoordinates) obj).artifactId)
        && Objects.equals(version, ((MavenCoordinates) obj).version)
        && Objects.equals(classifier, ((MavenCoordinates) obj).classifier);
  }

  @Override
  public String toString() {
    return String.format("%s:%s:%s", groupId, artifactId, version);
  }
}
