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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardOpenOption.READ;

import com.android.tools.build.bundletool.io.ZipReader.EntryNotFoundException;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.zipflinger.Entry;
import com.android.zipflinger.Location;
import com.android.zipflinger.PayloadInputStream;
import com.android.zipflinger.ZipMap;
import com.android.zipflinger.ZipWriter;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Parses a zip file, and allows to read entries and their content. */
public final class ZipReader implements AutoCloseable {

  /** The parsed map of the zip file. */
  private final ZipMap zipMap;

  /**
   * The file channel to the zip file.
   *
   * <p>Should remain open until {@code this} is closed.
   */
  private final FileChannel fileChannel;

  private ZipReader(ZipMap zipMap, FileChannel fileChannel) {
    this.zipMap = zipMap;
    this.fileChannel = fileChannel;
  }

  /** Creates an instance of {@link ZipReader} for the given zip file. */
  @MustBeClosed
  public static ZipReader createFromFile(Path zipFile) {
    checkNotNull(zipFile);
    checkArgument(Files.exists(zipFile));
    try {
      return new ZipReader(ZipMap.from(zipFile.toFile()), FileChannel.open(zipFile, READ));
    } catch (IllegalStateException e) {
      // Zipflinger library throws IllegalStateExceptions when the zip has a bad format.
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The file does not seem to be a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Returns the map of entries inside the zip file (per the CD) keyed by their name. */
  public ImmutableMap<String, Entry> getEntries() {
    return ImmutableMap.copyOf(zipMap.getEntries());
  }

  /** Returns the metadata about a zip entry from its name. */
  public Optional<Entry> getEntry(String entryName) {
    return Optional.ofNullable(zipMap.getEntries().get(entryName));
  }

  /**
   * Returns an {@link InputStream} of the payload of a zip entry as stored in the zip file.
   *
   * <p>The returned {@link InputStream} does not need to be closed.
   */
  public InputStream getPayload(String entryName) {
    Entry entry =
        getEntry(entryName)
            .orElseThrow(() -> new EntryNotFoundException(zipMap.getFile(), entryName));
    return getEntryPayload(entry);
  }

  /** Returns an {@link InputStream} of the uncompressed payload of a zip entry. */
  @MustBeClosed
  public InputStream getUncompressedPayload(String entryName) {
    Entry entry =
        getEntry(entryName)
            .orElseThrow(() -> new EntryNotFoundException(zipMap.getFile(), entryName));
    InputStream entryPayload = getEntryPayload(entry);
    if (!entry.isCompressed()) {
      return entryPayload;
    }
    Inflater inflater = new Inflater(/* nowrap= */ true); // nowrap = gzip compatible
    return new InflaterInputStream(entryPayload, inflater);
  }

  private InputStream getEntryPayload(Entry entry) {
    try {
      return new PayloadInputStream(fileChannel, entry.getPayloadLocation());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Copies the bytes of the payload of a zip entry to the given {@link ZipWriter}.
   *
   * <p>The copy happens using {@link FileChannel#transferTo} which takes advantage of filesystem
   * cache, making it a very I/O efficient way to copy data across files.
   */
  public void transferTo(ZipWriter zipWriter, String entryName) {
    Entry entry =
        getEntry(entryName)
            .orElseThrow(() -> new EntryNotFoundException(zipMap.getFile(), entryName));
    Location payloadLocation = entry.getPayloadLocation();
    try {
      zipWriter.transferFrom(fileChannel, payloadLocation.first, payloadLocation.size());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() throws IOException {
    fileChannel.close();
  }

  /** Exception thrown when an entry is searched for but not found in a zip file. */
  static class EntryNotFoundException extends CommandExecutionException {
    EntryNotFoundException(File zipFile, String entryName) {
      super(
          /* userMessage= */ "Internal error.",
          /* internalMessage= */ String.format(
              "Entry '%s' not found in zip file '%s'.", entryName, zipFile.getPath()));
    }
  }
}
