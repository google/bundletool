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

import static com.android.tools.build.bundletool.model.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Factory for {@link ApkSetBuilder}. */
public final class ApkSetBuilderFactory {

  /** Handles adding of {@link ModuleSplit} to the APK Set archive. */
  public interface ApkSetBuilder {
    /** Adds a split APK to the APK Set archive. */
    ApkDescription addSplitApk(ModuleSplit split, ZipPath apkPath);

    /** Adds a standalone APK to the APK Set archive. */
    ApkDescription addStandaloneApk(ModuleSplit split, ZipPath apkPath);

    /** Adds a standalone universal APK to the APK Set archive. */
    ApkDescription addStandaloneUniversalApk(ModuleSplit split);

    /** Adds an instant split APK to the APK Set archive. */
    ApkDescription addInstantApk(ModuleSplit split, ZipPath apkPath);

    /** Adds an system APK to the APK Set archive. */
    ApkDescription addSystemApk(ModuleSplit split, ZipPath apkPath);

    /** Adds an asset slice APK to the APK Set archive. */
    ApkDescription addAssetSliceApk(ModuleSplit split, ZipPath apkPath);

    /** Adds a hibernated APK to the APK Set archive. */
    ApkDescription addHibernatedApk(ModuleSplit split, ZipPath apkPath);

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

  public static ApkSetBuilder createApkSetWithoutArchiveBuilder(
      SplitApkSerializer splitApkSerializer,
      StandaloneApkSerializer standaloneApkSerializer,
      Path outputDir) {
    return new ApkSetWithoutArchiveBuilder(splitApkSerializer, standaloneApkSerializer, outputDir);
  }

  /** ApkSet builder that stores the generated APKs in the Apk Set archive. */
  public static class ApkSetArchiveBuilder implements ApkSetBuilder {
    private final SplitApkSerializer splitApkSerializer;
    private final StandaloneApkSerializer standaloneApkSerializer;
    private final List<String> relativeApkPaths;

    private final Path tempDirectory;

    private BuildApksResult tableOfContents;

    public ApkSetArchiveBuilder(
        SplitApkSerializer splitApkSerializer,
        StandaloneApkSerializer standaloneApkSerializer,
        Path tempDirectory) {
      this.splitApkSerializer = splitApkSerializer;
      this.standaloneApkSerializer = standaloneApkSerializer;
      this.tempDirectory = tempDirectory;
      this.relativeApkPaths = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public ApkDescription addSplitApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          splitApkSerializer.writeSplitToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addInstantApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          splitApkSerializer.writeInstantSplitToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addAssetSliceApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          splitApkSerializer.writeAssetSliceToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneUniversalApk(ModuleSplit split) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeToDiskAsUniversal(split, tempDirectory);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addSystemApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeSystemApkToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addHibernatedApk(ModuleSplit split, ZipPath apkPath) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeHibernatedApkToDisk(split, tempDirectory, apkPath);
      relativeApkPaths.add(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public void setTableOfContentsFile(BuildApksResult tableOfContentsProto) {
      tableOfContents = tableOfContentsProto;
    }

    @Override
    public void writeTo(Path destinationPath) {
      ZipBuilder apkSetZipBuilder = new ZipBuilder();
      if (tableOfContents != null) {
        apkSetZipBuilder.addFileWithProtoContent(
            ZipPath.create(TABLE_OF_CONTENTS_FILE), tableOfContents);
      }
      // Sort APKs to make ordering deterministic.
      for (String relativeApkPath : ImmutableList.sortedCopyOf(relativeApkPaths)) {
        Path fullApkPath = tempDirectory.resolve(relativeApkPath);
        checkFileExistsAndReadable(fullApkPath);
        apkSetZipBuilder.addFileFromDisk(
            ZipPath.create(relativeApkPath), fullApkPath.toFile(), EntryOption.UNCOMPRESSED);
      }
      try {
        apkSetZipBuilder.writeTo(destinationPath);
      } catch (IOException e) {
        throw new UncheckedIOException(
            String.format("Error while writing the APK Set archive to '%s'.", destinationPath), e);
      }
    }
  }

  /** ApkSet builder that stores the generated APKs directly in the output directory. */
  public static class ApkSetWithoutArchiveBuilder implements ApkSetBuilder {

    private final SplitApkSerializer splitApkSerializer;
    private final StandaloneApkSerializer standaloneApkSerializer;
    private final Path outputDirectory;

    public ApkSetWithoutArchiveBuilder(
        SplitApkSerializer splitApkSerializer,
        StandaloneApkSerializer standaloneApkSerializer,
        Path outputDirectory) {
      this.outputDirectory = outputDirectory;
      this.splitApkSerializer = splitApkSerializer;
      this.standaloneApkSerializer = standaloneApkSerializer;
    }

    @Override
    public ApkDescription addInstantApk(ModuleSplit split, ZipPath apkPath) {
      return splitApkSerializer.writeInstantSplitToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public ApkDescription addSplitApk(ModuleSplit split, ZipPath apkPath) {
      return splitApkSerializer.writeSplitToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public ApkDescription addAssetSliceApk(ModuleSplit split, ZipPath apkPath) {
      return splitApkSerializer.writeAssetSliceToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public ApkDescription addStandaloneApk(ModuleSplit split, ZipPath apkPath) {
      return standaloneApkSerializer.writeToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public ApkDescription addStandaloneUniversalApk(ModuleSplit split) {
      return standaloneApkSerializer.writeToDiskAsUniversal(split, outputDirectory);
    }

    @Override
    public ApkDescription addSystemApk(ModuleSplit split, ZipPath apkPath) {
      return standaloneApkSerializer.writeSystemApkToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public ApkDescription addHibernatedApk(ModuleSplit split, ZipPath apkPath) {
      return standaloneApkSerializer.writeHibernatedApkToDisk(split, outputDirectory, apkPath);
    }

    @Override
    public void setTableOfContentsFile(BuildApksResult tableOfContentsProto) {
      writeProtoFile(tableOfContentsProto, outputDirectory.resolve("toc.pb"));
    }

    @Override
    public void writeTo(Path destinationPath) {
      // No-op.
    }

    private void writeProtoFile(Message proto, Path outputFile) {
      try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
        proto.writeTo(outputStream);
      } catch (FileNotFoundException e) {
        throw new UncheckedIOException("Can't create the output file: " + outputFile, e);
      } catch (IOException e) {
        throw new UncheckedIOException("Error while writing the output file: " + outputFile, e);
      }
    }
  }
}
