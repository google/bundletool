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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** In-memory implementation of a {@link ModuleEntry}. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class InMemoryModuleEntry implements ModuleEntry {

  @Override
  public abstract ZipPath getPath();

  protected abstract ByteString getContentAsBytes();

  @Override
  public abstract boolean isDirectory();

  @Override
  public abstract boolean shouldCompress();

  @Override
  public InputStream getContent() {
    return new ByteArrayInputStream(getContentAsBytes().toByteArray());
  }

  @Override
  public InMemoryModuleEntry setCompression(boolean shouldCompress) {
    if (shouldCompress == shouldCompress()) {
      return this;
    }
    return create(getPath(), getContentAsBytes(), isDirectory(), shouldCompress);
  }

  public static InMemoryModuleEntry ofFile(String path, byte[] content) {
    return ofFile(ZipPath.create(path), content);
  }

  public static InMemoryModuleEntry ofFile(ZipPath path, byte[] content) {
    return new AutoValue_InMemoryModuleEntry(
        path, ByteString.copyFrom(content), /* isDirectory= */ false, /* shouldCompress= */ true);
  }

  public static InMemoryModuleEntry ofFile(String path, byte[] content, boolean shouldCompress) {
    return new AutoValue_InMemoryModuleEntry(
        ZipPath.create(path),
        ByteString.copyFrom(content),
        /* isDirectory= */ false,
        shouldCompress);
  }

  public static InMemoryModuleEntry ofDirectory(String path) {
    return new AutoValue_InMemoryModuleEntry(
        ZipPath.create(path),
        ByteString.copyFrom(new byte[0]),
        /* isDirectory= */ true,
        /* shouldCompress= */ true);
  }

  private static InMemoryModuleEntry create(
      ZipPath zipPath, ByteString content, boolean isDirectory, boolean shouldCompress) {
    return new AutoValue_InMemoryModuleEntry(zipPath, content, isDirectory, shouldCompress);
  }
}
