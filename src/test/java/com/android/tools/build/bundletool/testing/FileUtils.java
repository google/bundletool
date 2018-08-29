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

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.rules.TemporaryFolder;

/** File utility functions specific to unit tests. */
public final class FileUtils {

  private static final SecureRandom random = new SecureRandom();

  /** Creates a plain text file in the temporary directory containing the given lines. */
  public static Path createFileWithLines(TemporaryFolder tmp, String... lines) throws IOException {
    return Files.write(tmp.newFile().toPath(), ImmutableList.copyOf(lines));
  }

  /**
   * Returns randomly generated name for the file.
   *
   * <p>The filename follows the pattern: prefix[random number]suffix.
   */
  public static String getRandomFileName(String prefix, String suffix) {
    return prefix + Long.toString(random.nextLong()) + suffix;
  }

  /**
   * Returns randomly generated file path in the given temp directory.
   *
   * <p>The filename follows the pattern: prefix[random number]suffix.
   */
  public static Path getRandomFilePath(TemporaryFolder tmp, String prefix, String suffix) {
    return tmp.getRoot().toPath().resolve(getRandomFileName(prefix, suffix));
  }

  // Do not instantiate.
  private FileUtils() {}
}
