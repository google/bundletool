/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.transparency;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.commands.CheckTransparencyCommand;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Executes {@link CheckTransparencyCommand} in APK mode. */
public final class ApkModeTransparencyChecker {

  public static TransparencyCheckResult checkTransparency(CheckTransparencyCommand command) {
    try (TempDirectory tempDir = new TempDirectory("apk-transparency-checker")) {
      return ApkTransparencyCheckUtils.checkTransparency(
          extractAllApksFromZip(command.getApkZipPath().get(), tempDir));
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the file.", e);
    }
  }

  /** Returns list of paths to all .apk files extracted from a .zip file. */
  private static ImmutableList<Path> extractAllApksFromZip(
      Path zipOfApksPath, TempDirectory tempDirectory) throws IOException {
    ImmutableList.Builder<Path> allExtractedApkPaths = ImmutableList.builder();
    Path zipExtractedSubDirectory = tempDirectory.getPath().resolve("extracted");
    Files.createDirectory(zipExtractedSubDirectory);

    try (ZipFile zipOfApks = ZipUtils.openZipFile(zipOfApksPath)) {
      ImmutableList<ZipEntry> listOfApksToExtract =
          zipOfApks.stream()
              .filter(
                  zipEntry ->
                      !zipEntry.isDirectory()
                          && zipEntry.getName().toLowerCase(Locale.ROOT).endsWith(".apk"))
              .collect(toImmutableList());

      for (ZipEntry apkToExtract : listOfApksToExtract) {
        Path extractedApkPath =
            zipExtractedSubDirectory.resolve(ZipPath.create(apkToExtract.getName()).toString());
        Files.createDirectories(extractedApkPath.getParent());
        try (InputStream inputStream = zipOfApks.getInputStream(apkToExtract);
            OutputStream outputApk = Files.newOutputStream(extractedApkPath)) {
          ByteStreams.copy(inputStream, outputApk);
          allExtractedApkPaths.add(extractedApkPath);
        }
      }
    }
    return allExtractedApkPaths.build();
  }

  private ApkModeTransparencyChecker() {}
}
