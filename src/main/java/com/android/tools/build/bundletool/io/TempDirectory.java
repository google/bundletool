/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Temporary directory that will get deleted recursively when closed. */
public class TempDirectory implements AutoCloseable {

  private final Path dirPath;

  /** Creates a new temporary directory with no prefix. */
  public TempDirectory() {
    this(/* prefix= */ null);
  }

  /**
   * Creates a new temporary directory with the given prefix.
   *
   * @param prefix Prefix for the name of the directory. Can be {@code null} for no prefix.
   */
  public TempDirectory(@Nullable String prefix) {
    try {
      dirPath = Files.createTempDirectory(prefix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Path getPath() {
    return dirPath;
  }

  @Override
  public void close() {
    closeWithRetry(/* numAttempt= */ 1);
  }

  private void closeWithRetry(int numAttempt) {
    try {
      MoreFiles.deleteRecursively(dirPath, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (FileSystemException e) {
      // See https://github.com/google/bundletool/issues/61
      // Retrying because Windows sometimes doesn't delete everything synchronously.
      if (e.getCause() instanceof DirectoryNotEmptyException) {
        if (numAttempt == 5) {
          throw new UncheckedIOException(
              "Unable to delete temporary directory after 5 attempts.", e);
        }
        try {
          Thread.sleep(200L);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        closeWithRetry(numAttempt + 1);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
