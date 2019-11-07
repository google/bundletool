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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Sanitizer;
import com.android.bundle.Targeting.Sanitizer.SanitizerAlias;
import com.android.bundle.Targeting.SanitizerTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Splits the native libraries in the module by sanitizer. */
public class SanitizerNativeLibrariesSplitter implements ModuleSplitSplitter {

  /** Generates {@link ModuleSplit} objects dividing the native libraries by sanitizer. */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    if (!moduleSplit.getNativeConfig().isPresent()) {
      return ImmutableList.of(moduleSplit);
    }

    Set<ZipPath> hwasanDirs = new HashSet<>();
    for (TargetedNativeDirectory dir : moduleSplit.getNativeConfig().get().getDirectoryList()) {
      if (!dir.getTargeting().hasSanitizer()) {
        continue;
      }
      if (dir.getTargeting().getSanitizer().getAlias().equals(SanitizerAlias.HWADDRESS)) {
        hwasanDirs.add(ZipPath.create(dir.getPath()));
      }
    }

    List<ModuleEntry> hwasanEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> hwasanDirs.contains(entry.getPath().subpath(0, 2)))
            .collect(toImmutableList());
    if (hwasanEntries.isEmpty()) {
      return ImmutableList.of(moduleSplit);
    }

    List<ModuleEntry> nonHwasanEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> !hwasanDirs.contains(entry.getPath().subpath(0, 2)))
            .collect(toImmutableList());

    ModuleSplit hwasanSplit =
        moduleSplit.toBuilder()
            .setApkTargeting(
                moduleSplit.getApkTargeting().toBuilder()
                    .setSanitizerTargeting(
                        SanitizerTargeting.newBuilder()
                            .addValue(Sanitizer.newBuilder().setAlias(SanitizerAlias.HWADDRESS)))
                    .build())
            .setMasterSplit(false)
            .setEntries(hwasanEntries)
            .build();

    ModuleSplit nonHwasanSplit = moduleSplit.toBuilder().setEntries(nonHwasanEntries).build();

    return ImmutableList.of(hwasanSplit, nonHwasanSplit);
  }
}
