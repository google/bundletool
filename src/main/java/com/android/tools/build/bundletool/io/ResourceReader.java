/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.build.bundletool.io;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Helps to read resources */
public class ResourceReader {

  public ResourceReader() {}

  /** Returns a list of paths from a specified path using specific {@link FileSystem} */
  public ImmutableList<Path> listResourceFilesInFolder(String path)
      throws URISyntaxException, IOException {
    URL pathUrl = ResourceReader.class.getResource(path);
    return pathUrl.getProtocol().equals("jar")
        ? readFromJar(
            Paths.get(
                ResourceReader.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
            path)
        : readFromDirectory(Paths.get(pathUrl.toURI()));
  }

  private ImmutableList<Path> readFromDirectory(Path sourceDirectory) throws IOException {
    try (Stream<Path> stream = Files.list(sourceDirectory)) {
      return stream.collect(toImmutableList());
    }
  }

  private ImmutableList<Path> readFromJar(Path sourceJar, String path) throws IOException {
    try (FileSystem fs =
            FileSystems.newFileSystem(sourceJar, Thread.currentThread().getContextClassLoader());
        Stream<Path> stream = Files.list(fs.getPath(path))) {
      return stream.collect(toImmutableList());
    }
  }

  public ByteSource getResourceByteSource(String resourcePath) throws IOException {
    try (InputStream fileContentStream = ResourceReader.class.getResourceAsStream(resourcePath)) {
      return ByteSource.wrap(ByteStreams.toByteArray(fileContentStream));
    }
  }
}
