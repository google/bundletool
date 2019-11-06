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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Exposes aapt2 commands used by Bundle Tool. */
public interface Aapt2Command {

  void convertApkProtoToBinary(Path protoApk, Path binaryApk);
  String getPackageNameFromApk(Path binaryApk);

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

      // Try to match:
      // package: name='me.example.dynamicfeatures' versionCode='3' versionName='1.0' platformBuildVersionName='' platformBuildVersionCode='' compileSdkVersion='28' compileSdkVersionCodename='9'
      @Override
      public String getPackageNameFromApk(Path binaryApk) {
        AtomicReference<String> packageName = new AtomicReference<>();
        try {
          new CommandExecutor()
                  .execute(line -> {
                            if (line.startsWith("package: ")) {
                              packageName.set(line.replaceAll(".* name='([^']+)'.*", "$1"));
                            }
                          },
                          aapt2Path.toString(),
                          "dump",
                          "badging",
                          binaryApk.toString()
                  );
        } catch (Aapt2Exception e) {
          // dump badging returns with error code 1
          // we don't care about the error,
          // as long as we were able to parse the package name (below)
        }
        if (packageName.get() == null) {
          throw new Aapt2Exception("Cannot find application id of APK.");
        }
        return packageName.get();
      }
    };
  }

  /** Helper to execute aapt2 commands. */
  class CommandExecutor {
    private static final int TIMEOUT_AAPT2_COMMANDS_SECONDS = 5 * 60; // 5 minutes.

    public void execute(String... command) {
      execute(System.err::println, command);
    }

    public void execute(Consumer<String> outputConsumer, String... command) {
      try {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(TIMEOUT_AAPT2_COMMANDS_SECONDS, TimeUnit.SECONDS)) {
          printOutput(process, outputConsumer);
          throw new Aapt2Exception("Command timed out: " + Arrays.toString(command));
        }
        if (process.exitValue() != 0) {
          printOutput(process, outputConsumer);
          throw new Aapt2Exception(
              String.format(
                  "Command '%s' didn't terminate successfully (exit code: %d). Check the logs.",
                  Arrays.toString(command), process.exitValue()));
        }
      } catch (IOException | InterruptedException e) {
        throw new Aapt2Exception("Error when executing command: " + Arrays.toString(command), e);
      }
    }

    private static void printOutput(Process process, Consumer<String> outputConsumer) {
      try (BufferedReader outputReader = BufferedIo.reader(process.getInputStream())) {
        String line;
        while ((line = outputReader.readLine()) != null) {
          outputConsumer.accept(line);
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
