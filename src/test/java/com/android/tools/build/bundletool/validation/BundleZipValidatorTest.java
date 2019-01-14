/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleZipValidatorTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;

  @Before
  public void setUp() {
    tempFolder = tmp.getRoot().toPath();
  }

  @Test
  public void validateBundleZipEntry_directory_throws() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addDirectory(ZipPath.create("directory"))
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      ArrayList<? extends ZipEntry> entries = Collections.list(bundleZip.entries());
      // Sanity check.
      assertThat(entries).hasSize(1);

      ValidationException exception =
          assertThrows(
              ValidationException.class,
              () -> new BundleZipValidator().validateBundleZipEntry(bundleZip, entries.get(0)));

      assertThat(exception).hasMessageThat().contains("zip file contains directory zip entry");
    }
  }

  @Test
  public void validateBundleZipEntry_file_ok() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("file.txt"), DUMMY_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      ArrayList<? extends ZipEntry> entries = Collections.list(bundleZip.entries());
      // Sanity check.
      assertThat(entries).hasSize(1);

      new BundleZipValidator().validateBundleZipEntry(bundleZip, entries.get(0));
    }
  }
}
