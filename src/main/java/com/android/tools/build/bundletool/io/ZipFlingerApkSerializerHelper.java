/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.CompressionLevel.BEST_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.DEFAULT_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.NO_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.SAME_AS_SOURCE;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FileUtils.createParentDirectories;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_DEFAULT_UNCOMPRESS_EXTENSIONS;
import static com.android.zipflinger.Source.NO_ALIGNMENT;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.ResourceOptimizations.SparseEncoding;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.BuildApksManagerComponent.UseBundleCompression;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.CompressionLevel;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Serializes APKs to Proto or Binary format. */
final class ZipFlingerApkSerializerHelper extends ApkSerializerHelper {

  /** Suffix for native libraries. */
  private static final String NATIVE_LIBRARIES_SUFFIX = ".so";

  private static final Pattern NATIVE_LIBRARIES_PATTERN = Pattern.compile("lib/[^/]+/[^/]+\\.so");

  private final ZipReader bundleZipReader;
  private final BundleConfig bundleConfig;
  private final ApkSigner apkSigner;
  private final Aapt2Command aapt2;
  private final Version bundletoolVersion;
  private final boolean enableSparseEncoding;

  /**
   * Whether to re-use the compression of the entries in the App Bundle.
   *
   * <p>This is an optimization for testing purposes or when the App Bundle has been re-compressed
   * with the desired compression beforehand.
   */
  private final boolean useBundleCompression;

  @Inject
  ZipFlingerApkSerializerHelper(
      ZipReader bundleZipReader,
      BundleConfig bundleConfig,
      Aapt2Command aapt2,
      Version bundletoolVersion,
      ApkSigner apkSigner,
      @UseBundleCompression boolean useBundleCompression) {
    this.bundleZipReader = bundleZipReader;
    this.bundleConfig = bundleConfig;
    this.aapt2 = aapt2;
    this.bundletoolVersion = bundletoolVersion;
    this.apkSigner = apkSigner;
    this.useBundleCompression = useBundleCompression;
    this.enableSparseEncoding =
        bundleConfig
            .getOptimizations()
            .getResourceOptimizations()
            .getSparseEncoding()
            .equals(SparseEncoding.ENFORCED);
  }

