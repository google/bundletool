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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FileUtils}. */
@RunWith(JUnit4.class)
public class FileUtilsTest {

  @Test
  public void getDistinctParentPaths_emptyInput_emptyOutput() throws Exception {
    List<Path> parents = FileUtils.getDistinctParentPaths(Arrays.asList());

    assertThat(parents).isEmpty();
  }

  @Test
  public void getDistinctParentPaths_identifiesParent() throws Exception {
    Path file = Paths.get("dir1", "dir2", "dir3", "file");
    Path dir = Paths.get("dir1", "dir2", "dir3");

    List<Path> parents = FileUtils.getDistinctParentPaths(Arrays.asList(file));

    assertThat(parents).containsExactly(dir);
  }

  @Test
  public void getDistinctParentPaths_removesDuplicates() throws Exception {
    Path dir = Paths.get("same-dir");
    Path file1 = Paths.get("same-dir", "file1");
    Path file2 = Paths.get("same-dir", "file2");
    Path file3 = Paths.get("same-dir", "file3");

    List<Path> parents = FileUtils.getDistinctParentPaths(Arrays.asList(file1, file2, file3));

    assertThat(parents).containsExactly(dir);
  }

  @Test
  public void equalContent_emptyStreams() throws Exception {
    String data = "";

    boolean equal =
        FileUtils.equalContent(
            new ByteArrayInputStream(data.getBytes(UTF_8)),
            new ByteArrayInputStream(data.getBytes(UTF_8)));

    assertThat(equal).isTrue();
  }

  @Test
  public void equalContent_sameLengthAndContent() throws Exception {
    String data = "abc";

    boolean equal =
        FileUtils.equalContent(
            new ByteArrayInputStream(data.getBytes(UTF_8)),
            new ByteArrayInputStream(data.getBytes(UTF_8)));

    assertThat(equal).isTrue();
  }

  @Test
  public void equalContent_sameLengthAndDifferentContent() throws Exception {
    String data1 = "abX";
    String data2 = "abY";

    boolean equal =
        FileUtils.equalContent(
            new ByteArrayInputStream(data1.getBytes(UTF_8)),
            new ByteArrayInputStream(data2.getBytes(UTF_8)));

    assertThat(equal).isFalse();
  }

  @Test
  public void equalContent_differentLength() throws Exception {
    String data1 = "ab";
    String data2 = "abc";

    boolean equal =
        FileUtils.equalContent(
            new ByteArrayInputStream(data1.getBytes(UTF_8)),
            new ByteArrayInputStream(data2.getBytes(UTF_8)));

    assertThat(equal).isFalse();
  }

  @Test
  public void getExtension_emptyFile() {
    assertThat(FileUtils.getFileExtension(Paths.get(""))).isEmpty();
  }

  @Test
  public void getExtension_fileNoExtension() {
    assertThat(FileUtils.getFileExtension(Paths.get("file"))).isEmpty();
  }

  @Test
  public void getExtension_fileInDirectoryNoExtension() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file"))).isEmpty();
  }

  @Test
  public void getExtension_fileInDirectoryNoExtension_EndWithDot() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file."))).isEmpty();
  }

  @Test
  public void getExtension_fileInDirectoryOneLetterExtension() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file.a"))).isEqualTo("a");
  }

  @Test
  public void getExtension_fileInDirectorySimpleExtension() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file.txt"))).isEqualTo("txt");
  }

  @Test
  public void getExtension_fileInDirectorySimpleExtension_EndsWithDot() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file.txt."))).isEqualTo("");
  }

  @Test
  public void getExtension_fileInDirectoryDoubleExtension() {
    assertThat(FileUtils.getFileExtension(Paths.get("directory", "file.pb.json")))
        .isEqualTo("json");
  }
}
