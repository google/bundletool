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
package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.LargeFileSource;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/** Interface for ApkSet writer. */
public interface ApkSetWriter {

  Path getSplitsDirectory();

  void writeApkSet(BuildApksResult toc) throws IOException;

  void writeApkSetWithoutToc(BuildApksResult toc) throws IOException;

  void writeApkSet(BuildSdkApksResult toc) throws IOException;


  /** Creates ApkSet writer which stores all splits uncompressed inside output directory. */
  static ApkSetWriter directory(Path outputDirectory) {
    return new ApkSetWriter() {
      @Override
      public Path getSplitsDirectory() {
        return outputDirectory;
      }

      @Override
      public void writeApkSet(BuildApksResult toc) throws IOException {
        Files.write(getSplitsDirectory().resolve(TABLE_OF_CONTENTS_FILE), toc.toByteArray());
      }

      @Override
      public void writeApkSetWithoutToc(BuildApksResult toc) {
        // No-op for directory APK set writer.
      }

      @Override
      public void writeApkSet(BuildSdkApksResult toc) throws IOException {
        Files.write(getSplitsDirectory().resolve(TABLE_OF_CONTENTS_FILE), toc.toByteArray());
      }

    };
  }

  /** Creates ApkSet writer which stores all splits as ZIP archive. */
  static ApkSetWriter zip(Path tempDirectory, Path outputFile) {
    return new ApkSetWriter() {
      @Override
      public Path getSplitsDirectory() {
        return tempDirectory;
      }

      @Override
      public void writeApkSet(BuildApksResult toc) throws IOException {
        zipApkSet(getApkRelativePaths(toc), toc.toByteArray());
      }

      @Override
      public void writeApkSetWithoutToc(BuildApksResult toc) throws IOException {
        zipApkSet(getApkRelativePaths(toc), toc.toByteArray(), /* serializeToc= */ false);
      }

      @Override
      public void writeApkSet(BuildSdkApksResult toc) throws IOException {
        Stream<ApkDescription> apks =
            toc.getVariantList().stream()
                .flatMap(variant -> variant.getApkSetList().stream())
                .flatMap(apkSet -> apkSet.getApkDescriptionList().stream());

        ImmutableSet<String> apkRelativePaths =
            apks.map(ApkDescription::getPath).sorted().collect(toImmutableSet());

        zipApkSet(apkRelativePaths, toc.toByteArray());
      }


      private void zipApkSet(ImmutableSet<String> apkRelativePaths, byte[] tocBytes)
          throws IOException {
        zipApkSet(apkRelativePaths, tocBytes, /* serializeToc= */ true);
      }

      private void zipApkSet(
          ImmutableSet<String> apkRelativePaths, byte[] tocBytes, boolean serializeToc)
          throws IOException {
        try (ZipArchive zipArchive = new ZipArchive(outputFile)) {
          if (serializeToc) {
            zipArchive.add(
                new BytesSource(tocBytes, TABLE_OF_CONTENTS_FILE, Deflater.NO_COMPRESSION));
          }

          for (String relativePath : apkRelativePaths) {
            zipArchive.add(
                new LargeFileSource(
                    getSplitsDirectory().resolve(relativePath),
                    /* tmpStorage= */ null,
                    relativePath,
                    Deflater.NO_COMPRESSION));
          }
        }
      }

      private ImmutableSet<String> getApkRelativePaths(BuildApksResult toc) {
        Stream<ApkDescription> apks =
            toc.getVariantList().stream()
                .flatMap(variant -> variant.getApkSetList().stream())
                .flatMap(apkSet -> apkSet.getApkDescriptionList().stream());
        Stream<ApkDescription> assets =
            toc.getAssetSliceSetList().stream()
                .flatMap(assetSliceSet -> assetSliceSet.getApkDescriptionList().stream());

        return Stream.concat(apks, assets)
            .map(ApkDescription::getPath)
            .sorted()
            .collect(toImmutableSet());
      }
    };
  }
}