  @Override
  public Path writeToZipFile(ModuleSplit split, Path outputPath) {
    try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName())) {
      writeToZipFile(split, outputPath, tempDir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return outputPath;
  }

  private void writeToZipFile(ModuleSplit split, Path outputPath, TempDirectory tempDir)
      throws IOException {
    checkFileDoesNotExist(outputPath);
    createParentDirectories(outputPath);

    split = apkSigner.signEmbeddedApks(split);

    // Write a Proto-APK with only files that aapt2 requires as part of the convert command.
    Path partialProtoApk = tempDir.getPath().resolve("proto.apk");
    writeProtoApk(split, partialProtoApk, tempDir);

    // Invoke aapt2 to convert files from proto to binary format.
    Path binaryApkPath = tempDir.getPath().resolve("binary.apk");
    if (enableSparseEncoding) {
      Path interimApk = tempDir.getPath().resolve("interim.apk");
      aapt2.convertApkProtoToBinary(partialProtoApk, interimApk);
      aapt2.optimizeToSparseResourceTables(interimApk, binaryApkPath);
    } else {
      aapt2.convertApkProtoToBinary(partialProtoApk, binaryApkPath);
    }
    checkState(Files.exists(binaryApkPath), "No APK created by aapt2 convert command.");

    CompressionManager compressionManager = new CompressionManager(split, bundleConfig);

    try (ZipArchive apkWriter = new ZipArchive(outputPath.toFile())) {
      addEntriesConvertedByAapt2(apkWriter, binaryApkPath, compressionManager, tempDir);
      addRemainingEntries(apkWriter, split, compressionManager, tempDir);
    }

    apkSigner.signApk(outputPath, split);
  }

  @SuppressWarnings("MethodCanBeStatic")
  private void addEntriesConvertedByAapt2(
      ZipArchive apkWriter,
      Path binaryApkPath,
      CompressionManager compressionManager,
      TempDirectory tempDir)
      throws IOException {
    try (ZipReader aapt2ApkZipReader = ZipReader.createFromFile(binaryApkPath)) {
      // Sorting entries by name for determinism.
      ImmutableList<Entry> sortedAapt2ApkEntries =
          ImmutableList.sortedCopyOf(
              comparing(Entry::getName), aapt2ApkZipReader.getEntries().values());

      ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(aapt2ApkZipReader, tempDir);

      for (Entry entry : sortedAapt2ApkEntries) {
        String entryName = entry.getName();
        ZipPath pathInApk = ZipPath.create(entryName);
        CompressionLevel compressionLevel;
        if (compressionManager.shouldCompress(pathInApk)) {
          compressionLevel = entry.isCompressed() ? SAME_AS_SOURCE : BEST_COMPRESSION;
        } else {
          compressionLevel = NO_COMPRESSION;
        }
        apkWriter.add(
            sourceFactory
                .create(entry, pathInApk, compressionLevel)
                .setAlignment(getEntryAlignment(pathInApk, entry.isCompressed())));
      }
    }
  }

  private void addRemainingEntries(
      ZipArchive apkWriter,
      ModuleSplit split,
      CompressionManager compressionManager,
      TempDirectory tempDir)
      throws IOException {
    // Sort entries by name for determinism.
    ImmutableList<ModuleEntry> sortedEntries =
        ImmutableList.sortedCopyOf(
            comparing(entry -> ApkSerializerHelper.toApkEntryPath(entry.getPath())),
            split.getEntries());

    ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(bundleZipReader, tempDir);
    ImmutableMap<String, Entry> bundleEntries = bundleZipReader.getEntries();

    for (ModuleEntry moduleEntry : sortedEntries) {
      ZipPath pathInApk = ApkSerializerHelper.toApkEntryPath(moduleEntry.getPath());
      if (requiresAapt2Conversion(pathInApk)) {
        continue;
      }
      boolean shouldCompress = compressionManager.shouldCompress(pathInApk);

      if (moduleEntry.getBundleLocation().isPresent()) {
        ZipPath pathInBundle = moduleEntry.getBundleLocation().get().entryPathInBundle();
        Entry entry = bundleEntries.get(pathInBundle.toString());
        checkNotNull(entry, "Could not find entry '%s'.", pathInBundle);

        CompressionLevel compressionLevel;
        if (shouldCompress) {
          compressionLevel =
              useBundleCompression && entry.isCompressed() ? SAME_AS_SOURCE : DEFAULT_COMPRESSION;
        } else {
          compressionLevel = NO_COMPRESSION;
        }
        apkWriter.add(
            sourceFactory
                .create(entry, pathInApk, compressionLevel)
                .setAlignment(getEntryAlignment(pathInApk, shouldCompress)));
      } else {
        BytesSource bytesSource =
            new BytesSource(
                moduleEntry.getContent().read(),
                pathInApk.toString(),
                shouldCompress ? DEFAULT_COMPRESSION.getValue() : NO_COMPRESSION.getValue());
        bytesSource.align(getEntryAlignment(pathInApk, shouldCompress));
        apkWriter.add(bytesSource);
      }
    }
  }

  /**
   * Writes an APK for aapt2 to convert to binary.
   *
   * <p>This APK contains only files that aapt2 will convert from proto to binary, and the files
   * needed for the conversion to succeed (e.g. all resources referenced in the resource table).
   *
   * <p>All files are left uncompressed to save aapt2 from re-compressing all the entries it
   * generated since we prefer having control over which compression is used. This is effectively a
   * tradeoff between local storage and CPU.
   *
   * <p>No entry is 4-byte aligned since it's only a temporary APK for aapt2 conversion which won't
   * be actually stored or served to any device.
   */
  private void writeProtoApk(ModuleSplit split, Path protoApkPath, TempDirectory tempDir)
      throws IOException {
    try (ZipArchive apkWriter = new ZipArchive(protoApkPath.toFile())) {
      apkWriter.add(
          new BytesSource(
              split.getAndroidManifest().getManifestRoot().getProto().toByteArray(),
              MANIFEST_FILENAME,
              NO_COMPRESSION.getValue()));
      if (split.getResourceTable().isPresent()) {
        BytesSource bytesSource =
            new BytesSource(
                split.getResourceTable().get().toByteArray(),
                SpecialModuleEntry.RESOURCE_TABLE.getPath().toString(),
                NO_COMPRESSION.getValue());
        bytesSource.align(4);
        apkWriter.add(bytesSource);
      }

      Map<String, Entry> bundleEntriesByName = bundleZipReader.getEntries();
      ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(bundleZipReader, tempDir);

      for (ModuleEntry moduleEntry : split.getEntries()) {
        ZipPath pathInApk = ApkSerializerHelper.toApkEntryPath(moduleEntry.getPath());
        if (!requiresAapt2Conversion(pathInApk)) {
          continue;
        }

        if (moduleEntry.getBundleLocation().isPresent()) {
          ZipPath pathInBundle = moduleEntry.getBundleLocation().get().entryPathInBundle();
          Entry entry = bundleEntriesByName.get(pathInBundle.toString());
          checkNotNull(entry, "Could not find entry '%s'.", pathInBundle);
          apkWriter.add(sourceFactory.create(entry, pathInApk, NO_COMPRESSION));
        } else {
          apkWriter.add(
              new BytesSource(
                  moduleEntry.getContent().read(),
                  pathInApk.toString(),
                  NO_COMPRESSION.getValue()));
        }
      }
    }
  }

  private static long getEntryAlignment(ZipPath zipPath, boolean compressed) {
    if (compressed) {
      return NO_ALIGNMENT;
    }
    return zipPath.toString().endsWith(NATIVE_LIBRARIES_SUFFIX) ? 4096 : 4;
  }

  private class CompressionManager {

    private final boolean uncompressNativeLibs;
    private final ImmutableSet<ZipPath> forceUncompressedEntries;
    private final ImmutableList<PathMatcher> uncompressedPathMatchers;

    CompressionManager(ModuleSplit split, BundleConfig bundleConfig) {
      this.uncompressNativeLibs =
          !split.getAndroidManifest().getExtractNativeLibsValue().orElse(true);
      this.forceUncompressedEntries =
          split.getEntries().stream()
              .filter(ModuleEntry::getForceUncompressed)
              .map(entry -> ApkSerializerHelper.toApkEntryPath(entry.getPath()))
              .collect(toImmutableSet());
      this.uncompressedPathMatchers =
          bundleConfig.getCompression().getUncompressedGlobList().stream()
              .map(PathMatcher::createFromGlob)
              .collect(toImmutableList());
    }

    public boolean shouldCompress(ZipPath path) {
      if (uncompressedPathMatchers.stream()
          .anyMatch(pathMatcher -> pathMatcher.matches(path.toString()))) {
        return false;
      }

      // Resource table should always be uncompressed for runtime performance reasons.
      if (path.toString().equals("resources.arsc")) {
        return false;
      }

      if (forceUncompressedEntries.contains(path)) {
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
  }
}
