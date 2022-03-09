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

import static com.android.tools.build.bundletool.model.CompressionLevel.NO_COMPRESSION;
import static com.android.tools.build.bundletool.model.CompressionLevel.SAME_AS_SOURCE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.READ;

import com.android.tools.build.bundletool.model.CompressionLevel;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.zipflinger.Entry;
import com.android.zipflinger.Location;
import com.android.zipflinger.Source;
import com.android.zipflinger.ZipWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.ZipEntry;

/** A {@link Source} which can change the compression of an entry. */
public final class ZipEntrySource extends Source {

  /**
   * Threshold of the size of APK entries above which the compressed bytes will be stored on disk
   * rather than in memory.
   *
   * <p>Magic number found empirically. Can be overridden using the system property
   * "bundletool.compression.ondisk.entrysize".
   */
  @VisibleForTesting
  static final int STORE_ON_DISK_THRESHOLD_BYTES =
      SystemEnvironmentProvider.DEFAULT_PROVIDER
          .getProperty("bundletool.compression.ondisk.entrysize")
          .map(Integer::parseInt)
          .orElse(1024 * 1024); // 1 MB

  private final Entry entry;
  private final Payload payload;
  private final CompressionLevel compressionLevel;

  private ZipEntrySource(
      Entry entry, ZipPath newEntryName, Payload payload, CompressionLevel compressionLevel) {
    super(newEntryName.toString());
    this.entry = checkNotNull(entry);
    this.payload = checkNotNull(payload);
    this.crc = entry.getCrc();
    this.uncompressedSize = entry.getUncompressedSize();
    this.compressedSize = payload.size();
    this.compressionLevel = compressionLevel;
    this.compressionFlag =
        compressionLevel.isCompressed()
                || (compressionLevel.equals(SAME_AS_SOURCE) && entry.isCompressed())
            ? (short) ZipEntry.DEFLATED
            : (short) ZipEntry.STORED;
  }

  /**
   * Creates a {@link ZipEntrySource} by recompressing or decompressing the given zip entry
   * according to the requested compression level.
   *
   * <p>The compression happens synchronously on the same thread.
   */
  public static ZipEntrySource create(
      ZipReader zipReader,
      Entry entry,
      CompressionLevel compressionLevel,
      TempDirectory tempDirectory)
      throws IOException {
    return create(
        zipReader, entry, ZipPath.create(entry.getName()), compressionLevel, tempDirectory);
  }

  /**
   * Same as {@link #create(ZipReader, Entry, CompressionLevel, TempDirectory} but changes the name
   * of the entry in the output zip to {@code newEntryName}.
   */
  public static ZipEntrySource create(
      ZipReader zipReader,
      Entry entry,
      ZipPath newEntryName,
      CompressionLevel compressionLevel,
      TempDirectory tempDirectory)
      throws IOException {
    Payload payload = buildPayload(zipReader, entry, compressionLevel, tempDirectory);
    return new ZipEntrySource(entry, newEntryName, payload, compressionLevel);
  }

  private static Payload buildPayload(
      ZipReader zipReader,
      Entry entry,
      CompressionLevel compressionLevel,
      TempDirectory tempDirectory)
      throws IOException {
    switch (compressionLevel) {
      case SAME_AS_SOURCE:
        return new FromZipPayload(zipReader, entry);

      case NO_COMPRESSION:
        return entry.isCompressed()
            ? new UncompressedPayload(zipReader, entry)
            : new FromZipPayload(zipReader, entry);

      case DEFAULT_COMPRESSION:
      case BEST_COMPRESSION:
        if (entry.getUncompressedSize() <= STORE_ON_DISK_THRESHOLD_BYTES) {
          ByteBuffer buffer = extractPayloadToByteBuffer(zipReader, entry, compressionLevel);
          return new InMemoryPayload(buffer);
        } else {
          Path file = extractPayloadToFile(zipReader, entry, compressionLevel, tempDirectory);
          return new OnDiskPayload(file);
        }
    }
    throw new AssertionError("Unreachable statement.");
  }

  private static Path extractPayloadToFile(
      ZipReader zipReader,
      Entry entry,
      CompressionLevel compressionLevel,
      TempDirectory tempDirectory)
      throws IOException {
    Path payloadFile = Files.createTempFile(tempDirectory.getPath(), "entry", ".payload");
    try (InputStream in = recompressedPayloadInputStream(zipReader, entry, compressionLevel)) {
      Files.copy(in, payloadFile, REPLACE_EXISTING);
    }
    return payloadFile;
  }

  private static ByteBuffer extractPayloadToByteBuffer(
      ZipReader zipReader, Entry entry, CompressionLevel compressionLevel) throws IOException {
    byte[] payloadBytes;
    try (InputStream in = recompressedPayloadInputStream(zipReader, entry, compressionLevel)) {
      payloadBytes = ByteStreams.toByteArray(in);
    }
    return ByteBuffer.wrap(payloadBytes);
  }

