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

import static com.android.tools.build.bundletool.io.ApkSerializerHelper.requiresAapt2Conversion;
import static com.android.tools.build.bundletool.io.ApkSerializerHelper.toApkEntryPath;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_DEFAULT_UNCOMPRESS_EXTENSIONS;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.SigningDescription;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression.ApkCompressionAlgorithm;
import com.android.tools.build.bundletool.androidtools.P7ZipCommand;
import com.android.tools.build.bundletool.commands.BuildApksModule.VerboseLogs;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import javax.inject.Inject;

/** Serializes module splits on disk. */
public class ModuleSplitSerializer extends ApkSerializer {
  /** Suffix for native libraries. */
  private static final String NATIVE_LIBRARIES_SUFFIX = ".so";

  private static final Pattern NATIVE_LIBRARIES_PATTERN = Pattern.compile("lib/[^/]+/[^/]+\\.so");

  private final Aapt2ResourceConverter aapt2ResourceConverter;
  private final ApkSigner apkSigner;
  private final CacheablePathMatcher uncompressedPathMatchers;
  private final Version bundletoolVersion;
  private final ListeningExecutorService executorService;
  private final boolean use7ZipCompression;
  private final Optional<P7ZipCommand> p7ZipCommand;

  @Inject
  ModuleSplitSerializer(
      Optional<ApkListener> apkListener,
      @VerboseLogs boolean verbose,
      Aapt2ResourceConverter aapt2ResourceConverterFactory,
      ApkSigner apkSigner,
      BundleConfig bundleConfig,
      Version bundletoolVersion,
      ListeningExecutorService executorService,
      Optional<P7ZipCommand> p7ZipCommand) {
    super(apkListener, verbose);
    this.aapt2ResourceConverter = aapt2ResourceConverterFactory;
    this.apkSigner = apkSigner;
    this.uncompressedPathMatchers =
        new CacheablePathMatcher(
            bundleConfig.getCompression().getUncompressedGlobList().stream()
                .map(PathMatcher::createFromGlob)
                .collect(toImmutableList()));
    this.use7ZipCompression =
        bundleConfig
            .getCompression()
            .getApkCompressionAlgorithm()
            .equals(ApkCompressionAlgorithm.P7ZIP);
    this.bundletoolVersion = bundletoolVersion;
    this.executorService = executorService;
    this.p7ZipCommand = p7ZipCommand;
  }

