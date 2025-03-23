package org.eclipse.birt.publisher.metadata;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

public class ResolvedUnit {

  public String id;

  public String version;

  public String name;

  public String description;

  public MavenCoordinates maven;

  public boolean external;

  public Artifact artifact;
  public Artifact sourceArtifact;

  public Collection<ResolvedUnit> dependencies = new LinkedHashSet<>();

  public Collection<ResolvedUnit> optionalDependencies = new LinkedHashSet<>();

  @Override
  public int hashCode() {
    return Objects.hash(id, version);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof ResolvedUnit
        && Objects.equals(id, ((ResolvedUnit) obj).id)
        && Objects.equals(version, ((ResolvedUnit) obj).version);
  }

  @Override
  public String toString() {
    return String.format("%s,%s", id, version);
  }
}
