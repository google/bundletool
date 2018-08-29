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

package com.android.tools.build.bundletool.utils;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ZipUtils}. */
@RunWith(JUnit4.class)
public class ZipUtilsTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void getFilesWithPathPrefix_fileIsFound() throws Exception {
    Path zipPath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("META-INF/MANIFEST.MF"), "".getBytes(UTF_8))
            .writeTo(tmp.getRoot().toPath().resolve("output.jar"));
    ZipFile zipFile = new ZipFile(zipPath.toFile());

    List<ZipPath> files =
        ZipUtils.getFilesWithPathPrefix(zipFile, ZipPath.create("META-INF")).collect(toList());

    assertThat(files).containsExactly(ZipPath.create("META-INF/MANIFEST.MF"));
  }

  @Test
  public void getFilesWithPathPrefix_directoryDoesNotExists_emptyResult() throws Exception {
    Path zipPath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("META-INF/MANIFEST.MF"), "".getBytes(UTF_8))
            .writeTo(tmp.getRoot().toPath().resolve("output.jar"));
    ZipFile zipFile = new ZipFile(zipPath.toFile());

    List<ZipPath> files =
        ZipUtils.getFilesWithPathPrefix(zipFile, ZipPath.create("non-existent-dir"))
            .collect(toList());

    assertThat(files).isEmpty();
  }

  @Test
  public void getFilesWithPathPrefix_prefixPointsToFile_fileReturned() throws Exception {
    Path zipPath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("META-INF/MANIFEST.MF"), "".getBytes(UTF_8))
            .writeTo(tmp.getRoot().toPath().resolve("output.jar"));
    ZipFile zipFile = new ZipFile(zipPath.toFile());

    List<ZipPath> files =
        ZipUtils.getFilesWithPathPrefix(zipFile, ZipPath.create("META-INF/MANIFEST.MF"))
            .collect(toList());

    assertThat(files).containsExactly(ZipPath.create("META-INF/MANIFEST.MF"));
  }

  @Test
  public void getFilesWithPathPrefix_multipleFilesFound() throws Exception {
    Path zipPath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("META-INF/MANIFEST.MF"), "".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("META-INF/LICENSE.txt"), "".getBytes(UTF_8))
            .addFileWithContent(
                ZipPath.create("META-INF/services/org.xmlpull.v1.XmlPullParserFactory"),
                "".getBytes(UTF_8))
            .writeTo(tmp.getRoot().toPath().resolve("output.jar"));
    ZipFile zipFile = new ZipFile(zipPath.toFile());

    List<ZipPath> files =
        ZipUtils.getFilesWithPathPrefix(zipFile, ZipPath.create("META-INF")).collect(toList());

    assertThat(files)
        .containsExactly(
            ZipPath.create("META-INF/MANIFEST.MF"),
            ZipPath.create("META-INF/LICENSE.txt"),
            ZipPath.create("META-INF/services/org.xmlpull.v1.XmlPullParserFactory"));
  }
}
