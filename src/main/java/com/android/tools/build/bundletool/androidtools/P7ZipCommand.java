/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

/** Exposes 7zip commands used by Bundle Tool. */
public interface P7ZipCommand {

  /**
   * Compress all files inside {@code inputDirectoryPath} into ZIP file specified by {@code
   * outputPath}.
   */
  void compress(Path outputPath, Path inputDirectoryPath);

  /**
   * Creates default implementation of 7zip command given path to 7zip executable and number of
   * threads that should be used for compression.
   */
  public static P7ZipCommand defaultP7ZipCommand(Path p7zipExecutable, int numThreads) {
    return (outputPath, inputDirectoryPath) -> {
      ImmutableList<String> command =
          ImmutableList.of(
              p7zipExecutable.toString(),
              "a",
              "-tzip",
              "-mtc=off",
              "-mx=9",
              String.format("-mmt=%d", numThreads),
              "-bso0",
              "-bsp0",
              "-r",
              outputPath.toAbsolutePath().normalize().toString(),
              String.join(
                  File.separator, inputDirectoryPath.toAbsolutePath().normalize().toString(), "*"));
      new DefaultCommandExecutor()
          .execute(command, CommandOptions.builder().setTimeout(Duration.ofMinutes(10)).build());
    };
  }
}
