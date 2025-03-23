package org.eclipse.birt.publisher.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.birt.publisher.Site;

public class InstallableUnit {

  public String id;

  public String version;

  public String name;

  public String description;

  public Map<String, String> properties = new HashMap<>();

  public List<ProvidedCapability> provides = new ArrayList<>();

  public List<RequiredCapability> requires = new ArrayList<>();

  public List<Artifact> artifacts = new ArrayList<>();

  public MavenCoordinates maven;

  public Site site;

  public boolean isMatch(RequiredCapability requirement) {
    for (var capability : provides) {
      if (capability.isMatch(requirement)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, version);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof InstallableUnit
        && Objects.equals(id, ((InstallableUnit) obj).id)
        && Objects.equals(version, ((InstallableUnit) obj).version);
  }

  @Override
  public String toString() {
    return String.format("%s,%s", id, version);
  }
}
