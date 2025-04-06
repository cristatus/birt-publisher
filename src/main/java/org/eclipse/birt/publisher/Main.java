package org.eclipse.birt.publisher;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

  public static void main(String[] args) throws IOException {
    var config = Config.load();
    var base = Path.of(System.getProperty("base", "target/tmp"));

    var maven = new Maven(base, config.getMaven());

    var sites = config.getSites().stream().map(x -> new Site(x.name, x.url)).toList();
    var publisher = new Publisher(base, config, maven, sites);

    publisher.publish();
  }
}
