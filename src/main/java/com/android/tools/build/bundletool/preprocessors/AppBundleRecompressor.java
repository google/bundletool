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
package com.android.tools.build.bundletool.preprocessors;

import static com.android.tools.build.bundletool.model.AppBundle.METADATA_DIRECTORY;
import static com.android.tools.build.bundletool.model.CompressionLevel.DEFAULT_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.NO_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.SAME_AS_SOURCE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.io.ApkSerializerHelper;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.io.ZipEntrySource;
import com.android.tools.build.bundletool.io.ZipEntrySourceFactory;
import com.android.tools.build.bundletool.io.ZipReader;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.CompressionLevel;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.validation.BundleConfigValidator;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Re-compresses almost all entries from the App Bundle.
 *
 * <p>This is useful to be able to rely on the compressed data in the App Bundle and copy
 * byte-for-byte the compressed data of entries from the App Bundle in the APK instead of
 * re-compressing it for each APK.
 */
public final class AppBundleRecompressor {

  /**
   * Threshold for an entry size below which we consider that compressing its payload in a separate
   * thread would not provide any speed benefit.
   *
   * <p>Magic number found empirically. Can be overridden using the system property
   * "bundletool.compression.newthread.entrysize".
   */
  private static final long LARGE_ENTRY_SIZE_THRESHOLD_BYTES =
      SystemEnvironmentProvider.DEFAULT_PROVIDER
          .getProperty("bundletool.compression.newthread.entrysize")
          .map(Long::parseLong)
          .orElse(100_000L);

  private final ListeningExecutorService executor;
  private final ModuleCompressionManager moduleCompressionManager;

  public AppBundleRecompressor(ExecutorService executor) {
    this.executor = MoreExecutors.listeningDecorator(executor);
    this.moduleCompressionManager = new ModuleCompressionManager();
  }

