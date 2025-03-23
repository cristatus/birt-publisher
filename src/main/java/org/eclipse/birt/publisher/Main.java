package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

  public static void main(String[] args) throws IOException {
    var config = Config.load();
    var base = Path.of(System.getProperty("base", "target/tmp"));
    var repo = System.getProperty("maven.repo", base.resolve("repo").toUri().toString());
    var username = System.getProperty("maven.username");
    var password = System.getProperty("maven.password");

    var m2 = base.resolve(".m2");
    var maven = new Maven(m2).repository(null, repo, username, password);

    var sites = config.getSites().stream().map(x -> new Site(x.name, x.url)).toList();
    var publisher = new Publisher(base, config, maven, sites);

    publisher.publish();
  }
}
