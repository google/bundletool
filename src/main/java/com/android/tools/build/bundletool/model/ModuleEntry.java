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

import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Represents an entry in an App Bundle's module.
 *
 * <p>All subclasses should be immutable, and we assume that they are as long as the data source
 * backing this entry remains unchanged.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ModuleEntry {

  /** Returns the content of the entry as a stream of bytes. */
  @MustBeClosed
  public final InputStream getContent() {
    try {
      return getContentSupplier().get();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Path of the entry inside the module. */
  public abstract ZipPath getPath();

  /** Returns whether entry should be compressed in generated archives. */
  public abstract boolean getShouldCompress();

  /** Returns data source for this entry. */
  public abstract InputStreamSupplier getContentSupplier();

  /** Checks whether the given entries are identical. */
  @Override
  public final boolean equals(Object obj2) {
    if (!(obj2 instanceof ModuleEntry)) {
      return false;
    }

    ModuleEntry entry1 = this;
    ModuleEntry entry2 = (ModuleEntry) obj2;

    if (entry1 == entry2) {
      return true;
    }

    if (!entry1.getPath().equals(entry2.getPath())) {
      return false;
    }

    if (entry1.getShouldCompress() != entry2.getShouldCompress()) {
      return false;
    }

    try (InputStream inputStream1 = entry1.getContent();
        InputStream inputStream2 = entry2.getContent()) {
      return FileUtils.equalContent(inputStream1, inputStream2);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Failed to compare contents of module entries '%s' and '%s'.", entry1, entry2),
          e);
    }
  }

  @Override
  public final int hashCode() {
    // Deliberately omit the content for performance.
    return Objects.hash(getPath(), getShouldCompress());
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_ModuleEntry.Builder().setShouldCompress(true);
  }

  /** Builder for {@code ModuleEntry}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPath(ZipPath path);

    public abstract Builder setShouldCompress(boolean shouldCompress);

    /**
     * Sets the data source for this entry.
     *
     * <p>Strongly consider a {@link java.io.BufferedInputStream} for efficiency.
     *
     * @see InputStreamSuppliers
     */
    public abstract Builder setContentSupplier(InputStreamSupplier contentSupplier);

    public abstract ModuleEntry build();
  }
}
