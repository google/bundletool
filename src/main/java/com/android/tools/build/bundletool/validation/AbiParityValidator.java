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

package com.android.tools.build.bundletool.validation;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectorySegment;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** Validates that all modules that contain some native libraries support the same set of ABIs. */
public class AbiParityValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    BundleModule referentialModule = null;
    Set<AbiAlias> referentialAbis = null;
    for (BundleModule module : modules) {
      ImmutableSet<AbiAlias> moduleAbis = getSupportedAbis(module);

      if (moduleAbis.isEmpty()) {
        continue;
      }

      if (referentialAbis == null) {
        referentialModule = module;
        referentialAbis = moduleAbis;
      } else if (!referentialAbis.equals(moduleAbis)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "All modules with native libraries must support the same set of ABIs, but"
                    + " module '%s' supports '%s' and module '%s' supports '%s'.",
                referentialModule.getName(), referentialAbis, module.getName(), moduleAbis)
            .build();
      }
    }
  }

  private static ImmutableSet<AbiAlias> getSupportedAbis(BundleModule module) {
    return module
        .findEntriesUnderPath(BundleModule.LIB_DIRECTORY)
        // From "lib/<dir>/..." extract the "<dir>" part.
        .map(entry -> entry.getPath().getName(1).toString())
        // Extract ABI from the directory name.
        .map(TargetedDirectorySegment::parse)
        .map(TargetedDirectorySegment::getName)
        .map(subDir -> AbiName.fromLibSubDirName(subDir).get().toProto())
        .collect(toImmutableSet());
  }
}