  /**
   * Serializes module splits on disk under {@code outputDirectory}.
   *
   * <p>Returns {@link ApkDescription} for each serialized split keyed by relative path of module
   * split.
   */
  public ImmutableMap<ZipPath, ApkDescription> serialize(
      Path outputDirectory, ImmutableMap<ZipPath, ModuleSplit> splitsByRelativePath) {
    // Prepare original splits by:
    //  * signing embedded APKs
    //  * injecting manifest and resource table as module entries.
    ImmutableList<ModuleSplit> preparedSplits =
        splitsByRelativePath.values().stream()
            .map(apkSigner::signEmbeddedApks)
            .map(ModuleSplitSerializer::injectManifestAndResourceTableAsEntries)
            .collect(toImmutableList());

    try (SerializationFilesManager filesManager = new SerializationFilesManager()) {
      // Convert module splits to binary format and apply uncompressed globs specified in
      // BundleConfig. We do it in this order because as specified in documentation the matching
      // for uncompressed globs is done against paths in final APKs.
      ImmutableList<ModuleSplit> binarySplits =
          aapt2ResourceConverter.convert(preparedSplits, filesManager).stream()
              .map(this::applyUncompressedGlobsAndUncompressedNativeLibraries)
              .collect(toImmutableList());

      // Build a pack from entries which may be compressed inside final APKs. 'May be
      // compressed' means that for these entries we will decide later should they be compressed
      // or not based on whether we gain enough savings from compression.
      ModuleEntriesPack maybeCompressedEntriesPack =
          buildCompressedEntriesPack(filesManager, binarySplits);

      // Build a pack with entries that are uncompressed in final APKs: force uncompressed entries
      // + entries that have very low compression ratio.
      ModuleEntriesPack uncompressedEntriesPack =
          buildUncompressedEntriesPack(
              filesManager.getUncompressedEntriesPackPath(),
              binarySplits,
              maybeCompressedEntriesPack);

      // Now content of all binary apks is already moved to compressed/uncompressed packs. Delete
      // them to free space.
      filesManager.closeAndRemoveBinaryApks();

      // Merge two packs together, so we have all entries for final APKs inside one pack. If the
      // same entry is in both packs we prefer uncompressed one, because it means this entry
      // has very low compression ratio, it makes no sense to put it in compressed form.
      ModuleEntriesPack allEntriesPack =
          maybeCompressedEntriesPack.mergeWith(uncompressedEntriesPack);

      // Serialize and sign final APKs.
      ImmutableList<ListenableFuture<ApkDescription>> apkDescriptions =
          Streams.zip(
                  splitsByRelativePath.keySet().stream(),
                  binarySplits.stream(),
                  (relativePath, split) ->
                      executorService.submit(
                          () ->
                              serializeAndSignSplit(
                                  outputDirectory,
                                  relativePath,
                                  split,
                                  allEntriesPack,
                                  uncompressedEntriesPack)))
              .collect(toImmutableList());

      return ConcurrencyUtils.waitForAll(apkDescriptions).stream()
          .collect(toImmutableMap(apk -> ZipPath.create(apk.getPath()), identity()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ModuleEntriesPack buildCompressedEntriesPack(
      SerializationFilesManager filesManager, Collection<ModuleSplit> splits) {
    return use7ZipCompression
        ? build7ZipCompressedEntriesPack(filesManager, splits)
        : buildDeflateCompressedEntriesPack(filesManager, splits);
  }

  /** Builds pack with compressed entries using 7zip native tool. */
  private ModuleEntriesPack build7ZipCompressedEntriesPack(
      SerializationFilesManager filesManager, Collection<ModuleSplit> splits) {
    checkState(
        p7ZipCommand.isPresent(), "'p7ZipCommand' is required when 7zip compression is used.");
    ModuleEntriesPacker entriesPacker =
        new ModuleEntriesPacker(
            filesManager.getCompressedEntriesPackPath(), /* namePrefix= */ "c_");
    splits.stream()
        .flatMap(split -> split.getEntries().stream())
        .filter(entry -> !entry.getForceUncompressed())
        .forEach(entriesPacker::add);

    try (TempDirectory tempDirectory = new TempDirectory()) {
      return entriesPacker.pack(Zipper.compressedZip(p7ZipCommand.get(), tempDirectory.getPath()));
    }
  }

  /**
   * Builds pack with compressed entries, resource entries are compressed with the best compression
   * level (9) and all others with default compression level (6).
   */
  private ModuleEntriesPack buildDeflateCompressedEntriesPack(
      SerializationFilesManager filesManager, Collection<ModuleSplit> splits) {
    ModuleEntriesPacker otherEntriesPacker =
        new ModuleEntriesPacker(
            filesManager.getCompressedEntriesPackPath(), /* namePrefix= */ "c_");
    ModuleEntriesPacker resourceEntriesPacker =
        new ModuleEntriesPacker(
            filesManager.getCompressedResourceEntriesPackPath(), /* namePrefix= */ "r_");
    splits.stream()
        .flatMap(split -> split.getEntries().stream())
        .filter(entry -> !entry.getForceUncompressed())
        .forEach(
            entry -> {
              if (requiresAapt2Conversion(toApkEntryPath(entry.getPath()))) {
                resourceEntriesPacker.add(entry);
              } else {
                otherEntriesPacker.add(entry);
              }
            });

    ModuleEntriesPack resourceEntriesPack =
        resourceEntriesPacker.pack(
            Zipper.compressedZip(executorService, Deflater.BEST_COMPRESSION));
    ModuleEntriesPack otherEntriesPack =
        otherEntriesPacker.pack(
            Zipper.compressedZip(executorService, Deflater.DEFAULT_COMPRESSION));

    return resourceEntriesPack.mergeWith(otherEntriesPack);
  }

  private ModuleEntriesPack buildUncompressedEntriesPack(
      Path outputPath, Collection<ModuleSplit> splits, ModuleEntriesPack compressedPack) {
    ModuleEntriesPacker entriesPacker = new ModuleEntriesPacker(outputPath, /* namePrefix= */ "u_");
    splits.stream()
        .flatMap(split -> split.getEntries().stream())
        .filter(
            entry ->
                entry.getForceUncompressed()
                    || shouldUncompressBecauseOfLowRatio(entry, compressedPack))
        .forEach(entriesPacker::add);
    return entriesPacker.pack(Zipper.uncompressedZip());
  }

  private ApkDescription serializeAndSignSplit(
      Path outputDirectory,
      ZipPath apkRelativePath,
      ModuleSplit split,
      ModuleEntriesPack allEntriesPack,
      ModuleEntriesPack uncompressedEntriesPack) {
    Path outputPath = outputDirectory.resolve(apkRelativePath.toString());

    serializeSplit(outputPath, split, allEntriesPack, uncompressedEntriesPack);
    Optional<SigningDescription> signingDescription = apkSigner.signApk(outputPath, split);

    ApkDescription apkDescription =
        ApkDescriptionHelper.createApkDescription(apkRelativePath, split, signingDescription);
    notifyApkSerialized(apkDescription, split.getSplitType());

    return apkDescription;
  }

  private void serializeSplit(
      Path outputPath,
      ModuleSplit split,
      ModuleEntriesPack allEntriesPack,
      ModuleEntriesPack uncompressedEntriesPack) {
    FileUtils.createDirectories(outputPath.getParent());
    try (ZipArchive archive = new ZipArchive(outputPath)) {
      ImmutableMap<ZipPath, ModuleEntry> moduleEntriesByName =
          split.getEntries().stream()
              .collect(
                  toImmutableMap(
                      entry -> toApkEntryPath(entry.getPath()),
                      entry -> entry,
                      // If two entries end up at the same path in the APK, pick one arbitrarily.
                      // e.g. base/assets/foo and base/root/assets/foo.
                      (a, b) -> b));

      // Sorting entries by name for determinism.
      ImmutableList<ModuleEntry> sortedEntries =
          ImmutableList.sortedCopyOf(
              Comparator.comparing(e -> toApkEntryPath(e.getPath())), moduleEntriesByName.values());

      ZipSource zipSource =
          allEntriesPack.select(
              sortedEntries,
              entry -> toApkEntryPath(entry.getPath(), /* binaryApk= */ true).toString(),
              entry -> alignmentForEntry(entry, uncompressedEntriesPack));
      archive.add(zipSource);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns alignment for {@link ModuleEntry} inside APK.
   *
   * <p>Uncompressed native libraries inside APK must be aligned by 4096 and all other uncompressed
   * entries aligned by 4 bytes.
   */
  private static long alignmentForEntry(
      ModuleEntry entry, ModuleEntriesPack uncompressedEntriesPack) {
    if (!uncompressedEntriesPack.hasEntry(entry)) {
      return 0;
    }
    return entry.getPath().toString().endsWith(NATIVE_LIBRARIES_SUFFIX) ? 4096 : 4;
  }

  /**
   * Injects Android manifest and resource table which have special fields {@code
   * ModuleSplit.getAndroidManifest} and {@code ModuleSplit.getResourceTable} as regular module
   * entries into {@link ModuleSplit}.
   */
  private static ModuleSplit injectManifestAndResourceTableAsEntries(ModuleSplit split) {
    ImmutableList.Builder<ModuleEntry> splitEntries = ImmutableList.builder();

    splitEntries.add(
        ModuleEntry.builder()
            .setForceUncompressed(false)
            .setContent(
                ByteSource.wrap(
                    split.getAndroidManifest().getManifestRoot().getProto().toByteArray()))
            .setPath(SpecialModuleEntry.ANDROID_MANIFEST.getPath())
            .build());

    if (split.getResourceTable().isPresent()) {
      splitEntries.add(
          ModuleEntry.builder()
              .setForceUncompressed(true)
              .setContent(ByteSource.wrap(split.getResourceTable().get().toByteArray()))
              .setPath(SpecialModuleEntry.RESOURCE_TABLE.getPath())
              .build());
    }

    split.getEntries().stream()
        .filter(entry -> !SpecialModuleEntry.ANDROID_MANIFEST.getPath().equals(entry.getPath()))
        .filter(entry -> !SpecialModuleEntry.RESOURCE_TABLE.getPath().equals(entry.getPath()))
        .forEach(splitEntries::add);
    return split.toBuilder().setEntries(splitEntries.build()).build();
  }

  private ModuleSplit applyUncompressedGlobsAndUncompressedNativeLibraries(ModuleSplit split) {
    boolean uncompressNativeLibs =
        !split.getAndroidManifest().getExtractNativeLibsValue().orElse(true);
    return split.toBuilder()
        .setEntries(
            split.getEntries().stream()
                .map(
                    entry ->
                        shouldUncompressEntry(entry, uncompressNativeLibs)
                            ? entry.toBuilder().setForceUncompressed(true).build()
                            : entry)
                .collect(toImmutableList()))
        .build();
  }

  private boolean shouldUncompressEntry(ModuleEntry entry, boolean uncompressNativeLibs) {
    // If entry is already uncompressed no need to match it.
    if (entry.getForceUncompressed()) {
      return false;
    }
    return matchesForceUncompressedPath(entry)
        || matchesUncompressedNativeLib(entry, uncompressNativeLibs);
  }

  /** Whether entry is native library that should be uncompressed, . */
  private boolean matchesUncompressedNativeLib(ModuleEntry entry, boolean uncompressNativeLibs) {
    return uncompressNativeLibs
        && NATIVE_LIBRARIES_PATTERN.matcher(entry.getPath().toString()).matches();
  }

  /** Whether entry path in APK matches uncompressed globs specified in BundleConfig. */
  private boolean matchesForceUncompressedPath(ModuleEntry entry) {
    // Android manifest is always compressed.
    if (entry.getPath().equals(SpecialModuleEntry.ANDROID_MANIFEST.getPath())) {
      return false;
    }

    String path = toApkEntryPath(entry.getPath()).toString();
    if (uncompressedPathMatchers.matches(path)) {
      return true;
    }
    // Common extensions that should remain uncompressed because compression doesn't provide any
    // gains.
    if (!NO_DEFAULT_UNCOMPRESS_EXTENSIONS.enabledForVersion(bundletoolVersion)
        && ApkSerializerHelper.NO_COMPRESSION_EXTENSIONS.contains(
            FileUtils.getFileExtension(ZipPath.create(path)))) {
      return true;
    }
    return false;
  }

  /**
   * Whether module entry should be put in uncompressed form because savings for the entry is low.
   */
  private boolean shouldUncompressBecauseOfLowRatio(
      ModuleEntry moduleEntry, ModuleEntriesPack compressedPack) {
    Entry entry = compressedPack.getZipEntry(moduleEntry);
    long compressedSize = entry.getCompressedSize();
    long uncompressedSize = entry.getUncompressedSize();

    // Copying logic from aapt2: require at least 10% gains in savings.
    if (moduleEntry.getPath().startsWith("res")) {
      return uncompressedSize == 0 || (compressedSize + compressedSize / 10 > uncompressedSize);
    }
    return compressedSize >= uncompressedSize;
  }
}
