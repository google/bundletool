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

package com.android.tools.build.bundletool.model;

import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.InputStream;

/**
 * Represents a delegate for a ModuleEntry in a an App Bundle's module.
 *
 * <p>Useful for selectively overriding certain method(s) while leaving the rest of the
 * functionality unchanged.
 */
@Immutable
public class DelegatingModuleEntry implements ModuleEntry {

  private final ModuleEntry delegate;

  public DelegatingModuleEntry(ModuleEntry delegate) {
    this.delegate = delegate;
  }

  @MustBeClosed
  @Override
  public InputStream getContent() {
    return delegate.getContent();
  }

  @Override
  public ZipPath getPath() {
    return delegate.getPath();
  }

  @Override
  public boolean isDirectory() {
    return delegate.isDirectory();
  }

  @Override
  public boolean shouldCompress() {
    return delegate.shouldCompress();
  }

  @Override
  public ModuleEntry setCompression(boolean shouldCompress) {
    return delegate.setCompression(shouldCompress);
  }
}
