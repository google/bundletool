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

package com.android.tools.build.bundletool.androidtools;

import com.android.tools.build.bundletool.androidtools.CommandExecutor.CommandOptions;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Exposes aapt2 commands used by Bundle Tool. */
public interface Aapt2Command {

  void convertApkProtoToBinary(Path protoApk, Path binaryApk, ConvertOptions convertOptions);

  void optimizeToSparseResourceTables(Path originalApk, Path outputApk);

  /** Dumps the badging information of an .apk/.apex file, returning a String per line of out. */
  default ImmutableList<String> dumpBadging(Path apkPath) {
    throw new UnsupportedOperationException("Not implemented");
  }

  static Aapt2Command createFromExecutablePath(Path aapt2Path) {
    return new Aapt2Command() {
      private final Duration timeoutMillis = Duration.ofMinutes(5);

      @Override
      public void convertApkProtoToBinary(
          Path protoApk, Path binaryApk, ConvertOptions convertOptions) {
        ImmutableList.Builder<String> convertCommand =
            ImmutableList.<String>builder().add(aapt2Path.toString()).add("convert");
        if (convertOptions.getForceSparseEncoding()) {
          convertCommand.add("--force-sparse-encoding");
        }
        if (convertOptions.getCollapseResourceNames()) {
          convertCommand.add("--collapse-resource-names");
        }
        if (convertOptions.getDeduplicateResourceEntries()) {
          convertCommand.add("--deduplicate-entry-values");
        }
        convertOptions
            .getResourceConfigPath()
            .ifPresent(
                path ->
                    convertCommand
                        .add("--resources-config-path")
                        .add(path.toAbsolutePath().toString()));
        convertCommand
            .add("--output-format")
            .add("binary")
            .add("-o")
            .add(binaryApk.toString())
            .add(protoApk.toString());

        new DefaultCommandExecutor()
            .execute(
                convertCommand.build(), CommandOptions.builder().setTimeout(timeoutMillis).build());
      }

      @Override
      public void optimizeToSparseResourceTables(Path originalApk, Path outputApk) {
        ImmutableList<String> convertCommand =
            ImmutableList.of(
                aapt2Path.toString(),
                "optimize",
                "--enable-sparse-encoding",
                "-o",
                outputApk.toString(),
                originalApk.toString());

        new DefaultCommandExecutor()
            .execute(convertCommand, CommandOptions.builder().setTimeout(timeoutMillis).build());
      }

      @Override
      public ImmutableList<String> dumpBadging(Path apkPath) {
        return new DefaultCommandExecutor()
            .executeAndCapture(
                ImmutableList.of(aapt2Path.toString(), "dump", "badging", apkPath.toString()),
                CommandOptions.builder().setTimeout(timeoutMillis).build());
      }
    };
  }

  /** Options for 'aapt2 convert' command. */
  @AutoValue
  abstract class ConvertOptions {
    public abstract boolean getForceSparseEncoding();

    public abstract boolean getCollapseResourceNames();

    public abstract Optional<Path> getResourceConfigPath();

    public abstract boolean getDeduplicateResourceEntries();

    public static Builder builder() {
      return new AutoValue_Aapt2Command_ConvertOptions.Builder()
          .setForceSparseEncoding(false)
          .setCollapseResourceNames(false)
          .setDeduplicateResourceEntries(false);
    }

    /** Builder for {@link ConvertOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setForceSparseEncoding(boolean value);

      public abstract Builder setCollapseResourceNames(boolean value);

      public abstract Builder setResourceConfigPath(Optional<Path> resourceConfigPath);

      public abstract Builder setDeduplicateResourceEntries(boolean value);

      public abstract ConvertOptions build();
    }
  }
}
