/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.zipflinger.BytesSource;
import java.io.IOException;

/**
 * Same as {@link BytesSource} but exposes the (un)compressed size to this package.
 *
 * <p>This class can be deleted once bundletool depends on the version of zipflinger that makes
 * these methods public in {@link BytesSource}.
 */
final class BytesSource2 extends BytesSource {

  public BytesSource2(byte[] bytes, String name, int compressionLevel) throws IOException {
    super(bytes, name, compressionLevel);
  }

  /**
   * Returns the entry's compressed size.
   *
   * <p>The name of the method is suffixed with "2" to avoid confusing the compiler with the method
   * with the same name in the {@link BytesSource} class.
   */
  public long getCompressedSize2() {
    return compressedSize;
  }

  /**
   * Returns the entry's uncompressed size.
   *
   * <p>The name of the method is suffixed with "2" to avoid confusing the compiler with the method
   * with the same name in the {@link BytesSource} class.
   */
  public long getUncompressedSize2() {
    return uncompressedSize;
  }
}
