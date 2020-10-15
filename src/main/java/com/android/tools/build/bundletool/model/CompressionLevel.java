/*
 * Copyright (C) 2020 The Android Open Source Project
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

/** Zip compression level. */
public enum CompressionLevel {
  /**
   * Entry has the same compression as the entry of the zip file it comes from.
   *
   * <p>Only to be used when copying an entry from one zip to another.
   */
  SAME_AS_SOURCE(-2),

  /** Entry is uncompressed. */
  NO_COMPRESSION(0),

  /** Entry is compressed with Deflate 6. */
  DEFAULT_COMPRESSION(6),

  /** Entry is compressed with Deflate 9. */
  BEST_COMPRESSION(9);

  private final short value;

  CompressionLevel(int value) {
    checkArgument((short) value == value);
    this.value = (short) value;
  }

  public short getValue() {
    return value;
  }

  /**
   * Returns whether the compression level indicates a compression.
   *
   * <p>Note that in the case of {@link SAME_AS_SOURCE}, this method returns {@code false}.
   */
  public boolean isCompressed() {
    return value > 0;
  }
}
