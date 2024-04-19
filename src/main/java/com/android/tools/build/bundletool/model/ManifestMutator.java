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

import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;

/** Represents a mutation to manifest, which can then be applied to manifest for editing it. */
@Immutable
public interface ManifestMutator extends Consumer<ManifestEditor> {

  /** Add the {@code extractNativeLibs} attribute to the manifest. */
  static ManifestMutator withExtractNativeLibs(boolean value) {
    return manifestEditor -> manifestEditor.setExtractNativeLibsValue(value);
  }

  /** Add the {@code isSplitRequired} attribute to the manifest. */
  static ManifestMutator withSplitsRequired(boolean value) {
    return manifestEditor -> manifestEditor.setSplitsRequired(value);
  }
}
