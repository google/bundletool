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

import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.Aapt2Command.ConvertOptions;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
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

  @Inject
  Aapt2ResourceConverter(Aapt2Command aapt2Command, ListeningExecutorService executorService) {
    this.aapt2Command = aapt2Command;
    this.executorService = executorService;
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
          ConvertOptions.builder().setForceSparseEncoding(split.getSparseEncoding()).build());
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
}
