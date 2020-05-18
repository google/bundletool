/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.size;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.SizesOuterClass.Breakdown;
import com.android.bundle.SizesOuterClass.Sizes;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.auto.value.AutoValue;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ApkBreakdownGenerator}. */
@RunWith(JUnit4.class)
public class ApkBreakdownGeneratorTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private final ApkBreakdownGenerator apkBreakdownGenerator = new ApkBreakdownGenerator();

  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void computesBreakdown_resources() throws Exception {
    byte[] resources = "I am a resouce table for an android app".getBytes(UTF_8);

    ZipEntryInfo entry =
        ZipEntryInfo.builder()
            .setName("resources.arsc")
            .setContent(resources)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(entry);

    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;
    long resourcesDownloadSize = compress(resources);

    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto().toBuilder()
                .setResources(
                    Sizes.newBuilder()
                        .setDiskSize(resources.length)
                        .setDownloadSize(resourcesDownloadSize))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - resources.length)
                        .setDownloadSize(downloadedArchiveSize - resourcesDownloadSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_resourcesMultiple() throws Exception {
    byte[] resourceTable = "I am a resouce table for an android app".getBytes(UTF_8);
    byte[] aResource = "I am a resource in an apk file".getBytes(UTF_8);
    ZipEntryInfo compressedEntry =
        ZipEntryInfo.builder()
            .setName("res/raw/song01.ogg")
            .setContent(aResource)
            .setCompress(true)
            .build();
    ZipEntryInfo resourceTableEntry =
        ZipEntryInfo.builder()
            .setName("resources.arsc")
            .setContent(resourceTable)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(resourceTableEntry, compressedEntry);
    long archiveSize = Files.size(archive);

    // Because of the way we compress each entry with a flush between each compression we can't
    // calculate this without just repeating the prod code.
    // However we do know that it should be larger than compressing both entries in one batch
    // but smaller than compressing the entries independently.
    long resourcesDownloadSize = 57;
    assertThat(resourcesDownloadSize)
        .isGreaterThan(compress(Bytes.concat(resourceTable, aResource)));
    assertThat(resourcesDownloadSize).isLessThan(compress(resourceTable) + compress(aResource));

    long resourcesDiskSize =
        getCompressedSize(compressedEntry) + getCompressedSize(resourceTableEntry);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto().toBuilder()
                .setResources(
                    Sizes.newBuilder()
                        .setDiskSize(resourcesDiskSize)
                        .setDownloadSize(resourcesDownloadSize))
                // Expecting the zip/gzip overheads to be accounted for in OTHER.
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - resourcesDiskSize)
                        .setDownloadSize(downloadedArchiveSize - resourcesDownloadSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_dex() throws Exception {
    byte[] dex = "this is the contents of a dex file".getBytes(UTF_8);

    ZipEntryInfo dexEntry =
        ZipEntryInfo.builder().setName("classes.dex").setContent(dex).setCompress(false).build();
    Path archive = createZipArchiveWith(dexEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;
    long dexDownloadSize = compress(dex);

    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto().toBuilder()
                .setDex(Sizes.newBuilder().setDiskSize(dex.length).setDownloadSize(dexDownloadSize))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - dex.length)
                        .setDownloadSize(downloadedArchiveSize - dexDownloadSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_assets() throws Exception {
    byte[] assets = "this is a game asset".getBytes(UTF_8);
    ZipEntryInfo assetsEntry =
        ZipEntryInfo.builder()
            .setName("assets/intro.mp4")
            .setContent(assets)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(assetsEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;
    long assetsDownloadSize = compress(assets);

    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto().toBuilder()
                .setAssets(
                    Sizes.newBuilder()
                        .setDiskSize(assets.length)
                        .setDownloadSize(assetsDownloadSize))
                // Expecting the zip/gzip overheads to be accounted for in OTHER.
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - assets.length)
                        .setDownloadSize(downloadedArchiveSize - assetsDownloadSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_nativeLibs() throws Exception {
    byte[] nativeLib = "this is a native lib".getBytes(UTF_8);
    ZipEntryInfo nativeLibEntry =
        ZipEntryInfo.builder()
            .setName("lib/arm64-v8a/libcrashalytics.so")
            .setContent(nativeLib)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(nativeLibEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;
    long nativeLibDownloadSize = compress(nativeLib);
    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto().toBuilder()
                .setNativeLibs(
                    Sizes.newBuilder()
                        .setDiskSize(nativeLib.length)
                        .setDownloadSize(nativeLibDownloadSize))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - nativeLib.length)
                        .setDownloadSize(downloadedArchiveSize - nativeLibDownloadSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_other() throws Exception {
    byte[] other = "this is a random datafile".getBytes(UTF_8);
    ZipEntryInfo otherEntry =
        ZipEntryInfo.builder()
            .setName("org/hamcrest/something.cfg")
            .setContent(other)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(otherEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = apkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void checkDeflaterSyncOverheadCorrect() throws Exception {
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, /* noWrap */ true);
    byte[] output = new byte[100];
    assertThat(deflater.deflate(output, 0, output.length, Deflater.SYNC_FLUSH))
        .isEqualTo(ApkCompressedSizeCalculator.DEFLATER_SYNC_OVERHEAD_BYTES);
  }

  private static byte[] gzipOverArchive(byte[] archive) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
      gzipOutputStream.write(archive);
    }
    return outputStream.toByteArray();
  }

  private static long getCompressedSize(ZipEntryInfo entry) throws Exception {
    if (!entry.getCompress()) {
      return entry.getContent().length;
    }
    return compress(entry.getContent());
  }

  private static long compress(byte[] data) throws IOException {
    ZipEntry zipEntry = new ZipEntry("entry");
    try (ZipOutputStream zos = new ZipOutputStream(ByteStreams.nullOutputStream())) {
      zipEntry.setMethod(ZipEntry.DEFLATED);
      zos.putNextEntry(zipEntry);
      zos.write(data);
      zos.closeEntry();
    }
    return zipEntry.getCompressedSize();
  }

  private Path createZipArchiveWith(ZipEntryInfo... entries) throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder();
    for (ZipEntryInfo entryInfo : entries) {
      if (entryInfo.getCompress()) {
        zipBuilder.addFileWithContent(ZipPath.create(entryInfo.getName()), entryInfo.getContent());
      } else {
        zipBuilder.addFileWithContent(
            ZipPath.create(entryInfo.getName()), entryInfo.getContent(), EntryOption.UNCOMPRESSED);
      }
    }
    return zipBuilder.writeTo(tmpDir.resolve("archive.apk"));
  }

  private static Breakdown emptyBreakdownProto() {
    return Breakdown.newBuilder()
        .setResources(Sizes.getDefaultInstance())
        .setAssets(Sizes.getDefaultInstance())
        .setDex(Sizes.getDefaultInstance())
        .setNativeLibs(Sizes.getDefaultInstance())
        .setOther(Sizes.getDefaultInstance())
        .setTotal(Sizes.getDefaultInstance())
        .build();
  }

  @AutoValue
  abstract static class ZipEntryInfo {
    public abstract String getName();

    @SuppressWarnings({"mutable", "AutoValueImmutableFields"})
    public abstract byte[] getContent();

    public abstract boolean getCompress();

    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setName(String name);

      public abstract Builder setContent(byte[] content);

      public abstract Builder setCompress(boolean compress);

      public abstract ZipEntryInfo build();
    }

    public static ZipEntryInfo.Builder builder() {
      return new AutoValue_ApkBreakdownGeneratorTest_ZipEntryInfo.Builder();
    }
  }
}
