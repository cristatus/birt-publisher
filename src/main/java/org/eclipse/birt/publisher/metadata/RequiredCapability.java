package org.eclipse.birt.publisher.metadata;

import java.util.Objects;

public class RequiredCapability {

  public String namespace;

  public String name;

  public String range;

  public boolean optional;

  public boolean greedy;

  @Override
  public int hashCode() {
    return Objects.hash(namespace, name, range);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    return obj instanceof RequiredCapability
        && Objects.equals(namespace, ((RequiredCapability) obj).namespace)
        && Objects.equals(name, ((RequiredCapability) obj).name)
        && Objects.equals(range, ((RequiredCapability) obj).range);
  }

  @Override
  public String toString() {
    return String.format("%s,%s,%s", namespace, name, range);
  }
}
