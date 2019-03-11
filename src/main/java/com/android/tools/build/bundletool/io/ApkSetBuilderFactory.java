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
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableList;
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

    /** Adds an instant split APK to the APK Set archive. */
    ApkDescription addInstantApk(ModuleSplit split);

    /** Adds an system APK to the APK Set archive. */
    ApkDescription addSystemApk(ModuleSplit split);

    /** Adds an asset slice APK to the APK Set archive. */
    ApkDescription addAssetSliceApk(ModuleSplit split);

    /**
     * Adds an compressed system APK and an and an additional uncompressed stub APK containing just
     * the android manifest to the APK Set archive.
     */
    ImmutableList<ApkDescription> addCompressedSystemApks(ModuleSplit split);

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
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addInstantApk(ModuleSplit split) {
      ApkDescription apkDescription =
          splitApkSerializer.writeInstantSplitToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addAssetSliceApk(ModuleSplit split) {
      ApkDescription apkDescription =
          splitApkSerializer.writeAssetSliceToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneApk(ModuleSplit split) {
      ApkDescription apkDescription = standaloneApkSerializer.writeToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addStandaloneUniversalApk(ModuleSplit split) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeToDiskAsUniversal(split, tempDirectory);
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ApkDescription addSystemApk(ModuleSplit split) {
      ApkDescription apkDescription =
          standaloneApkSerializer.writeSystemApkToDisk(split, tempDirectory);
      addToApkSetArchive(apkDescription.getPath());
      return apkDescription;
    }

    @Override
    public ImmutableList<ApkDescription> addCompressedSystemApks(ModuleSplit split) {
      ImmutableList<ApkDescription> apkDescriptions =
          standaloneApkSerializer.writeCompressedSystemApksToDisk(split, tempDirectory);
      apkDescriptions.forEach(apkDescription -> addToApkSetArchive(apkDescription.getPath()));
      return apkDescriptions;
    }

    private void addToApkSetArchive(String relativeApkPath) {
      Path fullApkPath = tempDirectory.resolve(relativeApkPath);
      checkFileExistsAndReadable(fullApkPath);
      apkSetZipBuilder.addFileFromDisk(
          ZipPath.create(relativeApkPath), fullApkPath.toFile(), EntryOption.UNCOMPRESSED);
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
    public ApkDescription addInstantApk(ModuleSplit split) {
      return splitApkSerializer.writeInstantSplitToDisk(split, outputDirectory);
    }

    @Override
    public ApkDescription addSplitApk(ModuleSplit split) {
      return splitApkSerializer.writeSplitToDisk(split, outputDirectory);
    }

    @Override
    public ApkDescription addAssetSliceApk(ModuleSplit split) {
      return splitApkSerializer.writeAssetSliceToDisk(split, outputDirectory);
    }

    @Override
    public ApkDescription addStandaloneApk(ModuleSplit split) {
      return standaloneApkSerializer.writeToDisk(split, outputDirectory);
    }

    @Override
    public ApkDescription addStandaloneUniversalApk(ModuleSplit split) {
      return standaloneApkSerializer.writeToDiskAsUniversal(split, outputDirectory);
    }

    @Override
    public ApkDescription addSystemApk(ModuleSplit split) {
      return standaloneApkSerializer.writeSystemApkToDisk(split, outputDirectory);
    }

    @Override
    public ImmutableList<ApkDescription> addCompressedSystemApks(ModuleSplit split) {
      return standaloneApkSerializer.writeCompressedSystemApksToDisk(split, outputDirectory);
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
      try (OutputStream outputStream = BufferedIo.outputStream(outputFile)) {
        proto.writeTo(outputStream);
      } catch (FileNotFoundException e) {
        throw new UncheckedIOException(
            String.format("Can't create the output file '%s'.", outputFile), e);
      } catch (IOException e) {
        throw new UncheckedIOException(
            String.format("Error while writing the output file '%s'.", outputFile), e);
      }
    }
  }
}