  @SuppressWarnings("MustBeClosedChecker") // Stream will be closed when the return value is closed.
  @MustBeClosed
  private static InputStream recompressedPayloadInputStream(
      ZipReader zipReader, Entry entry, CompressionLevel compressionLevel) {
    checkArgument(compressionLevel.isCompressed());
    Deflater deflater = new Deflater(compressionLevel.getValue(), /* nowrap= */ true);
    InputStream contentStream = zipReader.getUncompressedPayload(entry.getName());
    return new DeflaterInputStream(contentStream, deflater);
  }

  @Override
  public void prepare() {}

  @Override
  public long writeTo(ZipWriter writer) throws IOException {
    return payload.writeTo(writer);
  }

  public Entry getEntry() {
    return entry;
  }

  public String getEntryName() {
    return entry.getName();
  }

  public long getCompressedSize() {
    return compressedSize;
  }

  public long getUncompressedSize() {
    return uncompressedSize;
  }

  public CompressionLevel getCompressionLevel() {
    return compressionLevel;
  }

  public ZipEntrySource setAlignment(long alignment) {
    super.align(alignment);
    return this;
  }

  /** Payload of a zip entry as stored in the zip file. */
  private abstract static class Payload {
    /** Writes the payload in the zip file. */
    public abstract long writeTo(ZipWriter writer) throws IOException;

    /** Returns the size of the payload (equivalent to the compressed size). */
    public abstract long size();
  }

  /** A {@link Payload} whose bytes are kept in-memory. */
  private static final class InMemoryPayload extends Payload {

    private final ByteBuffer payloadBytes;

    InMemoryPayload(ByteBuffer payloadBytes) {
      this.payloadBytes = payloadBytes;
    }

    @Override
    public long writeTo(ZipWriter writer) throws IOException {
      return writer.write(payloadBytes);
    }

    @Override
    public long size() {
      return payloadBytes.limit();
    }
  }

  /** A {@link Payload} whose bytes are stored on disk. */
  private static final class OnDiskPayload extends Payload {

    private final Path payloadPath;
    private final long payloadSize;

    OnDiskPayload(Path payloadPath) throws IOException {
      this.payloadPath = payloadPath;
      this.payloadSize = Files.size(payloadPath);
    }

    @Override
    public long writeTo(ZipWriter writer) throws IOException {
      try (FileChannel channel = FileChannel.open(payloadPath, READ)) {
        writer.transferFrom(channel, 0, payloadSize);
      }
      // File is in a temp directory so will be deleted regardless, but this is an attempt at
      // freeing up disk space earlier.
      Files.delete(payloadPath);
      return payloadSize;
    }

    @Override
    public long size() {
      return payloadSize;
    }
  }

  /** A {@link Payload} whose bytes are the same as from another zip file. */
  private static final class FromZipPayload extends Payload {

    private final ZipReader zipReader;
    private final Entry entry;

    FromZipPayload(ZipReader zipReader, Entry entry) {
      this.zipReader = zipReader;
      this.entry = entry;
    }

    @Override
    public long writeTo(ZipWriter writer) {
      Location payloadLocation = entry.getPayloadLocation();
      zipReader.transferTo(writer, entry.getName());
      return payloadLocation.size();
    }

    @Override
    public long size() {
      return entry.getCompressedSize();
    }
  }

  /** A {@link Payload} read from a zip and uncompressed on the fly. */
  private static final class UncompressedPayload extends Payload {

    private final ZipReader zipReader;
    private final Entry entry;

    UncompressedPayload(ZipReader zipReader, Entry entry) {
      this.zipReader = zipReader;
      this.entry = entry;
    }

    @Override
    public long writeTo(ZipWriter writer) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(ZipReader.BUFFER_SIZE_BYTES);

      int totalBytes = 0;
      try (InputStream in = zipReader.getUncompressedPayload(entry.getName())) {
        // TODO: Replace this with write.transferFrom(Channels.newChannel(in)) once zipflinger
        // supports ReadableByteChannel.
        int read;
        while ((read = copy(in, buffer)) > 0 || buffer.position() != 0) {
          buffer.flip();
          totalBytes += writer.write(buffer);
          buffer.compact();
        }
      }
      checkState(totalBytes == entry.getUncompressedSize(), "Fewer bytes written than expected.");

      return totalBytes;
    }

    @SuppressWarnings("ByteBufferBackingArray") // ByteBuffer was constructed locally.
    private static int copy(InputStream in, ByteBuffer buffer) throws IOException {
      int read = in.read(buffer.array(), buffer.position(), buffer.remaining());
      if (read > 0) {
        buffer.position(buffer.position() + read);
      }
      return read;
    }

    @Override
    public long size() {
      return entry.getUncompressedSize();
    }
  }
}
