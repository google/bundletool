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

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Ordered;
import com.google.common.truth.Subject;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A subject for a ZipInputStream. */
public class ZipFileSubject extends Subject {

  private final ZipFile actual;

  public ZipFileSubject(FailureMetadata failureMetadata, ZipFile actual) {
    super(failureMetadata, actual);
    this.actual = actual;
  }

  public void hasDirectory(String dirPath) {
    ZipEntry zipEntry = actual.getEntry(dirPath);
    if (zipEntry == null || !zipEntry.isDirectory()) {
      failWithoutActual(fact("expected to contain a directory entry with path", dirPath));
    }
  }

  public void doesNotHaveDirectory(String dirPath) {
    ZipEntry zipEntry = actual.getEntry(dirPath);
    if (zipEntry != null) {
      failWithoutActual(fact("expected not to contain a directory entry with path", dirPath));
    }
  }

  public ZipEntrySubject hasFile(String filePath) {
    ZipEntry zipEntry = actual.getEntry(filePath);
    if (zipEntry == null) {
      failWithoutActual(fact("expected to contain a file with path", filePath));
    }

    return check("file(%s)", filePath).about(ZipEntrySubject.zipEntries(actual)).that(zipEntry);
  }

  public void doesNotHaveFile(String filePath) {
    ZipEntry zipEntry = actual.getEntry(filePath);
    if (zipEntry != null) {
      failWithoutActual(fact("expected not to contain a file with path", filePath));
    }
  }

  public Ordered containsExactlyEntries(String... entryNames) {
    return assertThat(
            Collections.list(actual.entries()).stream().map(ZipEntry::getName).collect(toList()))
        .containsExactlyElementsIn(entryNames);
  }
}
