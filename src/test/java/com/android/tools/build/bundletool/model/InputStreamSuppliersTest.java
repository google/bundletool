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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.TestUtils.toByteArray;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.MockitoAnnotations.initMocks;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class InputStreamSuppliersTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  public void fromFile_existingFile_ok() throws Exception {
    byte[] fileData = {'h', 'e', 'l', 'l', 'o'};
    Path filePath = tmp.newFile("file.txt").toPath();
    Files.write(filePath, fileData);

    InputStreamSupplier inputStreamSupplier = InputStreamSuppliers.fromFile(filePath);

    assertThat(toByteArray(inputStreamSupplier::get)).isEqualTo(fileData);
  }

  @Test
  public void fromFile_nonExistingFile_throws() throws Exception {
    Path nonExistentFile = tmp.getRoot().toPath().resolve("imaginary.txt");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> InputStreamSuppliers.fromFile(nonExistentFile));

    assertThat(exception).hasMessageThat().contains("be an existing regular file");
  }

  @Test
  public void fromFile_directory_throws() throws Exception {
    Path directory = tmp.getRoot().toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> InputStreamSuppliers.fromFile(directory));

    assertThat(exception).hasMessageThat().contains("be an existing regular file");
  }

  @Test
  public void fromFile_fileDeleted_throws() throws Exception {
    Path filePath = tmp.newFile("file.txt").toPath();

    InputStreamSupplier inputStreamSupplier = InputStreamSuppliers.fromFile(filePath);
    Files.delete(filePath);
    assertThrows(NoSuchFileException.class, inputStreamSupplier::get);
  }

  @Test
  public void fromBytes() throws Exception {
    final byte[] content = {1, 34, 123};
    InputStreamSupplier inputStreamSupplier = InputStreamSuppliers.fromBytes(content);
    assertThat(toByteArray(inputStreamSupplier::get)).isEqualTo(content);
  }

  @Mock ZipFile zipFile;

  @Test
  public void fromZipEntry_pathTooShort_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> InputStreamSuppliers.fromZipEntry(new ZipEntry(""), zipFile));
  }
}
