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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static java.util.Comparator.naturalOrder;

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
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
    if (enableSparseEncoding && split.getResourceTable().isPresent()) {
      Path interimApk = tempDir.getPath().resolve("interim.apk");
      aapt2.convertApkProtoToBinary(partialProtoApk, interimApk);
      aapt2.optimizeToSparseResourceTables(interimApk, binaryApkPath);
    } else {
      aapt2.convertApkProtoToBinary(partialProtoApk, binaryApkPath);
    }
    checkState(Files.exists(binaryApkPath), "No APK created by aapt2 convert command.");

    try (ZipArchive apkWriter = new ZipArchive(outputPath);
        ZipReader aapt2ApkReader = ZipReader.createFromFile(binaryApkPath)) {
      ImmutableMap<ZipPath, ModuleEntry> moduleEntriesByName =
          split.getEntries().stream()
              .collect(
                  toImmutableMap(
                      entry -> ApkSerializerHelper.toApkEntryPath(entry.getPath()),
                      entry -> entry,
                      // If two entries end up at the same path in the APK, pick one arbitrarily.
                      // e.g. base/assets/foo and base/root/assets/foo.
                      (a, b) -> b));

      // Sorting entries by name for determinism.
      ImmutableSortedSet<ZipPath> sortedEntryNames =
          Stream.concat(
                  aapt2ApkReader.getEntries().keySet().stream().map(ZipPath::create),
                  moduleEntriesByName.keySet().stream())
              .collect(toImmutableSortedSet(naturalOrder()));

      ApkEntrySerializer apkEntrySerializer =
          new ApkEntrySerializer(apkWriter, aapt2ApkReader, split, tempDir);
      for (ZipPath pathInApk : sortedEntryNames) {
        Optional<Entry> aapt2Entry = aapt2ApkReader.getEntry(pathInApk.toString());
        if (aapt2Entry.isPresent()) {
          apkEntrySerializer.addAapt2Entry(pathInApk, aapt2Entry.get());
        } else {
          ModuleEntry moduleEntry = checkNotNull(moduleEntriesByName.get(pathInApk));
          apkEntrySerializer.addRegularEntry(pathInApk, moduleEntry);
        }
      }
    }

    apkSigner.signApk(outputPath, split);
  }

  private final class ApkEntrySerializer {
    /** Output APK where the entries will be added to. */
    private final ZipArchive apkWriter;
    /** The APK generated by aapt2 containing resources, manifest, etc. */
    private final ZipReader aapt2Apk;
    /** A directory to store intermediate artifacts. */
    private final TempDirectory tempDir;
    /** Controller of the compression of the entries in the final APK. */
    private final CompressionManager compressionManager;
    /** A map of the entries from the app bundle to copy data from. */
    private final ImmutableMap<String, Entry> bundleEntries;

    ApkEntrySerializer(
        ZipArchive apkWriter, ZipReader aapt2Apk, ModuleSplit moduleSplit, TempDirectory tempDir) {
      this.apkWriter = apkWriter;
      this.aapt2Apk = aapt2Apk;
      this.tempDir = tempDir;
      this.compressionManager = new CompressionManager(moduleSplit, bundleConfig);
      this.bundleEntries = bundleZipReader.getEntries();
    }

    void addRegularEntry(ZipPath pathInApk, ModuleEntry moduleEntry) throws IOException {
      ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(bundleZipReader, tempDir);
      boolean mayCompress = compressionManager.mayCompress(pathInApk);

      if (moduleEntry.getBundleLocation().isPresent()) {
        ZipPath pathInBundle = moduleEntry.getBundleLocation().get().entryPathInBundle();
        Entry entry = bundleEntries.get(pathInBundle.toString());
        checkNotNull(entry, "Could not find entry '%s'.", pathInBundle);

        CompressionLevel compressionLevel;
        if (mayCompress) {
          compressionLevel =
              useBundleCompression && entry.isCompressed() ? SAME_AS_SOURCE : DEFAULT_COMPRESSION;
        } else {
          compressionLevel = NO_COMPRESSION;
        }
        ZipEntrySource entrySource =
            sourceFactory
                .create(entry, pathInApk, compressionLevel)
                .setAlignment(getEntryAlignment(pathInApk, mayCompress));
        if (compressionLevel.isCompressed()
            && entrySource.getCompressedSize() >= entrySource.getUncompressedSize()) {
          // Not enough gains from compression, leave the entry uncompressed.
          entrySource =
              sourceFactory
                  .create(entry, pathInApk, NO_COMPRESSION)
                  .setAlignment(getEntryAlignment(pathInApk, /* compressed= */ false));
        }
        apkWriter.add(entrySource);

      } else {
        byte[] uncompressedContent = moduleEntry.getContent().read();
        BytesSource bytesSource =
            new BytesSource(
                uncompressedContent,
                pathInApk.toString(),
                mayCompress ? DEFAULT_COMPRESSION.getValue() : NO_COMPRESSION.getValue());
        bytesSource.align(getEntryAlignment(pathInApk, mayCompress));
        if (mayCompress && bytesSource.getCompressedSize() >= bytesSource.getUncompressedSize()) {
          // Not enough gains from compression, leave the entry uncompressed.
          bytesSource =
              new BytesSource(uncompressedContent, pathInApk.toString(), NO_COMPRESSION.getValue());
          bytesSource.align(getEntryAlignment(pathInApk, /* compressed= */ false));
        }

        apkWriter.add(bytesSource);
      }
    }

    void addAapt2Entry(ZipPath pathInApk, Entry entry) throws IOException {
      ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(aapt2Apk, tempDir);
      boolean mayCompress = compressionManager.mayCompress(pathInApk);

      // All entries in aapt2 should be uncompressed (see AppBundleRecompressor), so we don't use
      // SAME_AS_SOURCE.
      CompressionLevel compressionLevel = mayCompress ? BEST_COMPRESSION : NO_COMPRESSION;
      ZipEntrySource entrySource =
          sourceFactory
              .create(entry, pathInApk, compressionLevel)
              .setAlignment(getEntryAlignment(pathInApk, mayCompress));
      // Copying logic from aapt2: require at least 10% gains in savings.
      if (!compressionLevel.equals(NO_COMPRESSION)
          && (entrySource.getCompressedSize() + entrySource.getCompressedSize() / 10)
              > entrySource.getUncompressedSize()) {
        // Not enough gains from compression, leave the entry uncompressed.
        entrySource =
            sourceFactory
                .create(entry, pathInApk, NO_COMPRESSION)
                .setAlignment(getEntryAlignment(pathInApk, /* compressed= */ false));
      }

      apkWriter.add(entrySource);
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
    try (ZipArchive apkWriter = new ZipArchive(protoApkPath)) {
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

    /**
     * Returns true if the specified file may be compressed in the final generated APK.
     *
     * <p>If this method returns true, the preference is that the file is compressed within the APK,
     * however this isn't guaranteed, e.g. if the file's compressed size is greater than the
     * uncompressed size. If this method returns false, the file must be stored uncompressed.
     */
    public boolean mayCompress(ZipPath path) {
      String pathString = path.toString();

      // Resource table should always be uncompressed for runtime performance reasons.
      if (pathString.equals("resources.arsc")) {
        return false;
      }

      // The AndroidManifest.xml should be compressed even if *.xml files are set to be uncompressed
      // in the BundleConfig.
      if (pathString.equals("AndroidManifest.xml")) {
        return true;
      }

      if (uncompressedPathMatchers.stream()
          .anyMatch(pathMatcher -> pathMatcher.matches(pathString))) {
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
      if (uncompressNativeLibs && NATIVE_LIBRARIES_PATTERN.matcher(pathString).matches()) {
        return false;
      }

      // By default, may be compressed.
      return true;
    }
  }
}
