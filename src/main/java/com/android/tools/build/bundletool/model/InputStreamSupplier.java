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

import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * A repeatable action returning fresh instances of {@link InputStream}.
 *
 * <p>To be used to access various types of data, such as files on disk, zip file entries or raw
 * byte data.
 *
 * <p>All subclasses must be immutable, and return the same {@link InputStream} at each invocation.
 *
 * @deprecated All users should move over to use {@link ByteSource} exclusively.
 */
@Immutable
@Deprecated
public final class InputStreamSupplier {

  private final SupplierWithIO<InputStream> inputSupplier;
  private final SupplierWithIO<Long> sizeSupplier;

  public InputStreamSupplier(
      SupplierWithIO<InputStream> inputSupplier, SupplierWithIO<Long> sizeSupplier) {
    this.inputSupplier = inputSupplier;
    this.sizeSupplier = sizeSupplier;
  }

  public InputStreamSupplier(SupplierWithIO<InputStream> inputSupplier, long size) {
    this(inputSupplier, () -> size);
  }

  public static InputStreamSupplier fromByteSource(ByteSource content) {
    return new InputStreamSupplier(content::openStream, content::size);
  }

  @MustBeClosed
  public InputStream get() throws IOException {
    return inputSupplier.get();
  }

  public long sizeBytes() throws IOException {
    return sizeSupplier.get();
  }

  public ByteSource asByteSource() {
    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return inputSupplier.get();
      }

      @Override
      public Optional<Long> sizeIfKnown() {
        try {
          return Optional.of(sizeSupplier.get());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };
  }

  /** A version of {@code Supplier} that can throw an IOException. */
  @Immutable
  public interface SupplierWithIO<T> {
    T get() throws IOException;
  }
}
