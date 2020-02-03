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

package com.android.tools.build.bundletool.testing;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.rules.TemporaryFolder;

/** File utility functions specific to unit tests. */
public final class FileUtils {

  /** Creates a plain text file in the temporary directory containing the given lines. */
  public static Path createFileWithLines(TemporaryFolder tmp, String... lines) throws IOException {
    return Files.write(tmp.newFile().toPath(), ImmutableList.copyOf(lines));
  }

  public static ImmutableSet<Path> getAllFilesInDirectory(Path directory) throws Exception {
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths.filter(Files::isRegularFile).map(Path::getFileName).collect(toImmutableSet());
    }
  }

  public static Path uncompressGzipFile(Path gzipPath, Path outputPath) throws Exception {
    try (GZIPInputStream gzipInputStream =
            new GZIPInputStream(new FileInputStream(gzipPath.toFile()));
        FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
      ByteStreams.copy(gzipInputStream, fileOutputStream);
    }
    return outputPath;
  }

  // Do not instantiate.
  private FileUtils() {}
}
