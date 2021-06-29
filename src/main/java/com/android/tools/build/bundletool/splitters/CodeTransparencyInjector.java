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
package com.android.tools.build.bundletool.splitters;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;

/**
 * Copies the signed code transparency file
 * (BUNDLE-METADATA/com.android.tools.build.bundletool/code_transparency_signed.jwt) to the base
 * split of the main module, as well as standalone splits.
 */
public final class CodeTransparencyInjector {

  private final AppBundle appBundle;

  public CodeTransparencyInjector(AppBundle appBundle) {
    this.appBundle = appBundle;
  }

  public ModuleSplit inject(ModuleSplit split) {
    ModuleSplit.Builder splitBuilder = split.toBuilder();
    if (shouldPropagateTransparency(split)) {
      appBundle
          .getBundleMetadata()
          .getModuleEntryForSignedTransparencyFile()
          .ifPresent(splitBuilder::addEntry);
    }
    return splitBuilder.build();
  }

  private boolean shouldPropagateTransparency(ModuleSplit split) {
    if (split.getSplitType() == SplitType.STANDALONE) {
      return !appBundle.dexMergingEnabled();
    }
    return split.isMasterSplit() && split.isBaseModuleSplit();
  }
}
