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

import com.android.tools.build.bundletool.model.CommandExecutor.CommandOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.time.Duration;

/** Exposes aapt2 commands used by Bundle Tool. */
public interface Aapt2Command {

  void convertApkProtoToBinary(Path protoApk, Path binaryApk);

  /** Dumps the badging information of an .apk/.apex file, returning a String per line of out. */
  default ImmutableList<String> dumpBadging(Path apkPath) {
    throw new UnsupportedOperationException("Not implemented");
  }

  static Aapt2Command createFromExecutablePath(Path aapt2Path) {
    return new Aapt2Command() {
      private final Duration timeoutMillis = Duration.ofMinutes(5);

      @Override
      public void convertApkProtoToBinary(Path protoApk, Path binaryApk) {
        new DefaultCommandExecutor()
            .execute(
                ImmutableList.of(
                    aapt2Path.toString(),
                    "convert",
                    "--output-format",
                    "binary",
                    "-o",
                    binaryApk.toString(),
                    protoApk.toString()),
                CommandOptions.builder().setTimeout(timeoutMillis).build());
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
}
