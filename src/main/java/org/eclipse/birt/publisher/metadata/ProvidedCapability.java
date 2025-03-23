package org.eclipse.birt.publisher.metadata;

import java.util.Objects;

public class ProvidedCapability {

  public String namespace;

  public String name;

  public String version;

  public boolean isMatch(RequiredCapability requirement) {
    // We ignore the version for now
    if (Objects.equals(namespace, requirement.namespace)
        && Objects.equals(name, requirement.name)) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespace, name, version);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof ProvidedCapability
        && Objects.equals(namespace, ((ProvidedCapability) obj).namespace)
        && Objects.equals(name, ((ProvidedCapability) obj).name)
        && Objects.equals(version, ((ProvidedCapability) obj).version);
  }

  @Override
  public String toString() {
    return String.format("%s,%s,%s", namespace, name, version);
  }
}
