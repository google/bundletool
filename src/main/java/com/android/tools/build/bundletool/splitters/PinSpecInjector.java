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
package com.android.tools.build.bundletool.splitters;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * This copies the pin spec file (base/assets/com.android.hints.pins.txt), to each split. The pin
 * spec file is then read by ApkSigner to generate the pin list file (pinlist.meta).
 */
public class PinSpecInjector {
  /** The pin spec path in the bundle */
  public static final ZipPath PIN_SPEC_NAME = ZipPath.create("assets/com.android.hints.pins.txt");

  private final Optional<ModuleEntry> pinSpec;

  public PinSpecInjector(BundleModule module) {
    this.pinSpec = module.findEntries((p) -> p.endsWith(PIN_SPEC_NAME)).findFirst();
  }

  public ModuleSplit inject(ModuleSplit split) {
    if (!pinSpec.isPresent() || split.getEntries().contains(pinSpec.get())) {
      return split;
    }

    return split.toBuilder()
        .setEntries(
            ImmutableList.<ModuleEntry>builder()
                .addAll(split.getEntries())
                .add(pinSpec.get())
                .build())
        .build();
  }
}
