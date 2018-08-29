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

import static com.android.tools.build.bundletool.targeting.TargetingUtils.generateAllVariantTargetings;
import static com.android.tools.build.bundletool.targeting.TargetingUtils.matchModuleSplitWithVariants;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;

/**
 * Returns map of variants( effective sets of variants computed from module splits ) with all their
 * matched module splits and modifies the variant targeting of split to the corresponding variant.
 */
public class VariantGrouper {

  public ImmutableMultimap<VariantTargeting, ModuleSplit> groupByVariant(
      ImmutableCollection<ModuleSplit> moduleSplits) {

    ImmutableSet<VariantTargeting> variants =
        generateAllVariantTargetings(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()));

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantModuleSplitMap =
        matchModuleSplitWithVariants(variants, moduleSplits);

    return ImmutableMultimap.copyOf(
        Multimaps.transformEntries(
            variantModuleSplitMap,
            (variantTargeting, moduleSplit) ->
                moduleSplit.toBuilder().setVariantTargeting(variantTargeting).build()));
  }
}
