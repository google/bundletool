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

import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.ARCHIVE;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.INSTANT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SPLIT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.STANDALONE;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SYSTEM;
import static com.android.tools.build.bundletool.model.targeting.TargetingComparators.VARIANT_TARGETING_COMPARATOR;
import static java.util.Comparator.comparing;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.Immutable;

/**
 * Key identifying a variant.
 *
 * <p>A variant is a set of APKs. One device is guaranteed to receive only APKs from the same
 * variant.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class VariantKey implements Comparable<VariantKey> {
  public static VariantKey create(ModuleSplit moduleSplit) {
    return new AutoValue_VariantKey(moduleSplit.getSplitType(), moduleSplit.getVariantTargeting());
  }

  public abstract SplitType getSplitType();

  public abstract VariantTargeting getVariantTargeting();

  @Override
  public int compareTo(VariantKey o) {
    // Instant APKs get the lowest variant numbers followed by standalone and then split APKs.
    // System APKs never occur with other apk types, its ordering position doesn't matter.
    return comparing(
            VariantKey::getSplitType,
            Ordering.explicit(INSTANT, STANDALONE, SPLIT, ARCHIVE, SYSTEM))
        .thenComparing(VariantKey::getVariantTargeting, VARIANT_TARGETING_COMPARATOR)
        .compare(this, o);
  }
}
