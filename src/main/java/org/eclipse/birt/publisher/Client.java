package org.eclipse.birt.publisher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

  private static final Logger log = LoggerFactory.getLogger(Client.class);

  private static <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler)
      throws IOException, InterruptedException {
    try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
      return client.send(request, handler);
    }
  }

  public static void download(String url, Path file) {
    if (Files.exists(file)) {
      return;
    }

    log.debug("Downloading {}", url);

    var temp = file.resolveSibling(file.getFileName() + ".part");
    var request = HttpRequest.newBuilder().uri(URI.create(url)).build();

    try {
      Files.createDirectories(file.getParent());
      send(request, BodyHandlers.ofFile(temp));
      Files.move(temp, file);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException e) {
        // Ignore
      }
    }
  }

  public static void verify(Path file, String checksum) {
    verify(file, checksum, "SHA-512");
  }

  public static void verify(Path file, String checksum, String algorithm) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }

    try (var stream = Files.newInputStream(file)) {
      var buffer = new byte[4096];
      var read = 0;
      while ((read = stream.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // generate hex string from the digest
    var actual = new StringBuilder();
    for (var b : digest.digest()) {
      actual.append(String.format("%02x", b));
    }

    if (!checksum.equalsIgnoreCase(actual.toString())) {
      throw new RuntimeException("Checksum mismatch");
    }
  }

  public static void extract(Path zipFile, Path dir) throws IOException {
    Files.createDirectories(dir);
    try (var zip = new ZipFile(zipFile.toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        var entryFile = dir.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(entryFile);
        } else {
          Files.createDirectories(entryFile.getParent());
          Files.copy(zip.getInputStream(entry), entryFile, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
