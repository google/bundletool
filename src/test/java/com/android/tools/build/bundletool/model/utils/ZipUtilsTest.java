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

package com.android.tools.build.bundletool.model.utils;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
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
  public void allFileEntriesPaths_emptyZip() throws Exception {
    try (ZipFile zipFile = createZipFileWithFiles()) {
      ImmutableList<ZipPath> files =
          ZipUtils.allFileEntriesPaths(zipFile).collect(toImmutableList());
      assertThat(files).isEmpty();
    }
  }

  @Test
  public void allFileEntries_multipleFiles() throws Exception {
    try (ZipFile zipFile = createZipFileWithFiles("a", "b", "c")) {
      ImmutableList<ZipPath> files =
          ZipUtils.allFileEntriesPaths(zipFile).collect(toImmutableList());
      assertThat(files.stream().map(Path::toString).collect(toList()))
          .containsExactly("a", "b", "c");
    }
  }

  private ZipFile createZipFileWithFiles(String... fileNames) throws IOException {
    ZipBuilder zipBuilder = new ZipBuilder();
    for (String fileName : fileNames) {
      zipBuilder.addFileWithContent(ZipPath.create(fileName), new byte[1]);
    }
    Path zipPath = zipBuilder.writeTo(tmp.getRoot().toPath().resolve("output.jar"));
    return new ZipFile(zipPath.toFile());
  }
}
