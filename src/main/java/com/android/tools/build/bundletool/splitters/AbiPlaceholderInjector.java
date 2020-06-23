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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Targeting.Abi;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;

/**
 * Responsible for setting ABI placeholder libraries for modules with no native code.
 *
 * <p>This is required if the install time modules are not giving enough context for the Android
 * Platform to determine the correct ABI mode (for example: 32 or 64-bit). In situations where an
 * on-demand module only supports 32-bit mode and it solely contains the native code, the app won't
 * be able to start in the appropriate (32 bit) mode unless a placeholder native libraries are added
 * to guide the Android Platform.
 */
public class AbiPlaceholderInjector {

  private final ImmutableSet<Abi> abiPlaceholders;

  public AbiPlaceholderInjector(ImmutableSet<Abi> abiPlaceholders) {
    this.abiPlaceholders = abiPlaceholders;
  }

  /** Adds the placeholder native libraries to the {@link ModuleSplit}. */
  public ModuleSplit addPlaceholderNativeEntries(ModuleSplit moduleSplit) {
    return moduleSplit.toBuilder()
        .setEntries(
            ImmutableList.<ModuleEntry>builder()
                .addAll(moduleSplit.getEntries())
                .addAll(
                    abiPlaceholders.stream()
                        .map(AbiPlaceholderInjector::createEntryForAbi)
                        .collect(toImmutableList()))
                .build())
        .build();
  }

  private static ModuleEntry createEntryForAbi(Abi abi) {
    return ModuleEntry.builder()
        .setPath(
            BundleModule.LIB_DIRECTORY
                .resolve(AbiName.fromProto(abi.getAlias()).getPlatformName())
                .resolve("libplaceholder.so"))
        .setContent(ByteSource.wrap(new byte[0]))
        .build();
  }
}
