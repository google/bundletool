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

import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class FileSystemModuleEntry implements ModuleEntry {

  @Override
  public abstract ZipPath getPath();

  @Override
  public abstract boolean isDirectory();

  @Override
  public abstract boolean shouldCompress();

  abstract Path getFileSystemPath();

  @MustBeClosed
  @Override
  public InputStream getContent() {
    try {
      return BufferedIo.inputStream(getFileSystemPath());
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading file '%s'.", getFileSystemPath()), e);
    }
  }

  @Override
  public FileSystemModuleEntry setCompression(boolean shouldCompress) {
    if (shouldCompress == shouldCompress()) {
      return this;
    }
    return create(getPath(), isDirectory(), shouldCompress, getFileSystemPath());
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
    return create(entryPath, /* isDirectory= */ false, /* shouldCompress= */ true, fileSystemPath);
  }

  private static FileSystemModuleEntry create(
      ZipPath entryPath, boolean isDirectory, boolean shouldCompress, Path fileSystemPath) {
    return new AutoValue_FileSystemModuleEntry(
        entryPath, isDirectory, shouldCompress, fileSystemPath);
  }
}
