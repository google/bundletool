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

import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_PROTO_PATH;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.Compression;
import com.android.tools.build.apkzlib.zfile.ZFiles;
import com.android.tools.build.apkzlib.zip.AlignmentRule;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.utils.files.FileUtils;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.regex.Pattern;

/** Serializes APKs to Proto or Binary format. */
final class ApkSerializerHelper {

  /** Suffix for native libraries. */
  private static final String NATIVE_LIBRARIES_SUFFIX = ".so";

  private static final Pattern NATIVE_LIBRARIES_PATTERN = Pattern.compile("lib/[^/]+/[^/]+\\.so");

  /**
   * Alignment rule for all APKs.
   *
   * <ul>
   *   <li>Align by 4 all uncompressed files.
   *   <li>Align by 4096 all uncompressed native libraries.
   * </ul>
   *
   * Note that it's fine to always provide the same alignment rule regardless of the value of
   * 'extractNativeLibs' because apkzlib will only apply these rules to uncompressed files, so a
   * compressed file will remain unaligned.
   */
  @VisibleForTesting
  static final AlignmentRule APK_ALIGNMENT_RULE =
      AlignmentRules.compose(
          AlignmentRules.constantForSuffix(NATIVE_LIBRARIES_SUFFIX, 4096),
          AlignmentRules.constant(4));

  private static final Predicate<ZipPath> FILES_FOR_AAPT2 =
      path ->
          path.startsWith("res")
              || path.equals(RESOURCES_PROTO_PATH)
              || path.equals(ZipPath.create(MANIFEST_FILENAME));

  private static final String BUILT_BY = "BundleTool " + BundleToolVersion.getCurrentVersion();
  private static final String CREATED_BY = BUILT_BY;
  private static final ImmutableSet<String> NO_COMPRESSION_EXTENSIONS =
      ImmutableSet.of(
          "3g2", "3gp", "3gpp", "3gpp2", "aac", "amr", "awb", "gif", "imy", "jet", "jpeg", "jpg",
          "m4a", "m4v", "mid", "midi", "mkv", "mp2", "mp3", "mp4", "mpeg", "mpg", "ogg", "png",
          "rtttl", "smf", "wav", "webm", "wma", "wmv", "xmf");

  private final Aapt2Command aapt2Command;
  private final Optional<SigningConfiguration> signingConfig;
  private final ImmutableList<PathMatcher> uncompressedPathMatchers;

  ApkSerializerHelper(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Compression compression) {
    this.aapt2Command = aapt2Command;
    this.signingConfig = signingConfig;

    // Using the default filesystem will work on Windows because the "/" of the glob are swapped
    // with "\" when the PathMatcher is constructed and the Path on Windows use this file separator.
    // Note however that the "/" are not swapped for regexps, so we couldn't use PathMatcher if we
    // wanted to add support for regexp.
    FileSystem fileSystem = FileSystems.getDefault();
    this.uncompressedPathMatchers =
        compression
            .getUncompressedGlobList()
            .stream()
            .map(glob -> "glob:" + glob)
            .map(fileSystem::getPathMatcher)
            .collect(toImmutableList());
  }

  Path writeToZipFile(ModuleSplit split, Path outputPath) {
    TempFiles.withTempDirectory(tempDir -> writeToZipFile(split, outputPath, tempDir));
    return outputPath;
  }

  void writeToZipFile(ModuleSplit split, Path outputPath, Path tempDir) {
    checkFileDoesNotExist(outputPath);
    // Write a Proto-APK with only files that aapt2 requires as part of the convert command.
    Path partialProtoApk = tempDir.resolve("proto.apk");
    writeProtoApk(split, partialProtoApk);

    // Have aapt2 convert the Proto-APK to a Binary-APK.
    Path binaryApk = tempDir.resolve("binary.apk");
    aapt2Command.convertApkProtoToBinary(partialProtoApk, binaryApk);
    checkState(Files.exists(binaryApk), "No APK created by aapt2 convert command.");

    // Create a new APK that includes files processed by aapt2 and the other ones.
    int minSdkVersion = split.getAndroidManifest().get().getEffectiveMinSdkVersion();
    try (ZFile zOutputApk =
            ZFiles.apk(
                outputPath.toFile(),
                new ZFileOptions()
                    .setAlignmentRule(APK_ALIGNMENT_RULE)
                    .setCoverEmptySpaceUsingExtraField(true)
                    // Clear timestamps on zip entries to minimize diffs between APKs.
                    .setNoTimestamps(true),
                signingConfig.map(config -> config.getPrivateKey()).orElse(null),
                signingConfig.map(config -> config.getCertificates()).orElse(null),
                /* v1SigningEnabled= */ true,
                /* v2SigningEnabled= */ true,
                BUILT_BY,
                CREATED_BY,
                minSdkVersion);
        ZFile zAapt2Files =
            new ZFile(binaryApk.toFile(), new ZFileOptions(), /* readOnly= */ true)) {

      // Add files from the binary APK generated by AAPT2.
      zOutputApk.mergeFrom(zAapt2Files, /* ignoreFilter= */ Predicates.alwaysFalse());

      // Add the remaining files.
      addNonAapt2Files(zOutputApk, split);
      zOutputApk.sortZipContents();
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Failed to write APK file '%s'.", outputPath)
          .build();
    }
  }

