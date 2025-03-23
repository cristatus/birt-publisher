# BIRT Runtime Publisher

Publish BIRT Runtime to maven repository.

## Usage

Requires JDK 21.

```sh
$ mvn compile
$ mvn exec:java \
  -Dmaven.repo=https://repo.example.com/birt \
  -Dmaven.username=username \
  -Dmaven.password=password
```

## Test

Run following command to deploy to a local repo `tmp/repo` first:

```sh
$ mvn compile exec:java
```

Run tests:

```sh
$ mvn test
```
