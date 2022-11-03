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

import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.ResourceOptimizations.CollapsedResourceNames;
import com.android.bundle.Config.ResourceOptimizations.ResourceTypeAndName;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.Aapt2Command.ConvertOptions;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.zipflinger.ZipArchive;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.inject.Inject;

/** Class to convert module splits from proto to binary format. */
class Aapt2ResourceConverter {

  private final Aapt2Command aapt2Command;
  private final ListeningExecutorService executorService;
  private final CollapsedResourceNames collapsedResourceNames;

  private final Supplier<Optional<Path>> resourceConfigSupplier;

  @Inject
  Aapt2ResourceConverter(
      Aapt2Command aapt2Command,
      ListeningExecutorService executorService,
      Bundle bundle,
      BundleConfig bundleConfig,
      TempDirectory tempDirectory) {
    this.aapt2Command = aapt2Command;
    this.executorService = executorService;
    this.collapsedResourceNames =
        bundleConfig.getOptimizations().getResourceOptimizations().getCollapsedResourceNames();
    resourceConfigSupplier =
        Suppliers.memoize(
            () -> createResourceConfig(tempDirectory, bundle, collapsedResourceNames));
  }

  /**
   * Converts module splits from proto format to binary format via invoking 'aapt2 convert'.
   *
   * <p>Returns a list of {@link ModuleSplit} with converted entries in the same order as in
   * original {@code allSplits} list.
   */
  public ImmutableList<ModuleSplit> convert(
      Collection<ModuleSplit> allSplits, SerializationFilesManager filesManager) {
    // Uncompress all resource entries we have in module splits and store them in uncompressed
    // form inside special zip pack. This is done because we may have the same entry duplicated
    // in multiple splits to uncompress them only once.
    ModuleEntriesPacker packer =
        new ModuleEntriesPacker(filesManager.getResourcesEntriesPackPath(), /* namePrefix= */ "r_");
    allSplits.stream()
        .flatMap(split -> split.getEntries().stream())
        .filter(
            entry ->
                ApkSerializerHelper.requiresAapt2Conversion(
                    ApkSerializerHelper.toApkEntryPath(entry.getPath())))
        .forEach(packer::add);
    ModuleEntriesPack allResourcesUncompressedPack = packer.pack(Zipper.uncompressedZip());

    ResourceConverter resourceConverter =
        new ResourceConverter(filesManager, allResourcesUncompressedPack);

    ImmutableList<ListenableFuture<ModuleSplit>> binarySplitFutures =
        allSplits.stream()
            .map(
                split ->
                    executorService.submit(() -> resourceConverter.convertResourcesToBinary(split)))
            .collect(toImmutableList());
    return ConcurrencyUtils.waitForAll(binarySplitFutures);
  }

  private class ResourceConverter {

    private final SerializationFilesManager filesManager;
    private final ModuleEntriesPack packWithResourceEntries;

    ResourceConverter(
        SerializationFilesManager filesManager, ModuleEntriesPack packWithResourceEntries) {
      this.filesManager = filesManager;
      this.packWithResourceEntries = packWithResourceEntries;
    }

