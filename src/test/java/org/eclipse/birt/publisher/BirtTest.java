package org.eclipse.birt.publisher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.birt.report.engine.api.ReportRunner;
import org.junit.jupiter.api.Test;

public class BirtTest {

  private String extractText(Path pdf) throws IOException {
    var sb = new StringBuilder();
    try (var reader = new PdfReader(pdf.toString())) {
      var extractor = new PdfTextExtractor(reader);
      for (var i = 1; i <= reader.getNumberOfPages(); i++) {
        sb.append(extractor.getTextFromPage(i));
      }
    }
    return sb.toString();
  }

  private String run(String format) throws IOException {
    var design = "target/test-classes/hello_world.rptdesign";
    var output = Path.of("target/hello_world." + format);

    var args =
        new String[] {
          "-o",
          output.toString(),
          "-f",
          format,
          "-m",
          "RunAndRender",
          "-p",
          "paramString=my parameter",
          "-p",
          "paramInteger=1",
          "-p",
          "paramList=1,2,3",
          design
        };

    Files.deleteIfExists(output);

    var runner = new ReportRunner(args);
    var exitCode = runner.execute();

    if (exitCode != 0 || Files.notExists(output)) {
      throw new IOException("Failed to generate report");
    }

    return "pdf".equals(format) ? extractText(output) : Files.readString(output);
  }

  private void checkOutput(String text) {
    assertTrue(text.contains("Congratulations!"));
    assertTrue(
        text.contains(
            "If you can see this report, it means that the BIRT Engine is installed correctly."));
  }

  @Test
  public void testRunner() throws Exception {
    var formats = List.of("html", "pdf");
    for (var format : formats) {
      var text = run(format);
      checkOutput(text);
    }
  }
}
