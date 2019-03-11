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

import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * It ensures that no two generated APKs will have the same split ID by applying a different suffix
 * to them.
 *
 * <p>The splits need to belong the same module.
 */
@ThreadSafe
public final class SuffixManager {
  @GuardedBy("this")
  private final Multimap<VariantTargeting, String> usedSuffixes = HashMultimap.create();

  public synchronized String createSuffix(ModuleSplit moduleSplit) {
    String currentProposal = moduleSplit.getSuffix();
    int serialNumber = 1;
    while (usedSuffixes.containsEntry(moduleSplit.getVariantTargeting(), currentProposal)) {
      serialNumber++;
      currentProposal = String.format("%s_%d", moduleSplit.getSuffix(), serialNumber);
    }
    usedSuffixes.put(moduleSplit.getVariantTargeting(), currentProposal);
    return currentProposal;
  }
}
