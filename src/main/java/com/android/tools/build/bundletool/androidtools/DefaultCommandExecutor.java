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
import com.google.common.io.CharStreams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Helper to execute native commands. */
public final class DefaultCommandExecutor implements CommandExecutor {

  @Override
  public void execute(ImmutableList<String> command, CommandOptions options) {
    executeImpl(command, options);
  }

  @Override
  public ImmutableList<String> executeAndCapture(
      ImmutableList<String> command, CommandOptions options) {
    return captureOutput(executeImpl(command, options));
  }

  private static Process executeImpl(ImmutableList<String> command, CommandOptions options) {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      if (!process.waitFor(options.getTimeout().toMillis(), MILLISECONDS)) {
        printOutput(process);
        throw CommandExecutionException.builder()
            .withInternalMessage("Command timed out: %s", command)
            .build();
      }
      if (process.exitValue() != 0) {
        printOutput(process);
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Command '%s' didn't terminate successfully (exit code: %d). Check the logs.",
                command, process.exitValue())
            .build();
      }
      return process;
    } catch (IOException | InterruptedException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Error when executing command: %s", command)
          .withCause(e)
          .build();
    }
  }

  private static ImmutableList<String> captureOutput(Process process) {
    try (BufferedReader outputReader = BufferedIo.reader(process.getInputStream())) {
      return ImmutableList.copyOf(CharStreams.readLines(outputReader));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void printOutput(Process process) {
    try (BufferedReader outputReader = BufferedIo.reader(process.getInputStream())) {
      String line;
      while ((line = outputReader.readLine()) != null) {
        System.err.println(line);
      }
    } catch (IOException e) {
      System.err.println("Error when printing output of command:" + e.getMessage());
    }
  }
}
