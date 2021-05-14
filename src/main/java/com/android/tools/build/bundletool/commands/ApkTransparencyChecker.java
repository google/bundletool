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
package com.android.tools.build.bundletool.commands;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks code transparency for a given set of a device-specific APK files. */
final class ApkTransparencyChecker {

  private static final String TRANSPARENCY_FILE_ZIP_ENTRY_NAME =
      "META-INF/" + BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME;

  static void checkTransparency(CheckTransparencyCommand command, PrintStream outputStream) {
    try (TempDirectory tempDir = new TempDirectory("apk-transparency-checker")) {
      ImmutableList<Path> allApkPaths =
          extractAllApksFromZip(command.getApkZipPath().get(), tempDir);
      Optional<Path> baseApkPath = getBaseApkPath(allApkPaths);
      if (!baseApkPath.isPresent()) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "The provided .zip file must either contain a single APK, or, if multiple APK"
                    + " files are present, a base APK.")
            .build();
      }

      ZipFile baseApkFile = ZipUtils.openZipFile(baseApkPath.get());
      Optional<ZipEntry> transparencyFileEntry =
          Optional.ofNullable(baseApkFile.getEntry(TRANSPARENCY_FILE_ZIP_ENTRY_NAME));
      if (!transparencyFileEntry.isPresent()) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "Could not verify code transparency because transparency file is not present in the"
                    + " APK.")
            .build();
      }
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

    ZipFile zipOfApks = ZipUtils.openZipFile(zipOfApksPath);
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

    return allExtractedApkPaths.build();
  }

  private static Optional<Path> getBaseApkPath(ImmutableList<Path> apkPaths) {
    // If only 1 APK is present in the archive, it is assumed to be a universal or standalone APK.
    if (apkPaths.size() == 1) {
      return apkPaths.get(0).getFileName().toString().endsWith(".apk")
          ? Optional.of(apkPaths.get(0))
          : Optional.empty();
    }
    return apkPaths.stream()
        .filter(apkPath -> apkPath.getFileName().toString().equals("base.apk"))
        .findAny();
  }

  private ApkTransparencyChecker() {}
}
