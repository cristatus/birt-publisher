package org.eclipse.birt.publisher.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.eclipse.birt.publisher.Site;

public class Artifact {

  public String id;

  public String version;

  public String classifier;

  public Map<String, String> properties = new HashMap<>();

  public MavenCoordinates maven;

  public Site site;

  public String url;
  public String file;
  public String size;
  public String sha1;
  public String sha512;
  public String sha256;

  @Override
  public int hashCode() {
    return Objects.hash(id, version, classifier);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof Artifact
        && Objects.equals(id, ((Artifact) obj).id)
        && Objects.equals(version, ((Artifact) obj).version)
        && Objects.equals(classifier, ((Artifact) obj).classifier);
  }

  @Override
  public String toString() {
    return String.format("%s,%s", id, version);
  }
}
