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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.ZipPath;
import java.nio.file.Files;
import java.nio.file.Path;

/** Common assertions about files/directories with standardized error messages. */
public final class FilePreconditions {

  /** Checks whether the given path doesn't denote an existing file. */
  public static void checkFileDoesNotExist(Path path) {
    checkArgument(!Files.exists(path), "File '%s' already exists.", path);
  }

  /** Checks whether the given path denotes a readable file. */
  public static void checkFileExistsAndReadable(Path path) {
    checkArgument(Files.exists(path), "File '%s' was not found.", path);
    checkArgument(Files.isReadable(path), "File '%s' is not readable.", path);
    checkArgument(!Files.isDirectory(path), "File '%s' is a directory.", path);
  }

  /** Checks whether the given path denotes an executable file. */
  public static void checkFileExistsAndExecutable(Path path) {
    checkArgument(Files.exists(path), "File '%s' was not found.", path);
    checkArgument(Files.isExecutable(path), "File '%s' is not executable.", path);
  }

  /**
   * Checks the extension of the given file.
   *
   * @param fileDescription description of the file to be used in an error message (eg. "Zip file",
   *     "APK")
   */
  public static void checkFileHasExtension(String fileDescription, ZipPath path, String extension) {
    checkFileHasExtension(fileDescription, path.getFileName().toString(), extension);
  }

  /**
   * Checks the extension of the given file.
   *
   * @param fileDescription description of the file to be used in an error message (eg. "Zip file",
   *     "APK")
   */
  public static void checkFileHasExtension(String fileDescription, Path path, String extension) {
    checkFileHasExtension(fileDescription, path.getFileName().toString(), extension);
  }

  private static void checkFileHasExtension(
      String fileDescription, String filename, String extension) {
    checkArgument(
        filename.endsWith(extension),
        "%s '%s' is expected to have '%s' extension.",
        fileDescription,
        filename,
        extension);
  }

  /** Checks whether the given path denotes an existing directory. */
  public static void checkDirectoryExists(Path path) {
    checkArgument(Files.exists(path), "Directory '%s' was not found.", path);
    checkArgument(Files.isDirectory(path), "'%s' is not a directory.", path);
  }

  /** Checks whether the given path denotes an existing empty directory. */
  public static void checkDirectoryExistsAndEmpty(Path path) {
    checkDirectoryExists(path);
    checkArgument(path.toFile().list().length == 0, "Directory '%s' is not empty.", path);
  }

  // Not meant to be instantiated.
  private FilePreconditions() {}
}
