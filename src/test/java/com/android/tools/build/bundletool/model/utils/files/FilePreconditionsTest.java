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

package com.android.tools.build.bundletool.model.utils.files;

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExistsAndEmpty;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FilePreconditions} class. */
@RunWith(JUnit4.class)
public class FilePreconditionsTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void checkFileDoesNotExist_nonExistent_success() throws Exception {
    Path nonExistent = Paths.get("non-existent");

    checkFileDoesNotExist(nonExistent);
  }

  @Test
  public void checkFileDoesNotExist_existing_fail() throws Exception {
    Path validFile = tmp.newFile().toPath();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkFileDoesNotExist(validFile));

    assertThat(exception).hasMessageThat().matches("File '.*' already exists.");
  }

  @Test
  public void checkFileExistsAndReadable_nonExistent_fail() throws Exception {
    Path nonExistent = Paths.get("non-existent");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkFileExistsAndReadable(nonExistent));
    assertThat(exception).hasMessageThat().matches("File '.*' was not found.");
  }

  @Test
  public void checkFileExistsAndReadable_notAFile_fail() throws Exception {
    Path directory = tmp.newFolder().toPath();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkFileExistsAndReadable(directory));
    assertThat(exception).hasMessageThat().matches("File '.*' is a directory.");
  }

  @Test
  public void checkFileExistsAndReadable_success() throws Exception {
    Path validFile = tmp.newFile().toPath();

    checkFileExistsAndReadable(validFile);
  }

  @Test
  public void checkFileHasExtension_success() throws Exception {
    Path xmlFile = tmp.newFile("AndroidManifest.xml").toPath();

    checkFileHasExtension("Android manifest", xmlFile, ".xml");
  }

  @Test
  public void checkFileHasExtension_failure() throws Exception {
    Path xmlFile = tmp.newFile("AndroidManifest.xml").toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> checkFileHasExtension("Module", xmlFile, ".zip"));

    assertThat(exception)
        .hasMessageThat()
        .matches("Module 'AndroidManifest.xml' is expected to have '.zip' extension.");
  }

  @Test
  public void checkDirectoryExists_nonExistent_fail() {
    Path nonExistent = Paths.get("non-existent");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkDirectoryExists(nonExistent));
    assertThat(exception).hasMessageThat().matches("Directory '.*' was not found.");
  }

  @Test
  public void checkDirectoryExists_notADirectory_fail() throws Exception {
    Path notDirectory = tmp.newFile().toPath();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkDirectoryExists(notDirectory));
    assertThat(exception).hasMessageThat().matches("'.*' is not a directory.");
  }

  @Test
  public void checkDirectoryExists_success() throws Exception {
    Path validDirectory = tmp.newFolder().toPath();

    checkDirectoryExists(validDirectory);
  }

  @Test
  public void checkDirectoryExistsAndEmpty_nonExistent_fail() throws Exception {
    Path nonExistent = Paths.get("non-existent");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> checkDirectoryExistsAndEmpty(nonExistent));
    assertThat(exception).hasMessageThat().contains("was not found.");
  }

  @Test
  public void checkDirectoryExistsAndEmpty_notADirectory_fail() throws Exception {
    Path notDirectory = tmp.newFile().toPath();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> checkDirectoryExistsAndEmpty(notDirectory));
    assertThat(exception).hasMessageThat().contains("is not a directory.");
  }

  @Test
  public void checkDirectoryExistsAndEmpty_notEmpty_fail() throws Exception {
    Path notEmpty = tmp.newFolder().toPath();
    Files.createFile(notEmpty.resolve("a-file.txt"));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> checkDirectoryExistsAndEmpty(notEmpty));
    assertThat(exception).hasMessageThat().contains("is not empty.");
  }

  @Test
  public void checkDirectoryExistsAndEmpty_success() throws Exception {
    Path validDirectory = tmp.newFolder().toPath();

    checkDirectoryExistsAndEmpty(validDirectory);
  }
}
