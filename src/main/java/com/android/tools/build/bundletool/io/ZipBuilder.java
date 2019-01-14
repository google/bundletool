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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Builder for creating zip files.
 *
 * <p>The builder behaves lazily and does not write any output until {@link #writeTo(Path)} is
 * invoked.
 */
public final class ZipBuilder {
  private static final Long EPOCH = 0L;

  /** Entries to be output. */
  private final Map<ZipPath, Entry> entries = new LinkedHashMap<>();

  /**
   * Writes the data into a zip file.
   *
   * <p>It is an error if the <code>target</code> file already exists.
   *
   * @return The path the .zip file was written to (ie. <code>target</code>).
   * @throws IOException When an I/O error occurs.
   */
  public synchronized Path writeTo(Path target) throws IOException {
    // Create temp file and move to requested location when completely written. If the command
    // fails, this prevents us from generating partial output at the user-specified location.
    Path tempFile = Files.createTempFile("ZipBuilder-", ".zip.tmp");

    try {
      try (OutputStream out = BufferedIo.outputStream(tempFile);
          ZipOutputStream outZip = new ZipOutputStream(out)) {
        for (ZipPath path : entries.keySet()) {
          Entry entry = entries.get(path);
          if (entry.getIsDirectory()) {
            // For directories, we append "/" at the end of the file path since that's what the
            // ZipEntry class relies on.
            ZipEntry zipEntry = new ZipEntry(path + "/");
            zipEntry.setTime(EPOCH);
            outZip.putNextEntry(zipEntry);
            // Directories are represented as having empty content in a zip file, so we don't write
            // any bytes to the outZip for this entry.
          } else {
            ZipEntry zipEntry = new ZipEntry(path.toString());
            zipEntry.setTime(EPOCH);
            if (entry.hasOption(EntryOption.UNCOMPRESSED)) {
              zipEntry.setMethod(ZipEntry.STORED);
              // ZipFile API requires us to set the following properties manually for uncompressed
              // ZipEntries, just setting the compression method is not enough.
              try (InputStream is = entry.getInputStreamSupplier().get().get()) {
                byte[] entryData = ByteStreams.toByteArray(is);
                zipEntry.setSize(entryData.length);
                zipEntry.setCompressedSize(entryData.length);
                zipEntry.setCrc(computeCrc32(entryData));
                outZip.putNextEntry(zipEntry);
                outZip.write(entryData);
              }
            } else {
              outZip.putNextEntry(zipEntry);
              try (InputStream content = entry.getInputStreamSupplier().get().get()) {
                ByteStreams.copy(content, outZip);
              }
            }
          }
          outZip.closeEntry();
        }
      }

      // Fails if the target file exists.
      Files.move(tempFile, target);

    } catch (IOException e) {
      Files.deleteIfExists(tempFile);
      throw e;
    }

    return target;
  }

  /**
   * Lazily creates an entry at the specified path and with the given content.
   *
   * <p>Will throw an exception if the path is already taken.
   */
  public ZipBuilder addFileWithContent(ZipPath toPath, byte[] content, EntryOption... options) {
    return addFile(toPath, () -> new ByteArrayInputStream(content), options);
  }

  /**
   * Lazily copies the given file to the specified path.
   *
   * <p>Will throw an exception if the path is already taken.
   */
  public ZipBuilder addFileFromDisk(ZipPath toPath, File file, EntryOption... options) {
    checkArgument(file.isFile(), "Path '%s' does not denote a file.", file);
    return addFile(toPath, BufferedIo.inputStreamSupplier(file.toPath()), options);
  }

  /**
   * Lazily creates a file at the specified path containing the given proto message in binary form.
   *
   * <p>Will throw an exception if the path is already taken.
   */
  public ZipBuilder addFileWithProtoContent(
      ZipPath toPath, MessageLite protoMsg, EntryOption... options) {
    return addFile(toPath, () -> new ByteArrayInputStream(protoMsg.toByteArray()), options);
  }

  /**
   * Lazily copies the given zip file entry to the specified path.
   *
   * <p>Will throw an exception if the path is already taken.
   */
  public ZipBuilder addFileFromZip(
      ZipPath toPath, ZipFile fromZipFile, ZipEntry zipEntry, EntryOption... options) {
    return addFile(toPath, BufferedIo.inputStreamSupplier(fromZipFile, zipEntry), options);
  }

  /**
   * Lazily adds a file in the zip at the given location with the content of the {@code
   * inputStreamSupplier}.
   *
   * <p>Note that the input stream needs to remain available until the method {@link #writeTo(Path)}
   * has been invoked.
   */
  public ZipBuilder addFile(
      ZipPath toPath, InputStreamSupplier inputStreamSupplier, EntryOption... options) {
    return addEntryInternal(
        toPath,
        Entry.builder()
            .setIsDirectory(false)
            .setInputStreamSupplier(inputStreamSupplier)
            .setOptions(ImmutableSet.copyOf(options))
            .build());
  }

  /**
   * Lazily creates empty directory at the specified path.
   *
   * <p>Note that creating an empty directory is not required to add files into that directory.
   *
   * <p>Will throw an exception if the path is already taken.
   */
  public ZipBuilder addDirectory(ZipPath dir) {
    return addEntryInternal(
        dir, Entry.builder().setIsDirectory(true).setOptions(ImmutableSet.of()).build());
  }

  /** This is the single synchronized point for adding entries to this {@link ZipBuilder}. */
  private synchronized ZipBuilder addEntryInternal(ZipPath path, Entry entry) {
    checkArgument(entries.put(path, entry) == null, "Path '%s' is already taken.", path);
    return this;
  }

  /**
   * Copies all file entries from <code>srcZipFile</code> to <code>toDirectory</code> inside the
   * output being built. This operation cannot rewrite any already existing entry.
   *
   * <p>Note that zip directory entries (including empty directories) are not copied.
   */
  public ZipBuilder copyAllContentsFromZip(
      ZipPath toDirectory, ZipFile srcZipFile, EntryOption... entryOptions) {
    Enumeration<? extends ZipEntry> zipEntries = srcZipFile.entries();
    while (zipEntries.hasMoreElements()) {
      ZipEntry zipEntry = zipEntries.nextElement();
      if (!zipEntry.isDirectory()) {
        addFileFromZip(toDirectory.resolve(zipEntry.getName()), srcZipFile, zipEntry, entryOptions);
      }
    }

    return this;
  }

  /**
   * Internal data object holding properties of an entry to be written by the {@link ZipBuilder}.
   */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  protected abstract static class Entry {
    /** Absent for directory entries. */
    public abstract Optional<InputStreamSupplier> getInputStreamSupplier();

    public abstract boolean getIsDirectory();

    public abstract ImmutableSet<EntryOption> getOptions();

    public static Builder builder() {
      return new AutoValue_ZipBuilder_Entry.Builder();
    }

    public boolean hasOption(EntryOption option) {
      return getOptions().contains(option);
    }

    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setInputStreamSupplier(InputStreamSupplier inputStreamSupplier);

      public abstract Builder setIsDirectory(boolean isDirectory);

      public abstract Builder setOptions(ImmutableSet<EntryOption> options);

      public abstract Entry autoBuild();

      public Entry build() {
        Entry result = autoBuild();
        // ZipBuilder implementations may rely on this precondition.
        checkState(
            result.getInputStreamSupplier().isPresent() ^ result.getIsDirectory(),
            "Input stream supplier must be absent iff the entry is a directory.");
        return result;
      }
    }
  }

  /**
   * Additional properties of {@link Entry}s.
   *
   * <p>An option doesn't need to be supported by every {@link ZipBuilder} implementation, in which
   * case the unknown options are silently ignored.
   */
  public enum EntryOption {
    UNCOMPRESSED
  }

  private static long computeCrc32(byte[] data) {
    CRC32 crc32 = new CRC32();
    crc32.update(data);
    return crc32.getValue();
  }
}