    /** Converts resources in split from proto to binary format. */
    public ModuleSplit convertResourcesToBinary(ModuleSplit split) {
      try {
        Path protoApkPath = writePartialProtoApk(split);
        Path binaryApkPath = convertAndOptimizeProtoApk(split, protoApkPath);
        Files.delete(protoApkPath);

        return withConvertedEntries(split, binaryApkPath);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    /** Writes APK with only resource entries in proto format. */
    private Path writePartialProtoApk(ModuleSplit split) throws IOException {
      Path protoApkPath = filesManager.getNextAapt2ProtoApkPath();
      try (ZipArchive protoApk = new ZipArchive(protoApkPath)) {
        ImmutableList<ModuleEntry> entriesToConvert =
            split.getEntries().stream()
                .filter(
                    entry ->
                        ApkSerializerHelper.requiresAapt2Conversion(
                            ApkSerializerHelper.toApkEntryPath(entry.getPath())))
                .collect(toImmutableList());
        protoApk.add(
            packWithResourceEntries.select(
                entriesToConvert,
                entry -> ApkSerializerHelper.toApkEntryPath(entry.getPath()).toString()));
      }
      return protoApkPath;
    }

    /**
     * Invokes 'aapt2' convert and optimize. Returns path to APK with resources in binary format.
     */
    private Path convertAndOptimizeProtoApk(ModuleSplit split, Path protoApkPath) {
      Path binaryApkPath = filesManager.getNextAapt2BinaryApkPath();
      aapt2Command.convertApkProtoToBinary(
          protoApkPath,
          binaryApkPath,
          ConvertOptions.builder()
              .setForceSparseEncoding(split.getSparseEncoding())
              .setCollapseResourceNames(collapsedResourceNames.getCollapseResourceNames())
              .setDeduplicateResourceEntries(collapsedResourceNames.getDeduplicateResourceEntries())
              .setResourceConfigPath(resourceConfigSupplier.get())
              .build());
      return binaryApkPath;
    }

    /**
     * Replaces resource entries in original {@link ModuleSplit} with entries from converted APK.
     */
    private ModuleSplit withConvertedEntries(ModuleSplit split, Path binaryApkPath) {
      ZipFile binaryZip = filesManager.openBinaryApk(binaryApkPath);

      Stream<ModuleEntry> otherEntriesStream =
          split.getEntries().stream()
              .filter(
                  entry ->
                      !ApkSerializerHelper.requiresAapt2Conversion(
                          ApkSerializerHelper.toApkEntryPath(entry.getPath())));

      Stream<ModuleEntry> resourceEntriesStream =
          binaryZip.stream()
              .filter(zipEntry -> zipEntry.getName().startsWith("res/"))
              .map(
                  zipEntry ->
                      ModuleEntry.builder()
                          .setPath(ZipPath.create(zipEntry.getName()))
                          .setContent(ZipUtils.asByteSource(binaryZip, zipEntry))
                          .build());

      ModuleEntry manifestEntry =
          ModuleEntry.builder()
              .setContent(ZipUtils.asByteSource(binaryZip, binaryZip.getEntry(MANIFEST_FILENAME)))
              .setPath(SpecialModuleEntry.ANDROID_MANIFEST.getPath())
              .setForceUncompressed(false)
              .build();

      Optional<ModuleEntry> resourceTableEntry =
          Optional.ofNullable(binaryZip.getEntry("resources.arsc"))
              .map(
                  zipEntry ->
                      ModuleEntry.builder()
                          .setContent(ZipUtils.asByteSource(binaryZip, zipEntry))
                          .setPath(SpecialModuleEntry.RESOURCE_TABLE.getPath())
                          .setForceUncompressed(true)
                          .build());

      ImmutableList<ModuleEntry> allEntries =
          Streams.concat(
                  Stream.of(manifestEntry),
                  stream(resourceTableEntry),
                  resourceEntriesStream,
                  otherEntriesStream)
              .collect(toImmutableList());
      return split.toBuilder().setEntries(allEntries).build();
    }
  }

  private static Optional<Path> createResourceConfig(
      TempDirectory tempDirectory, Bundle bundle, CollapsedResourceNames collapsedResourceNames) {
    if (!collapsedResourceNames.getCollapseResourceNames()
        || (collapsedResourceNames.getNoCollapseResourcesCount() == 0
            && collapsedResourceNames.getNoCollapseResourceTypesCount() == 0)) {
      return Optional.empty();
    }
    try {
      Path resourceConfigPath =
          Files.createTempFile(tempDirectory.getPath(), "aapt2-resource", ".cfg");

      ImmutableList<String> configLines =
          Stream.concat(
                  collapsedResourceNames.getNoCollapseResourcesList().stream(),
                  expandExcludedResourceTypes(bundle, collapsedResourceNames).stream())
              .map(
                  typeAndName ->
                      String.format(
                          "%s/%s#no_collapse", typeAndName.getType(), typeAndName.getName()))
              .collect(toImmutableList());
      Files.write(resourceConfigPath, configLines);
      return Optional.of(resourceConfigPath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ImmutableList<ResourceTypeAndName> expandExcludedResourceTypes(
      Bundle bundle, CollapsedResourceNames collapsedResourceNames) {
    if (collapsedResourceNames.getNoCollapseResourceTypesCount() == 0) {
      return ImmutableList.of();
    }
    ImmutableSet<String> excludedTypes =
        ImmutableSet.copyOf(collapsedResourceNames.getNoCollapseResourceTypesList());
    return bundle.getModules().values().stream()
        .filter(bundleModule -> bundleModule.getResourceTable().isPresent())
        .map(bundleModule -> bundleModule.getResourceTable().get())
        .flatMap(resourceTable -> resourceTable.getPackageList().stream())
        .flatMap(resourcePackage -> resourcePackage.getTypeList().stream())
        .filter(type -> excludedTypes.contains(type.getName()))
        .flatMap(
            type ->
                type.getEntryList().stream()
                    .map(
                        entry ->
                            ResourceTypeAndName.newBuilder()
                                .setName(entry.getName())
                                .setType(type.getName())
                                .build()))
        .collect(toImmutableList());
  }
}
