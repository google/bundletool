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

import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_IMAGE_SUFFIX;
import static com.android.tools.build.bundletool.model.BundleModule.BUILD_INFO_SUFFIX;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.android.tools.build.bundletool.model.utils.files.FileUtils.createParentDirectories;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_DEFAULT_UNCOMPRESS_EXTENSIONS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.apkzlib.zfile.ZFiles;
import com.android.tools.build.apkzlib.zip.AlignmentRule;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.GZipUtils;
import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.inject.Inject;

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
  static final AlignmentRule APK_ALIGNMENT_RULE =
      AlignmentRules.compose(
          AlignmentRules.constantForSuffix(NATIVE_LIBRARIES_SUFFIX, 4096),
          AlignmentRules.constant(4));

  private static final Predicate<ZipPath> FILES_FOR_AAPT2 =
      path ->
          path.startsWith("res")
              || path.equals(SpecialModuleEntry.RESOURCE_TABLE.getPath())
              || path.equals(ZipPath.create(MANIFEST_FILENAME));

  private static final String BUILT_BY = "BundleTool";
  private static final String CREATED_BY = BUILT_BY;
  private static final ImmutableSet<String> NO_COMPRESSION_EXTENSIONS =
      ImmutableSet.of(
          "3g2", "3gp", "3gpp", "3gpp2", "aac", "amr", "awb", "gif", "imy", "jet", "jpeg", "jpg",
          "m4a", "m4v", "mid", "midi", "mkv", "mp2", "mp3", "mp4", "mpeg", "mpg", "ogg", "png",
          "rtttl", "smf", "wav", "webm", "wma", "wmv", "xmf");

  private final Aapt2Command aapt2Command;
  private final Version bundletoolVersion;
  private final ImmutableList<PathMatcher> uncompressedPathMatchers;
  private final ApkSigner apkSigner;

  @Inject
  ApkSerializerHelper(
      Aapt2Command aapt2Command,
      Version bundletoolVersion,
      BundleConfig bundleConfig,
      ApkSigner apkSigner) {
    this.aapt2Command = aapt2Command;
    this.bundletoolVersion = bundletoolVersion;
    this.uncompressedPathMatchers =
        bundleConfig.getCompression().getUncompressedGlobList().stream()
            .map(PathMatcher::createFromGlob)
            .collect(toImmutableList());
    this.apkSigner = apkSigner;
  }

  Path writeToZipFile(ModuleSplit split, Path outputPath) {
    try (TempDirectory tempDirectory = new TempDirectory()) {
      writeToZipFile(split, outputPath, tempDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return outputPath;
  }

  private void writeToZipFile(ModuleSplit split, Path outputPath, TempDirectory tempDir)
      throws IOException {
    checkFileDoesNotExist(outputPath);
    createParentDirectories(outputPath);

    // Sign the embedded APKs
    split = apkSigner.signEmbeddedApks(split);

    // Write a Proto-APK with only files that aapt2 requires as part of the convert command.
    Path partialProtoApk = tempDir.getPath().resolve("proto.apk");
    writeProtoApk(split, partialProtoApk);

    // Have aapt2 convert the Proto-APK to a Binary-APK.
    Path binaryApk = tempDir.getPath().resolve("binary.apk");
    aapt2Command.convertApkProtoToBinary(partialProtoApk, binaryApk);
    checkState(Files.exists(binaryApk), "No APK created by aapt2 convert command.");

    // Create a new APK that includes files processed by aapt2 and the other ones.
    try (ZFile zOutputApk =
            ZFiles.apk(
                outputPath.toFile(),
                createZFileOptions(tempDir.getPath())
                    .setAlignmentRule(APK_ALIGNMENT_RULE)
                    .setCoverEmptySpaceUsingExtraField(true)
                    // Clear timestamps on zip entries to minimize diffs between APKs.
                    .setNoTimestamps(true),
                /* signingOptions= */ com.google.common.base.Optional.absent(),
                BUILT_BY,
                CREATED_BY);
        ZFile zAapt2Files =
            ZFile.openReadOnly(binaryApk.toFile(), createZFileOptions(tempDir.getPath()))) {

      // Add files from the binary APK generated by AAPT2.
      zOutputApk.mergeFrom(zAapt2Files, /* ignoreFilter= */ Predicates.alwaysFalse());

      // Add the remaining files.
      addNonAapt2Files(zOutputApk, split);
      zOutputApk.sortZipContents();
    }

    apkSigner.signApk(outputPath, split);
  }

  Path writeCompressedApkToZipFile(ModuleSplit split, Path outputPath) {
    try (TempDirectory tempDirectory = new TempDirectory()) {
      Path tempApkOutputPath = tempDirectory.getPath().resolve("output.apk");
      writeToZipFile(split, tempApkOutputPath);
      writeCompressedApkToZipFile(tempApkOutputPath, outputPath);
    }
    return outputPath;
  }

  private void writeCompressedApkToZipFile(Path apkPath, Path outputApkGzipPath) {
    checkFileDoesNotExist(outputApkGzipPath);
    createParentDirectories(outputApkGzipPath);

    try {
      GZipUtils.compress(apkPath, outputApkGzipPath);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Failed to write APK file '%s' to compressed APK file '%s'.",
              apkPath, outputApkGzipPath),
          e);
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
  private Path writeProtoApk(ModuleSplit split, Path outputPath) throws IOException {
    boolean extractNativeLibs = split.getAndroidManifest().getExtractNativeLibsValue().orElse(true);

    ZipBuilder zipBuilder = new ZipBuilder();
    for (ModuleEntry entry : split.getEntries()) {
      ZipPath pathInApk = toApkEntryPath(entry.getPath());
      if (!FILES_FOR_AAPT2.apply(pathInApk)) {
        continue;
      }

      EntryOption[] entryOptions =
          entryOptionForPath(
              pathInApk,
              /* uncompressNativeLibs= */ !extractNativeLibs,
              /* forceUncompressed= */ entry.getForceUncompressed());
      zipBuilder.addFile(pathInApk, entry.getContent(), entryOptions);
    }

    split
        .getResourceTable()
        .ifPresent(
            resourceTable ->
                zipBuilder.addFileWithProtoContent(
                    SpecialModuleEntry.RESOURCE_TABLE.getPath(), resourceTable));
    zipBuilder.addFileWithProtoContent(
        ZipPath.create(MANIFEST_FILENAME), split.getAndroidManifest().getManifestRoot().getProto());

    zipBuilder.writeTo(outputPath);

    return outputPath;
  }

  private EntryOption[] entryOptionForPath(
      ZipPath path, boolean uncompressNativeLibs, boolean forceUncompressed) {
    if (shouldCompress(path, uncompressNativeLibs, forceUncompressed)) {
      return new EntryOption[] {};
    } else {
      return new EntryOption[] {EntryOption.UNCOMPRESSED};
    }
  }

  private boolean shouldCompress(
      ZipPath path, boolean uncompressNativeLibs, boolean forceUncompressed) {
    if (uncompressedPathMatchers.stream()
        .anyMatch(pathMatcher -> pathMatcher.matches(path.toString()))) {
      return false;
    }

    if (forceUncompressed) {
      return false;
    }

    // Common extensions that should remain uncompressed because compression doesn't provide any
    // gains.
    if (!NO_DEFAULT_UNCOMPRESS_EXTENSIONS.enabledForVersion(bundletoolVersion)
        && NO_COMPRESSION_EXTENSIONS.contains(FileUtils.getFileExtension(path))) {
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
    boolean extractNativeLibs = split.getAndroidManifest().getExtractNativeLibsValue().orElse(true);

    // Add the non-Aapt2 files.
    for (ModuleEntry entry : split.getEntries()) {
      ZipPath pathInApk = toApkEntryPath(entry.getPath());
      if (!FILES_FOR_AAPT2.apply(pathInApk)) {
        boolean shouldCompress =
            shouldCompress(pathInApk, !extractNativeLibs, entry.getForceUncompressed());
        addFile(zFile, pathInApk, entry, shouldCompress);
      }
    }
  }

  void addFile(ZFile zFile, ZipPath pathInApk, ModuleEntry entry, boolean shouldCompress)
      throws IOException {
    try (InputStream entryInputStream = entry.getContent().openStream()) {
      zFile.add(pathInApk.toString(), entryInputStream, shouldCompress);
    }
  }

  /**
   * Transforms the entry path in the module to the final path in the module split.
   *
   * <p>The entries from root/, dex/, manifest/ directories will be moved to the top level of the
   * split. Entries from apex/ will be moved to the top level and named "apex_payload.img" for
   * images or "apex_build_info.pb" for APEX build info. There should only be one such entry.
   */
  static ZipPath toApkEntryPath(ZipPath pathInModule) {
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
    if (pathInModule.startsWith(APEX_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() >= 2,
          "Only files inside the apex directory are supported but found: %s",
          pathInModule);
      checkArgument(
          pathInModule.toString().endsWith(APEX_IMAGE_SUFFIX)
              || pathInModule.toString().endsWith(BUILD_INFO_SUFFIX),
          "Unexpected filename in apex directory: %s",
          pathInModule);
      if (pathInModule.toString().endsWith(APEX_IMAGE_SUFFIX)) {
        return ZipPath.create("apex_payload.img");
      } else {
        return ZipPath.create("apex_build_info.pb");
      }
    }
    return pathInModule;
  }

  private static ZFileOptions createZFileOptions(Path tempDir) {
    ZFileOptions options = new ZFileOptions();
    return options;
  }
}