  /**
   * Creates a proto-APK from the {@link ModuleSplit} and stores it on disk.
   *
   * <p>Note that it only includes files that aapt2 transforms, i.e. AndroidManifest.xml, resource
   * table, and resources. This is an optimization to prevent aapt2 from copying unnecessary files.
   *
   * @param split In-memory representation of the APK to write.
   * @param outputPath Path to where the APK should be created.
   * @return The path to the created APK.
   */
  private Path writeProtoApk(ModuleSplit split, Path outputPath) {
    checkState(split.getAndroidManifest().isPresent(), "Missing AndroidManifest");

    boolean extractNativeLibs =
        split.getAndroidManifest().get().getExtractNativeLibsValue().orElse(true);

    ZipBuilder zipBuilder = new ZipBuilder();
    for (ModuleEntry entry : split.getEntries()) {
      ZipPath pathInApk = toApkEntryPath(entry.getPath());
      if (FILES_FOR_AAPT2.apply(pathInApk)) {
        zipBuilder.addFile(
            pathInApk, () -> entry.getContent(), entryOptionForPath(pathInApk, !extractNativeLibs));
      }
    }

    split
        .getResourceTable()
        .ifPresent(
            resourceTable ->
                zipBuilder.addFileWithProtoContent(RESOURCES_PROTO_PATH, resourceTable));
    zipBuilder.addFileWithProtoContent(
        ZipPath.create(MANIFEST_FILENAME), split.getAndroidManifest().get().getManifestRoot());

    try {
      zipBuilder.writeTo(outputPath);
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Error while writing APK to file '%s'.", outputPath.getFileName())
          .build();
    }

    return outputPath;
  }

  private EntryOption[] entryOptionForPath(ZipPath path, boolean uncompressNativeLibs) {
    if (shouldCompress(path, uncompressNativeLibs)) {
      return new EntryOption[] {};
    } else {
      return new EntryOption[] {EntryOption.UNCOMPRESSED};
    }
  }

  private boolean shouldCompress(ZipPath path, boolean uncompressNativeLibs) {
    // Developer knows best: when they provide the uncompressed glob, we respect it.
    if (uncompressedPathMatchers.stream().anyMatch(pathMatcher -> pathMatcher.matches(path))) {
      return false;
    }

    // Common extensions that should remain uncompressed because don't provide any gains.
    if (NO_COMPRESSION_EXTENSIONS.contains(FileUtils.getFileExtension(path))) {
      return false;
    }

    // Uncompressed native libraries (supported since SDK 23 - Android M).
    if (uncompressNativeLibs && NATIVE_LIBRARIES_PATTERN.matcher(path.toString()).matches()) {
      return false;
    }

    // By default, compressed.
    return true;
  }

  /** Takes the given APK and adds the files that weren't processed by AAPT2. */
  private void addNonAapt2Files(ZFile zFile, ModuleSplit split) throws IOException {
    boolean extractNativeLibs =
        split.getAndroidManifest().get().getExtractNativeLibsValue().orElse(true);

    // Add the non-Aapt2 files.
    for (ModuleEntry entry : split.getEntries()) {
      ZipPath pathInApk = toApkEntryPath(entry.getPath());
      if (!FILES_FOR_AAPT2.apply(pathInApk)) {
        try (InputStream entryInputStream = entry.getContent()) {
          zFile.add(
              pathInApk.toString(),
              entryInputStream,
              shouldCompress(pathInApk, !extractNativeLibs));
        }
      }
    }
  }

  /**
   * Transforms the entry path in the module to the final path in the module split.
   *
   * <p>The entries from root/, dex/, manifest/ directories will be moved to the top level of the
   * split.
   */
  private ZipPath toApkEntryPath(ZipPath pathInModule) {
    if (pathInModule.startsWith(DEX_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() == 2,
          "Only files directly in the dex directory are supported but found: %s.",
          pathInModule);
      checkFileHasExtension("File under dex/ directory", pathInModule, ".dex");
      return pathInModule.getFileName();
    }
    if (pathInModule.startsWith(ROOT_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() >= 2,
          "Only files inside the root directory are supported but found: %s",
          pathInModule);
      return pathInModule.subpath(1, pathInModule.getNameCount());
    }
    return pathInModule;
  }
}
