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

package com.android.tools.build.bundletool.splitters;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.LanguageTargeting;

/**
 * Splits assets by language.
 *
 * <p>See {@link AssetsDimensionSplitterFactory} for details of the implementation.
 */
public final class LanguageAssetsSplitter {

  /** Creates a {@link ModuleSplitSplitter} capable of splitting assets on the language. */
  public static ModuleSplitSplitter create() {
    return AssetsDimensionSplitterFactory.createSplitter(
        AssetsDirectoryTargeting::getLanguage,
        LanguageAssetsSplitter::fromLanguage,
        ApkTargeting::hasLanguageTargeting);
  }

  private static ApkTargeting fromLanguage(LanguageTargeting targeting) {
    return ApkTargeting.newBuilder().setLanguageTargeting(targeting).build();
  }

  // Do not instantiate.
  private LanguageAssetsSplitter() {}
}
