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

import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ModuleEntry} which points to an entry inside a {@link ZipFile}.
 *
 * <p>It is the responsibility of the caller to ensure that the referenced {@link ZipFile} stays
 * opened for the lifetime of the {@link ModuleZipEntry} instance.
 */
@AutoValue
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

  @Override
  public InputStream getContent() {
    try {
      return BufferedIo.inputStream(getZipFile(), getZipEntry());
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Error while reading the zip entry '%s'.", getZipEntry().getName())
          .build();
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

  /** Constructs a {@link ModuleEntry} for {@link ZipEntry} contained in a bundle zip file. */
  public static ModuleZipEntry fromBundleZipEntry(ZipEntry zipEntry, ZipFile zipFile) {
    return create(zipEntry, zipFile, /* pathNamesToSkip= */ 1);
  }

  /** Constructs a {@link ModuleEntry} for {@link ZipEntry} contained in a module zip file. */
  public static ModuleZipEntry fromModuleZipEntry(ZipEntry zipEntry, ZipFile zipFile) {
    return create(zipEntry, zipFile, /* pathNamesToSkip= */ 0);
  }

  private static ModuleZipEntry create(ZipEntry zipEntry, ZipFile zipFile, int pathNamesToSkip) {
    checkArgument(ZipPath.create(zipEntry.getName()).getNameCount() > pathNamesToSkip);
    return new AutoValue_ModuleZipEntry(zipEntry, zipFile, pathNamesToSkip);
  }
}
