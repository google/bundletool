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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.google.protobuf.Message;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Factory for {@link ApkSetBuilder}. */
public final class ApkSetBuilderFactory {

  /** Handles adding of {@link ModuleSplit} to the APK Set archive. */
  public interface ApkSetBuilder {
    /** Adds a split APK to the APK Set archive. */
    ApkDescription addSplitApk(ModuleSplit split);

    /** Adds a standalone APK to the APK Set archive. */
    ApkDescription addStandaloneApk(ModuleSplit split);

    /** Adds a standalone universal APK to the APK Set archive. */
    ApkDescription addStandaloneUniversalApk(ModuleSplit split);

    /** Sets the TOC file in the APK Set archive. */
    void setTableOfContentsFile(BuildApksResult tableOfContentsProto);

    /** Writes out the APK Set archive to the specified destination. */
    void writeTo(Path destinationPath);
  }

  public static ApkSetBuilder createApkSetBuilder(
      SplitApkSerializer splitApkSerializer,
      StandaloneApkSerializer standaloneApkSerializer,
      Path tempDir) {
    return new ApkSetArchiveBuilder(splitApkSerializer, standaloneApkSerializer, tempDir);
  }


  /** ApkSet builder that stores the generated APKs in the Apk Set archive. */
  public static class ApkSetArchiveBuilder implements ApkSetBuilder {
    private final SplitApkSerializer splitApkSerializer;
    private final StandaloneApkSerializer standaloneApkSerializer;
    private final ZipBuilder apkSetZipBuilder;
    private final Path tempDirectory;

    public ApkSetArchiveBuilder(
        SplitApkSerializer splitApkSerializer,
        StandaloneApkSerializer standaloneApkSerializer,
        Path tempDirectory) {
      this.splitApkSerializer = splitApkSerializer;
      this.standaloneApkSerializer = standaloneApkSerializer;
      this.tempDirectory = tempDirectory;
      this.apkSetZipBuilder = new ZipBuilder();
    }

    @Override
    public ApkDescription addSplitApk(ModuleSplit split) {
      ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription);
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneApk(ModuleSplit split) {
      ApkDescription apkDescription = standaloneApkSerializer.writeToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription);
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneUniversalApk(ModuleSplit split) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeToDiskAsUniversal(split, tempDirectory);
      addToApkSetArchive(apkDescription);
      return apkDescription;
    }

    private void addToApkSetArchive(ApkDescription apkDescription) {
      Path apkPath = tempDirectory.resolve(apkDescription.getPath());
      checkFileExistsAndReadable(apkPath);
      apkSetZipBuilder.addFileFromDisk(
          ZipPath.create(apkDescription.getPath()), apkPath.toFile(), EntryOption.UNCOMPRESSED);
    }

    @Override
    public void setTableOfContentsFile(BuildApksResult tableOfContentsProto) {
      apkSetZipBuilder.addFileWithProtoContent(
          ZipPath.create(TABLE_OF_CONTENTS_FILE), tableOfContentsProto);
    }

    @Override
    public void writeTo(Path destinationPath) {
      try {
        apkSetZipBuilder.writeTo(destinationPath);
      } catch (IOException e) {
        throw new UncheckedIOException(
            String.format("Error while writing the APK Set archive to '%s'.", destinationPath), e);
      }
    }
  }

}
