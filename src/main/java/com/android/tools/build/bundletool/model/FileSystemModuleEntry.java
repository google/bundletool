/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link ModuleEntry} which points to a file located on the filesystem.
 *
 * <p>It is the responsibility of the caller to ensure that the referenced file exists for the
 * lifetime of the {@link FileSystemModuleEntry} instance.
 *
 * <p>Always reflects current content of the underlying file.
 */
@AutoValue
public abstract class FileSystemModuleEntry implements ModuleEntry {

  @Override
  public abstract ZipPath getPath();

  @Override
  public abstract boolean isDirectory();

  abstract Path getFileSystemPath();

  @Override
  public InputStream getContent() {
    try {
      return BufferedIo.inputStream(getFileSystemPath());
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Error while reading file '%s'.", getFileSystemPath())
          .build();
    }
  }

  /**
   * Constructs a {@link ModuleEntry} representing the specified file on the filesystem.
   *
   * @param entryPath path of the module entry
   * @param fileSystemPath path of the underlying file
   */
  public static FileSystemModuleEntry ofFile(ZipPath entryPath, Path fileSystemPath) {
    checkArgument(
        Files.isRegularFile(fileSystemPath),
        "Expecting '%s' to be an existing regular file.",
        fileSystemPath);
    return create(entryPath, /* isDirectory= */ false, fileSystemPath);
  }

  private static FileSystemModuleEntry create(
      ZipPath entryPath, boolean isDirectory, Path fileSystemPath) {
    return new AutoValue_FileSystemModuleEntry(entryPath, isDirectory, fileSystemPath);
  }
}
