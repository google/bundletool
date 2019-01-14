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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS64;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMultiAbiTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingComparatorsTest {

  // Realistic sets of targeting architectures.
  private static final ImmutableList<ImmutableSet<AbiAlias>> REALISTIC_TARGETING_LIST =
      ImmutableList.of(
          ImmutableSet.of(ARMEABI_V7A),
          ImmutableSet.of(ARM64_V8A),
          ImmutableSet.of(ARM64_V8A, ARMEABI_V7A),
          ImmutableSet.of(X86),
          ImmutableSet.of(X86, ARMEABI_V7A),
          ImmutableSet.of(X86_64),
          ImmutableSet.of(X86_64, ARMEABI_V7A),
          ImmutableSet.of(X86_64, ARM64_V8A, ARMEABI_V7A),
          ImmutableSet.of(X86_64, X86),
          ImmutableSet.of(X86_64, X86, ARMEABI_V7A),
          ImmutableSet.of(X86_64, X86, ARM64_V8A, ARMEABI_V7A));

  // All different combinations of three architectures.
  private static final ImmutableList<ImmutableSet<AbiAlias>> EXHAUSTIVE_TARGETING_LIST =
      ImmutableList.of(
          ImmutableSet.of(ARMEABI),
          ImmutableSet.of(X86_64),
          ImmutableSet.of(X86_64, ARMEABI),
          ImmutableSet.of(MIPS64),
          ImmutableSet.of(MIPS64, ARMEABI),
          ImmutableSet.of(MIPS64, X86_64),
          ImmutableSet.of(MIPS64, X86_64, ARMEABI));

  @Test
  public void testMultiAbiAliasComparator_exhaustiveTargetingList() {
    assertThat(
            Comparators.isInStrictOrder(
                EXHAUSTIVE_TARGETING_LIST, TargetingComparators.MULTI_ABI_ALIAS_COMPARATOR))
        .isTrue();
  }

  @Test
  public void testMultiAbiAliasComparator_realisticTargetingList() {
    assertThat(
            Comparators.isInStrictOrder(
                REALISTIC_TARGETING_LIST, TargetingComparators.MULTI_ABI_ALIAS_COMPARATOR))
        .isTrue();
  }

  @Test
  public void testMultiAbiComparator_exhaustiveTargetingList() {
    ImmutableList<VariantTargeting> exhaustiveTargetingVariants =
        EXHAUSTIVE_TARGETING_LIST.stream()
            .map(TargetingComparatorsTest::fromAbiSet)
            .collect(toImmutableList());

    assertThat(
            Comparators.isInStrictOrder(
                exhaustiveTargetingVariants, TargetingComparators.MULTI_ABI_COMPARATOR))
        .isTrue();
  }

  @Test
  public void testMultiAbiComparator_realisticTargetingList() {
    ImmutableList<VariantTargeting> realisticTargetingVariants =
        REALISTIC_TARGETING_LIST.stream()
            .map(TargetingComparatorsTest::fromAbiSet)
            .collect(toImmutableList());

    assertThat(
            Comparators.isInStrictOrder(
                realisticTargetingVariants, TargetingComparators.MULTI_ABI_COMPARATOR))
        .isTrue();
  }

  private static VariantTargeting fromAbiSet(ImmutableSet<AbiAlias> abis) {
    return variantMultiAbiTargeting(ImmutableSet.of(abis), ImmutableSet.of());
  }
}
