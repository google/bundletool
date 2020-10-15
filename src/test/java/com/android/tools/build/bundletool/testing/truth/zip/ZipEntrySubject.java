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

package com.android.tools.build.bundletool.testing.truth.zip;

import static com.android.tools.build.bundletool.testing.DateTimeConverter.fromLocalTimeToUtc;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.io.ByteStreams;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** A subject for a ZipEntry. */
public class ZipEntrySubject extends Subject {

  /**
   * The constant {@code 0} in DOS time converted to Java epoch time.
   *
   * <p>For reference see {@link java.util.zip.ZipUtils#extendedDosToJavaTime(long)}.
   */
  private static final long EXTENDED_DOS_TIME_EPOCH_START_TIMESTAMP = 312768000000L;

  private static final long ZIPFLINGER_ZIP_TIMESTAMP = 347158862000L;

  private final ZipEntry actual;
  private InputStream zip;

  public ZipEntrySubject(FailureMetadata failureMetadata, ZipInputStream zip, ZipEntry actual) {
    super(failureMetadata, actual);
    this.actual = actual;
    this.zip = zip;
  }

  public ZipEntrySubject(FailureMetadata failureMetadata, ZipFile zip, ZipEntry actual) {
    super(failureMetadata, actual);
    this.actual = actual;
    try {
      this.zip = zip.getInputStream(actual);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Subject.Factory<ZipEntrySubject, ZipEntry> zipEntries(ZipFile zip) {
    return new Subject.Factory<ZipEntrySubject, ZipEntry>() {
      @Override
      public ZipEntrySubject createSubject(FailureMetadata metadata, ZipEntry actual) {
        return new ZipEntrySubject(metadata, zip, actual);
      }
    };
  }

  public ZipEntrySubject withContent(byte[] expectedContent) throws IOException {
    assertWithMessage("File \"%s\" in zip file does not have the expected content", actual)
        .that(ByteStreams.toByteArray(zip))
        .isEqualTo(expectedContent);
    return this;
  }

  public ZipEntrySubject withSize(long size) {
    check("getSize()")
        .withMessage("File \"%s\" in zip file does not have the expected size", actual)
        .that(actual.getSize())
        .isEqualTo(size);
    return this;
  }

  public ZipEntrySubject withoutTimestamp() throws IOException {
    check("getCreationTime()")
        .withMessage("File \"%s\" in zip file has no creation time set", actual)
        .that(actual.getCreationTime())
        .isNull();
    check("getLastAccessTime()")
        .withMessage("File \"%s\" in zip file has no last access time set", actual)
        .that(actual.getLastAccessTime())
        .isNull();
    // Zip files internally use extended DOS time.
    assertWithMessage("File \"%s\" in zip file does not have the expected time", actual)
        .that(fromLocalTimeToUtc(actual.getTime()))
        .isAnyOf(EXTENDED_DOS_TIME_EPOCH_START_TIMESTAMP, ZIPFLINGER_ZIP_TIMESTAMP);
    return this;
  }

  public ZipEntrySubject thatIsCompressed() {
    if (actual.getMethod() != ZipEntry.DEFLATED) {
      failWithoutActual(
          simpleFact("expected to be compressed in the zip file but was uncompressed."));
    }
    return this;
  }

  public ZipEntrySubject thatIsUncompressed() {
    if (actual.getMethod() != ZipEntry.STORED) {
      failWithoutActual(
          simpleFact("expected to be uncompressed in the zip file but was compressed."));
    }
    return this;
  }

  public ZipEntrySubject thatIsDirectory() {
    if (!actual.isDirectory()) {
      failWithActual(simpleFact("expected to be a directory."));
    }
    return this;
  }
}