  public void recompressAppBundle(File inputFile, File outputFile) {
    try (ZipReader zipReader = ZipReader.createFromFile(inputFile.toPath());
        ZipArchive newBundle = new ZipArchive(outputFile);
        TempDirectory tempDirectory = new TempDirectory(getClass().getSimpleName())) {
      ZipEntrySourceFactory sourceFactory = new ZipEntrySourceFactory(zipReader, tempDirectory);
      List<ListenableFuture<ZipEntrySource>> sources = new ArrayList<>();

      BundleConfig bundleConfig = extractBundleConfig(zipReader);
      ImmutableSet<String> uncompressedAssetsModules =
          extractModulesWithUncompressedAssets(zipReader, bundleConfig);
      CompressionManager compressionManager =
          new CompressionManager(bundleConfig, uncompressedAssetsModules);

      for (Entry entry : zipReader.getEntries().values()) {
        CompressionLevel compressionLevel = compressionManager.getCompressionLevel(entry);
        // Everything reads off the same file, so parallelization would be discouraged, however
        // compression is so slow that we still get benefits from parallelizing the compression of
        // large entries. FileChannel transfers and decompression are very fast as well so no
        // parallelization there either.
        if (compressionLevel.equals(SAME_AS_SOURCE)
            || compressionLevel.equals(NO_COMPRESSION)
            || entry.getUncompressedSize() < LARGE_ENTRY_SIZE_THRESHOLD_BYTES) {
          sources.add(immediateFuture(sourceFactory.create(entry, compressionLevel)));
        } else {
          sources.add(executor.submit(() -> sourceFactory.create(entry, compressionLevel)));
        }
      }

      // We don't care about the order of entries in the re-compressed bundle, we just take them
      // as they're ready.
      for (ListenableFuture<ZipEntrySource> sourceFuture : Futures.inCompletionOrder(sources)) {
        ZipEntrySource source = Futures.getUnchecked(sourceFuture);
        if (source.getCompressionLevel().isCompressed()
            && source.getCompressedSize() >= source.getUncompressedSize()) {
          // No benefit in compressing, leave the file uncompressed.
          newBundle.add(sourceFactory.create(source.getEntry(), NO_COMPRESSION));
        } else {
          newBundle.add(source);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BundleConfig extractBundleConfig(ZipReader zipReader) {
    // The bundle hasn't been validated yet, therefore selected validations need to be run manually.
    if (!zipReader.getEntry(AppBundle.BUNDLE_CONFIG_FILE_NAME).isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The archive doesn't seem to be an App Bundle, it is missing required file '%s'.",
              AppBundle.BUNDLE_CONFIG_FILE_NAME)
          .build();
    }
    try (InputStream inputStream =
        zipReader.getUncompressedPayload(AppBundle.BUNDLE_CONFIG_FILE_NAME)) {
      BundleConfig bundleConfig =
          BundleConfig.parseFrom(inputStream, ExtensionRegistryLite.getEmptyRegistry());
      new BundleConfigValidator().validateCompression(bundleConfig.getCompression());
      return bundleConfig;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the compression level to be used for the given entry in the newly created App Bundle.
   *
   * <ul>
   *   <li>The compression of entries that don't end up in the final APKs doesn't matter, so we
   *       re-use whatever compression was used in the source bundle.
   *   <li>Asset files in asset modules are left uncompressed unless configured otherwise in
   *       BundleConfig.
   *   <li>Entries that will be processed by aapt2 will be uncompressed, so that aapt2 does not
   *       attempt to re-compress them during the conversion to binary format (aapt2 preserves the
   *       compression of the entries of the input APK). This is because we don't want to rely on
   *       aapt2 compression today, thus we will re-compress anything that comes out of aapt2.
   *   <li>Entries that must end up uncompressed in the APK could be uncompressed at this stage, but
   *       we choose to keep whatever compression was used in the original bundle (usually, they
   *       will be compressed) to avoid producing a bundle excessively big. Even if those entries
   *       are uncompressed multiple times when serializing the final APKs, this is a tradeoff to
   *       keep the size on disk of this re-compressed bundle reasonable.
   *   <li>Entries that must end up compressed in the APK (e.g. dex files) need to be re-compressed
   *       so that we can guarantee the compression level in the APK (since the compression level
   *       used in the bundle is unknown).
   * </ul>
   */
  private static class CompressionManager {

    private static final ZipPath ASSETS_DIRECTORY = ZipPath.create("assets");

    private final ImmutableList<PathMatcher> uncompressedPathMatchers;
    private final ImmutableSet<String> uncompressedAssetsModuleNames;

    CompressionManager(
        BundleConfig bundleConfig, ImmutableSet<String> uncompressedAssetsModuleNames) {
      this.uncompressedPathMatchers =
          bundleConfig.getCompression().getUncompressedGlobList().stream()
              .map(PathMatcher::createFromGlob)
              .collect(toImmutableList());
      this.uncompressedAssetsModuleNames = uncompressedAssetsModuleNames;
    }

    CompressionLevel getCompressionLevel(Entry entry) {
      ZipPath bundleEntryPath = ZipPath.create(entry.getName());
      Optional<ZipPath> pathInModule = toPathInModule(bundleEntryPath);

      if (!pathInModule.isPresent()) {
        return SAME_AS_SOURCE;
      }

      String moduleName = bundleEntryPath.getName(0).toString();
      if (pathInModule.get().startsWith(ASSETS_DIRECTORY)
          && uncompressedAssetsModuleNames.contains(moduleName)) {
        return NO_COMPRESSION;
      }

      ZipPath pathInApk = ApkSerializerHelper.toApkEntryPath(pathInModule.get());
      if (ApkSerializerHelper.requiresAapt2Conversion(pathInApk)) {
        return NO_COMPRESSION;
      }

      if (uncompressedPathMatchers.stream()
          .anyMatch(matcher -> matcher.matches(pathInApk.toString()))) {
        return SAME_AS_SOURCE;
      }

      return DEFAULT_COMPRESSION;
    }

    private static Optional<ZipPath> toPathInModule(ZipPath pathInBundle) {
      if (pathInBundle.getNameCount() <= 1 || pathInBundle.startsWith(METADATA_DIRECTORY)) {
        return Optional.empty();
      }
      return Optional.of(pathInBundle.subpath(1, pathInBundle.getNameCount()));
    }
  }

  private ImmutableSet<String> extractModulesWithUncompressedAssets(
      ZipReader zipReader, BundleConfig config) {
    return getAllManifestsByModuleName(zipReader).entrySet().stream()
        .filter(
            entry -> {
              AndroidManifest manifest = entry.getValue();
              return moduleCompressionManager.shouldForceUncompressAssets(config, manifest);
            })
        .map(Map.Entry::getKey)
        .collect(toImmutableSet());
  }

  private static ImmutableMap<String, AndroidManifest> getAllManifestsByModuleName(
      ZipReader zipReader) {
    ZipPath manifestSuffixPath = ZipPath.create("manifest/AndroidManifest.xml");

    return zipReader.getEntries().keySet().stream()
        .map(ZipPath::create)
        .filter(zipPath -> zipPath.getNameCount() == 3 && zipPath.endsWith(manifestSuffixPath))
        .collect(
            toImmutableMap(
                zipPath -> zipPath.getName(0).toString(),
                zipPath -> parseAndroidManifest(zipReader, zipPath)));
  }

  private static AndroidManifest parseAndroidManifest(ZipReader zipReader, ZipPath path) {
    try (InputStream inputStream = zipReader.getUncompressedPayload(path.toString())) {
      return AndroidManifest.create(
          XmlNode.parseFrom(inputStream, ExtensionRegistryLite.getEmptyRegistry()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
