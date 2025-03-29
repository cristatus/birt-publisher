# BIRT Runtime Publisher

Publish BIRT Runtime to maven repository.

## Requirements

- JDK 21
- Apache Maven

## Usage

Compile the project:

```sh
mvn compile
```

Run the publisher with the required parameters:

```sh
mvn exec:java \
  -Dmaven.repo=https://repo.example.com/birt \
  -Dmaven.username=username \
  -Dmaven.password=password
```

## Testing

To deploy artifacts to a local repository (`tmp/repo`) for testing:

```sh
mvn compile exec:java
```

Run unit tests:

```sh
mvn test
```

## Custom Maven Group ID

Use the `maven.group` system property to specify a custom Maven group ID:

```sh
mvn compile exec:java -Dmaven.group=com.example.birt
```

## How It Works

The application uses a configuration file to define artifact details, P2 update sites, and mapping rules. A sample configuration file:

```json
{
  "details": [
    {
      "group": "org.eclipse.birt",
      "name": "technology.birt",
      "scm": "https://github.com/eclipse-birt/birt"
    },
    {
      "group": "org.eclipse.datatools.plugins",
      "name": "tools.datatools",
      "scm": "https://github.com/eclipse-datatools/datatools"
    }
  ],
  "sites": [
    {
      "name": "birt",
      "url": "https://mirrors.dotsrc.org/eclipse/birt/updates/release/4.19.0"
    },
    {
      "name": "datatools",
      "url": "https://mirrors.dotsrc.org/eclipse/datatools/updates/release/1.16.3"
    },
    {
      "name": "orbit",
      "url": "https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/release/4.35.0"
    }
  ],
  "mappings": [
    {
      "pattern": "(org\\.eclipse\\.(equinox|osgi)):.*",
      "groupId": "org.eclipse.platform"
    },
    {
      "pattern": "(org\\.eclipse\\.orbit):(derby):(.*)",
      "groupId": "org.apache.derby"
    },
    {
      "pattern": "(org\\.eclipse\\.birt\\.features):(org\\.eclipse\\.birt\\.engine\\.runtime):(.*)",
      "groupId": "org.eclipse.birt",
      "artifactId": "birt-runtime"
    },
    {
      "pattern": "org\\.apache\\.commons\\.net",
      "groupId": "commons-net",
      "artifactId": "commons-net",
      "version": "3.2"
    }
  ],
  "nocheck": [
    { "pattern": "org\\.eclipse\\.birt.*" },
    { "pattern": "org\\.eclipse\\.datatools.*" }
  ],
  "publish": [
    { "id": "org.eclipse.birt.engine.runtime.feature.group" }
  ]
}
```

### Processing Steps

1. The application scans all defined P2 update sites.
2. It constructs a dependency graph of installable units.
3. Maven coordinates are adjusted according to the mapping rules.
4. Artifacts are checked against Maven Central to avoid duplicate publishing.
5. Missing artifacts are downloaded, POM files are generated, and the artifacts are published to the specified Maven repository.
6. `.feature.group` artifacts are published as POM-type artifacts.
7. Corresponding source artifacts are identified and published where available.

### Configuration Options

- **`details`** – Metadata for POM generation (e.g., SCM information)
- **`sites`** – P2 update sites to scan
- **`mappings`** – Rules to adjust Maven coordinates
- **`nocheck`** – Artifacts to exclude from Maven Central checks
- **`publish`** – List of units to publish
