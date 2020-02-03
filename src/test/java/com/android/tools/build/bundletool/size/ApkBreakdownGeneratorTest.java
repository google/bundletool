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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.SizesOuterClass.Breakdown;
import com.android.bundle.SizesOuterClass.Sizes;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.auto.value.AutoValue;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final byte[] BYTES = new byte[256];

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void computesBreakdown_resources() throws Exception {
    ZipEntryInfo entry =
        ZipEntryInfo.builder()
            .setName("resources.arsc")
            .setContent(BYTES)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(entry);

    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setResources(Sizes.newBuilder().setDiskSize(256).setDownloadSize(10))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - 256)
                        .setDownloadSize(downloadedArchiveSize - 10))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_resourcesMultiple() throws Exception {
    ZipEntryInfo compressedEntry =
        ZipEntryInfo.builder()
            .setName("res/raw/song01.ogg")
            .setContent(BYTES)
            .setCompress(true)
            .build();
    ZipEntryInfo resourceTable =
        ZipEntryInfo.builder()
            .setName("resources.arsc")
            .setContent(BYTES)
            .setCompress(false)
            .build();
    long compressedEntrySize = getCompressedSize(compressedEntry);
    Path archive = createZipArchiveWith(resourceTable, compressedEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setResources(
                    Sizes.newBuilder().setDiskSize(256 + compressedEntrySize).setDownloadSize(18))
                // Expecting the zip/gzip overheads to be accounted for in OTHER.
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - (256 + compressedEntrySize))
                        .setDownloadSize(downloadedArchiveSize - 18))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_dex() throws Exception {
    ZipEntryInfo dexEntry =
        ZipEntryInfo.builder().setName("classes.dex").setContent(BYTES).setCompress(false).build();
    Path archive = createZipArchiveWith(dexEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setDex(Sizes.newBuilder().setDiskSize(256).setDownloadSize(10))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - 256)
                        .setDownloadSize(downloadedArchiveSize - 10))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_assets() throws Exception {
    ZipEntryInfo assetsEntry =
        ZipEntryInfo.builder()
            .setName("assets/intro.mp4")
            .setContent(BYTES)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(assetsEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setAssets(Sizes.newBuilder().setDiskSize(256).setDownloadSize(10))
                // Expecting the zip/gzip overheads to be accounted for in OTHER.
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - 256)
                        .setDownloadSize(downloadedArchiveSize - 10))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_nativeLibs() throws Exception {
    ZipEntryInfo nativeLibEntry =
        ZipEntryInfo.builder()
            .setName("lib/arm64-v8a/libcrashalytics.so")
            .setContent(BYTES)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(nativeLibEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
    assertThat(breakdown)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            emptyBreakdownProto()
                .toBuilder()
                .setNativeLibs(Sizes.newBuilder().setDiskSize(256).setDownloadSize(10))
                .setOther(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize - 256)
                        .setDownloadSize(downloadedArchiveSize - 10))
                .setTotal(
                    Sizes.newBuilder()
                        .setDiskSize(archiveSize)
                        .setDownloadSize(downloadedArchiveSize))
                .build());
  }

  @Test
  public void computesBreakdown_other() throws Exception {
    ZipEntryInfo otherEntry =
        ZipEntryInfo.builder()
            .setName("org/hamcrest/something.cfg")
            .setContent(BYTES)
            .setCompress(false)
            .build();
    Path archive = createZipArchiveWith(otherEntry);
    long archiveSize = Files.size(archive);
    long downloadedArchiveSize = gzipOverArchive(Files.readAllBytes(archive)).length;

    Breakdown breakdown = ApkBreakdownGenerator.calculateBreakdown(archive);
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
    ZipEntry zipEntry = new ZipEntry(entry.getName());
    try (ZipOutputStream zos = new ZipOutputStream(ByteStreams.nullOutputStream())) {
      zipEntry.setMethod(ZipEntry.DEFLATED);
      zos.putNextEntry(zipEntry);
      zos.write(entry.getContent());
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
