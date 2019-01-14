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
package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ModuleEntry} which points to an entry inside a {@link ZipFile}.
 *
 * <p>It is the responsibility of the caller to ensure that the referenced {@link ZipFile} stays
 * opened for the lifetime of the {@link ModuleZipEntry} instance.
 */
// This class is only immutable as long as the underlying zip file doesn't change, but if the zip
// file changes, nothing we're doing in bundletool makes sense.
@SuppressWarnings("Immutable")
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ModuleZipEntry implements ModuleEntry {

  abstract ZipEntry getZipEntry();

  abstract ZipFile getZipFile();

  /**
   * Expresses how many path names of {@link #getZipEntry()} need to be skipped in order to produce
   * a path that is relative to the module directory.
   *
   * <p>Equal to 1 when this instance represents an entry in a bundle zip. This is because in the
   * bundle zip top-level directories denote module names (eg. "module_name/res/drawable.icon"),
   * hence 1 path name needs to be skipped when resolving relative path of a module entry.
   *
   * <p>Equal to 0 when this instance represents an entry in a module zip. In this case module
   * directory structure starts directly at the root of the zip file (eg. "res/drawable.icon").
   */
  abstract int getPathNamesToSkip();

  /**
   * Expresses whether this entry is compressed or uncompressed in the final output APK. This might
   * not match the compression method of the input ZipEntry.
   */
  @Override
  public abstract boolean shouldCompress();

  @MustBeClosed
  @Override
  public InputStream getContent() {
    try {
      return BufferedIo.inputStream(getZipFile(), getZipEntry());
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading the zip entry '%s'.", getZipEntry().getName()), e);
    }
  }

  @Override
  public ZipPath getPath() {
    ZipPath path = ZipPath.create(getZipEntry().getName());
    return path.subpath(getPathNamesToSkip(), path.getNameCount());
  }

  @Override
  public boolean isDirectory() {
    return getZipEntry().isDirectory();
  }

  @Override
  public ModuleZipEntry setCompression(boolean shouldCompress) {
    if (shouldCompress == shouldCompress()) {
      return this;
    }
    return create(getZipEntry(), getZipFile(), getPathNamesToSkip(), shouldCompress);
  }

  /** Constructs a {@link ModuleEntry} for {@link ZipEntry} contained in a bundle zip file. */
  public static ModuleZipEntry fromBundleZipEntry(ZipEntry zipEntry, ZipFile zipFile) {
    return create(zipEntry, zipFile, /* pathNamesToSkip= */ 1, /* shouldCompress= */ true);
  }

  /** Constructs a {@link ModuleEntry} for {@link ZipEntry} contained in a module zip file. */
  public static ModuleZipEntry fromModuleZipEntry(ZipEntry zipEntry, ZipFile zipFile) {
    return create(zipEntry, zipFile, /* pathNamesToSkip= */ 0, /* shouldCompress= */ true);
  }

  private static ModuleZipEntry create(
      ZipEntry zipEntry, ZipFile zipFile, int pathNamesToSkip, boolean shouldCompress) {
    checkArgument(ZipPath.create(zipEntry.getName()).getNameCount() > pathNamesToSkip);
    return new AutoValue_ModuleZipEntry(zipEntry, zipFile, pathNamesToSkip, shouldCompress);
  }
}
