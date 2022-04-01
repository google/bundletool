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

package com.android.tools.build.bundletool;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteStreams;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

/**
 * Provides centralized access to testdata files.
 *
 * <p>The {@code fileName} argument always starts with the "testdata/" directory.
 */
public class TestData {

  public static InputStream openStream(String fileName) {
    InputStream is = TestData.class.getResourceAsStream(fileName);
    checkArgument(is != null, "Testdata file '%s' not found.", fileName);
    return is;
  }

  public static Reader openReader(String fileName) {
    return new InputStreamReader(openStream(fileName), UTF_8);
  }

  public static byte[] readBytes(String fileName) {
    try (InputStream inputStream = openStream(fileName)) {
      return ByteStreams.toByteArray(inputStream);
    } catch (IOException e) {
      // Throw an unchecked exception to allow usage in lambda expressions.
      throw new UncheckedIOException(
          String.format("Failed to read contents of testdata file '%s'.", fileName), e);
    }
  }

  /** Copies the testdata resource into the temporary directory. */
  public static Path copyToTempDir(TemporaryFolder tmp, String testDataPath) throws Exception {
    Path testDataFilename = Paths.get(testDataPath).getFileName();
    Path outputFile = tmp.newFolder().toPath().resolve(testDataFilename);
    try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile.toFile())) {
      ByteStreams.copy(openStream(testDataPath), fileOutputStream);
    }
    return outputFile;
  }

  private TestData() {}
}
