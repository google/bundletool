/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.model;

import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Exposes aapt2 commands used by Bundle Tool. */
public interface Aapt2Command {

  void convertApkProtoToBinary(Path protoApk, Path binaryApk);

  /** Dumps the badging information of an .apk/.apex file, returning a String per line of out. */
  default ImmutableList<String> dumpBadging(Path apkPath) {
    throw new UnsupportedOperationException("Not implemented");
  }

  static Aapt2Command createFromExecutablePath(Path aapt2Path) {
    return new Aapt2Command() {
      @Override
      public void convertApkProtoToBinary(Path protoApk, Path binaryApk) {
        new CommandExecutor()
            .execute(
                aapt2Path.toString(),
                "convert",
                "--output-format",
                "binary",
                "-o",
                binaryApk.toString(),
                protoApk.toString());
      }

      @Override
      public ImmutableList<String> dumpBadging(Path apkPath) {
        return new CommandExecutor()
            .executeAndCapture(aapt2Path.toString(), "dump", "badging", apkPath.toString());
      }
    };
  }

  /** Helper to execute aapt2 commands. */
  class CommandExecutor {
    private static final int TIMEOUT_AAPT2_COMMANDS_SECONDS = 5 * 60; // 5 minutes.

    public void execute(String... command) {
      executeImpl(command);
    }

    public ImmutableList<String> executeAndCapture(String... command) {
      return captureOutput(executeImpl(command));
    }

    private static Process executeImpl(String... command) {
      try {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(TIMEOUT_AAPT2_COMMANDS_SECONDS, TimeUnit.SECONDS)) {
          printOutput(process);
          throw new Aapt2Exception("Command timed out: " + Arrays.toString(command));
        }
        if (process.exitValue() != 0) {
          printOutput(process);
          throw new Aapt2Exception(
              String.format(
                  "Command '%s' didn't terminate successfully (exit code: %d). Check the logs.",
                  Arrays.toString(command), process.exitValue()));
        }
        return process;
      } catch (IOException | InterruptedException e) {
        throw new Aapt2Exception("Error when executing command: " + Arrays.toString(command), e);
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
        System.err.println("Error when printing output of aapt2 command:" + e.getMessage());
      }
    }
  }

  /**
   * Exception thrown during execution of aapt2.
   *
   * <p>This does not extend CommandExecutionException on purpose because the message may contain
   * paths to files.
   */
  class Aapt2Exception extends RuntimeException {
    private Aapt2Exception(String message) {
      super(message);
    }

    private Aapt2Exception(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
