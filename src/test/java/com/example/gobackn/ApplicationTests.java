package com.example.gobackn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ApplicationTests {

  @Test
  void mainPrintsGreeting() {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();

    try (PrintStream replacement = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
      System.setOut(replacement);
      Application.main(new String[0]);
    } finally {
      System.setOut(originalOut);
    }

    assertEquals("Hello from go-back-n", capturedOutput.toString(StandardCharsets.UTF_8).trim());
  }
}
