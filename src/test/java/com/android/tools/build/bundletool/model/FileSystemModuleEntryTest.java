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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.testing.TestUtils;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileSystemModuleEntryTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void ofFile_existingFile_ok() throws Exception {
    byte[] fileData = {'h', 'e', 'l', 'l', 'o'};
    Path filePath = tmp.newFile("file.txt").toPath();
    Files.write(filePath, fileData);

    FileSystemModuleEntry entry =
        FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), filePath);

    assertThat(entry.getPath()).isEqualTo(ZipPath.create("assets/file.txt"));
    assertThat(entry.isDirectory()).isFalse();
    assertThat(TestUtils.getEntryContent(entry)).isEqualTo(fileData);
  }

  @Test
  public void ofFile_nonExistingFile_throws() throws Exception {
    Path nonExistentFile = tmp.getRoot().toPath().resolve("imaginary.txt");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), nonExistentFile));

    assertThat(exception).hasMessageThat().contains("be an existing regular file");
  }

  @Test
  public void ofFile_directory_throws() throws Exception {
    Path directory = tmp.getRoot().toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), directory));

    assertThat(exception).hasMessageThat().contains("be an existing regular file");
  }

  @Test
  public void getContent_fileDeleted_throws() throws Exception {
    Path filePath = tmp.newFile("file.txt").toPath();

    FileSystemModuleEntry entry =
        FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), filePath);
    Files.delete(filePath);
    UncheckedIOException exception =
        assertThrows(UncheckedIOException.class, () -> entry.getContent());

    assertThat(exception).hasMessageThat().contains("Error while reading file");
  }

  @Test
  public void setCompression_setFalse_ok() throws Exception {
    Path filePath = tmp.newFile("file.txt").toPath();
    FileSystemModuleEntry entry =
        FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), filePath);
    assertThat(entry.shouldCompress()).isTrue();

    FileSystemModuleEntry compressedEntry = entry.setCompression(false);
    assertThat(compressedEntry.shouldCompress()).isFalse();
    assertThat(entry.getPath()).isEqualTo(compressedEntry.getPath());
    assertThat(entry.getFileSystemPath()).isEqualTo(compressedEntry.getFileSystemPath());
    assertThat(entry.isDirectory()).isEqualTo(compressedEntry.isDirectory());
    assertThat(TestUtils.getEntryContent(entry))
        .isEqualTo(TestUtils.getEntryContent(compressedEntry));
  }

  @Test
  public void setCompression_setTrue_ok() throws Exception {
    Path filePath = tmp.newFile("file.txt").toPath();
    FileSystemModuleEntry entry =
        FileSystemModuleEntry.ofFile(ZipPath.create("assets/file.txt"), filePath);
    assertThat(entry.shouldCompress()).isTrue();

    FileSystemModuleEntry compressedEntry = entry.setCompression(true);
    assertThat(entry.shouldCompress()).isTrue();
    assertThat(entry.getPath()).isEqualTo(compressedEntry.getPath());
    assertThat(entry.getFileSystemPath()).isEqualTo(compressedEntry.getFileSystemPath());
    assertThat(entry.isDirectory()).isEqualTo(compressedEntry.isDirectory());
    assertThat(TestUtils.getEntryContent(entry))
        .isEqualTo(TestUtils.getEntryContent(compressedEntry));
  }
}
