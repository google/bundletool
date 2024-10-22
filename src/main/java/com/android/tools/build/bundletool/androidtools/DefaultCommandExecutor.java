/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.tools.build.bundletool.androidtools;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableList;
import com.google.common.io.LineReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

/** Helper to execute native commands. */
public final class DefaultCommandExecutor implements CommandExecutor {

  @Override
  public void execute(ImmutableList<String> command, CommandOptions options) {
    ImmutableList<String> capturedOutput = executeImpl(command, options);
    printOutput(capturedOutput, System.out);
  }

  @Override
  public ImmutableList<String> executeAndCapture(
      ImmutableList<String> command, CommandOptions options) {
    return executeImpl(command, options);
  }

  private static ImmutableList<String> executeImpl(
      ImmutableList<String> command, CommandOptions options) {
    try {
      Process process =
          new ProcessBuilder(command)
              .redirectOutput(Redirect.PIPE)
              .redirectErrorStream(true)
              .start();

      OutputCapturer outputCapturer = OutputCapturer.startCapture(process.getInputStream());

      if (!process.waitFor(options.getTimeout().toMillis(), MILLISECONDS)) {
        printOutput(outputCapturer.getOutput(/* interrupt= */ true), System.err);
        throw CommandExecutionException.builder()
            .withInternalMessage("Command timed out: %s", command)
            .build();
      }
      if (process.exitValue() != 0) {
        printOutput(outputCapturer.getOutput(/* interrupt= */ true), System.err);
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Command '%s' didn't terminate successfully (exit code: %d). Check the logs.",
                command, process.exitValue())
            .build();
      }
      return outputCapturer.getOutput(/* interrupt= */ false);
    } catch (IOException | InterruptedException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Error when executing command: %s", command)
          .withCause(e)
          .build();
    }
  }

  static class OutputCapturer {
    private final Thread thread;
    private final List<String> output;
    private final InputStream stream;

    private OutputCapturer(Thread thread, List<String> output, InputStream stream) {
      this.thread = thread;
      this.output = output;
      this.stream = stream;
    }

    static OutputCapturer startCapture(InputStream stream) {
      List<String> output = new ArrayList<>();
      Thread thread =
          new Thread(
              () -> {
                try (BufferedReader reader = BufferedIo.reader(stream)) {
                  LineReader lineReader = new LineReader(reader);
                  String line;
                  while ((line = lineReader.readLine()) != null) {
                    output.add(line);
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      thread.start();
      return new OutputCapturer(thread, output, stream);
    }

    ImmutableList<String> getOutput(boolean interrupt) throws InterruptedException, IOException {
      if (interrupt) {
        stream.close();
      }
      thread.join();
      return ImmutableList.copyOf(output);
    }
  }

  private static void printOutput(List<String> output, PrintStream stream) {
    for (String line : output) {
      stream.println(line);
    }
  }
}
