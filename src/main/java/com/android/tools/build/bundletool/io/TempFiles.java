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

package com.android.tools.build.bundletool.io;

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;

import com.google.common.base.Throwables;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Utility class for working with temporary files. */
public final class TempFiles {

  private static final Logger logger = Logger.getLogger(TempFiles.class.getName());

  /**
   * Creates a new temp directory, executes given action with the temp directory and deletes all its
   * content afterwards.
   *
   * <p>If action throws an {@link IOException}, it gets wrapped into a {@link RuntimeException} and
   * re-thrown.
   *
   * <p>Failure to delete the temp directory gets logged.
   *
   * @param action Action to be executed with the created temp directory.
   * @param <T> Return value of the {@code action}.
   * @return Value returned by the action.
   * @throws RuntimeException When action throws an unhandled {@link IOException}.
   */
  public static <T> T withTempDirectoryReturning(ActionWithTemporaryDirectory<T> action) {
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("temp");
      return action.apply(tempDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (tempDir != null) {
        deleteRecursivelyIgnoringErrors(tempDir);
      }
    }
  }

  /**
   * Same as {@link #withTempDirectoryReturning(ActionWithTemporaryDirectory)}, but returns nothing.
   */
  public static void withTempDirectory(VoidActionWithTemporaryDirectory action) {
    ActionWithTemporaryDirectory<Void> wrappedAction =
        tempDir -> {
          action.apply(tempDir);
          return null;
        };
    withTempDirectoryReturning(wrappedAction);
  }

  /**
   * Attempts to delete the file or directory at the given path recursively.
   *
   * <p>Any {@link IOException} is just logged but not propagated.
   */
  private static void deleteRecursivelyIgnoringErrors(Path path) {
    try {
      // Using "ALLOW_INSECURE" so that it works on some filesystems that have restrictions.
      MoreFiles.deleteRecursively(path, ALLOW_INSECURE);
    } catch (IOException e) {
      logger.warning(
          String.format("Error deleting path '%s': %s", path, Throwables.getStackTraceAsString(e)));
    }
  }

  /**
   * Allows to write lambdas throwing {@link IOException}s and returning a generic value.
   *
   * @param <T> Result of the action.
   */
  public interface ActionWithTemporaryDirectory<T> {
    T apply(Path tempDir) throws IOException;
  }

  /** Allows to write lambdas throwing {@link IOException}s. */
  public interface VoidActionWithTemporaryDirectory {
    void apply(Path tempDir) throws IOException;
  }

  private TempFiles() {}
}
