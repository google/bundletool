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

package com.android.tools.build.bundletool.testing;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApexImageTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingUtilsTest {

  private static final MultiAbiTargeting SINGLE_ABI_NO_ALTERNATIVES =
      MultiAbiTargeting.newBuilder().addValue(multiAbi(X86)).build();
  private static final MultiAbiTargeting SINGLE_ABI_WITH_ALTERNATIVES =
      MultiAbiTargeting.newBuilder()
          .addValue(multiAbi(X86))
          .addAlternatives(multiAbi(ARMEABI_V7A))
          .addAlternatives(multiAbi(ARM64_V8A))
          .build();
  private static final MultiAbiTargeting MULTI_ABI_NO_ALTERNATIVES =
      MultiAbiTargeting.newBuilder().addValue(multiAbi(ARMEABI_V7A, ARM64_V8A)).build();
  private static final MultiAbiTargeting MULTI_ABI_WITH_ALTERNATIVES =
      MultiAbiTargeting.newBuilder()
          .addValue(multiAbi(X86))
          .addValue(multiAbi(ARMEABI_V7A, ARM64_V8A))
          .addAlternatives(multiAbi(X86_64))
          .build();

  @Test
  public void toAbiAlias_validAbi_succeeds() {
    assertThat(TargetingUtils.toAbiAlias("x86")).isEqualTo(X86);
  }

  @Test
  public void toAbiAlias_unknownAbi_throws() {
    Exception e =
        assertThrows(IllegalArgumentException.class, () -> TargetingUtils.toAbiAlias("sparc"));

    assertThat(e).hasMessageThat().contains("Unrecognized ABI");
  }

  @Test
  public void toAbi_validAbi_succeeds() {
    Abi expectedAbi = Abi.newBuilder().setAlias(X86).build();

    assertThat(TargetingUtils.toAbi("x86")).isEqualTo(expectedAbi);
  }

  @Test
  public void toAbi_unknownAbi_throws() {
    Exception e = assertThrows(IllegalArgumentException.class, () -> TargetingUtils.toAbi("sparc"));

    assertThat(e).hasMessageThat().contains("Unrecognized ABI");
  }

  @Test
  public void multiAbiTargeting_singleAbiNoAlternatives() {
    assertThat(TargetingUtils.multiAbiTargeting(X86)).isEqualTo(SINGLE_ABI_NO_ALTERNATIVES);
  }

  @Test
  public void multiAbiTargeting_singleAbiAndAlternatives() {
    assertThat(TargetingUtils.multiAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(SINGLE_ABI_WITH_ALTERNATIVES);
  }

  @Test
  public void multiAbiTargeting_multipleAbisNoAlternatives() {
    ImmutableSet<ImmutableSet<AbiAlias>> abiAliases =
        ImmutableSet.of(ImmutableSet.of(ARMEABI_V7A, ARM64_V8A));

    assertThat(TargetingUtils.multiAbiTargeting(abiAliases))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(MULTI_ABI_NO_ALTERNATIVES);
  }

  @Test
  public void multiAbiTargeting_multipleAbisAndAlternatives() {
    ImmutableSet<ImmutableSet<AbiAlias>> abiAliases =
        ImmutableSet.of(ImmutableSet.of(X86), ImmutableSet.of(ARMEABI_V7A, ARM64_V8A));
    ImmutableSet<ImmutableSet<AbiAlias>> alternatives = ImmutableSet.of(ImmutableSet.of(X86_64));

    assertThat(TargetingUtils.multiAbiTargeting(abiAliases, alternatives))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(MULTI_ABI_WITH_ALTERNATIVES);
  }

  @Test
  public void apexImages() {
    TargetedApexImage firstTargeting = TargetedApexImage.newBuilder().setPath("path1").build();
    TargetedApexImage secondTargeting = TargetedApexImage.newBuilder().setPath("path2").build();

    ApexImages apexImages = TargetingUtils.apexImages(firstTargeting, secondTargeting);

    assertThat(apexImages.getImageList()).containsExactly(firstTargeting, secondTargeting);
  }

  @Test
  public void targetedApexImage() {
    ApexImageTargeting targeting =
        ApexImageTargeting.newBuilder().setMultiAbi(MULTI_ABI_WITH_ALTERNATIVES).build();

    TargetedApexImage apexImage = TargetingUtils.targetedApexImage("path", targeting);

    assertThat(apexImage.getPath()).isEqualTo("path");
    assertThat(apexImage.getTargeting()).ignoringRepeatedFieldOrder().isEqualTo(targeting);
  }

  @Test
  public void apexImageTargeting() {
    ApexImageTargeting expected =
        ApexImageTargeting.newBuilder().setMultiAbi(MULTI_ABI_NO_ALTERNATIVES).build();

    assertThat(TargetingUtils.apexImageTargeting("armeabi-v7a", "arm64-v8a")).isEqualTo(expected);
  }

  @Test
  public void apkMultiAbiTargeting_byAbiAlias() {
    ApkTargeting expectedTargeting =
        ApkTargeting.newBuilder().setMultiAbiTargeting(SINGLE_ABI_NO_ALTERNATIVES).build();

    assertThat(TargetingUtils.apkMultiAbiTargeting(X86)).isEqualTo(expectedTargeting);
  }

  @Test
  public void apkMultiAbiTargeting_byMultiAbiTargeting() {
    ApkTargeting expectedTargeting =
        ApkTargeting.newBuilder().setMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES).build();

    assertThat(TargetingUtils.apkMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void apkMultiAbiTargeting_byAbiAliasAndAlternativesSet() {
    ApkTargeting expectedTargeting =
        ApkTargeting.newBuilder().setMultiAbiTargeting(SINGLE_ABI_WITH_ALTERNATIVES).build();

    assertThat(
            TargetingUtils.apkMultiAbiTargeting(
                AbiAlias.X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void apkMultiAbiTargeting_byMultipleAbisAndAlternatives() {
    ApkTargeting expectedTargeting =
        ApkTargeting.newBuilder().setMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES).build();

    assertThat(
            TargetingUtils.apkMultiAbiTargeting(
                ImmutableSet.of(ImmutableSet.of(X86), ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)),
                ImmutableSet.of(ImmutableSet.of(X86_64))))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void apkMultiAbiTargetingFromAllTergeting() {
    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(
            ImmutableSet.of(ARMEABI_V7A), ImmutableSet.of(ARM64_V8A), ImmutableSet.of(X86));

    ApkTargeting expectedTargeting =
        ApkTargeting.newBuilder().setMultiAbiTargeting(SINGLE_ABI_WITH_ALTERNATIVES).build();
    assertThat(
            TargetingUtils.apkMultiAbiTargetingFromAllTargeting(ImmutableSet.of(X86), allTargeting))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void variantMultiAbiTargeting_byMultiAbiTargeting() {
    VariantTargeting expectedTargeting =
        VariantTargeting.newBuilder().setMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES).build();

    assertThat(TargetingUtils.variantMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void variantMultiAbiTargeting_byAbiAliasAndAlternativesSet() {
    VariantTargeting expectedTargeting =
        VariantTargeting.newBuilder().setMultiAbiTargeting(SINGLE_ABI_WITH_ALTERNATIVES).build();

    assertThat(
            TargetingUtils.variantMultiAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)))
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void variantMultiAbiTargeting_byMultipleAbisAndAlternatives() {
    VariantTargeting expectedTargeting =
        VariantTargeting.newBuilder().setMultiAbiTargeting(MULTI_ABI_WITH_ALTERNATIVES).build();

    assertThat(
            TargetingUtils.variantMultiAbiTargeting(
                ImmutableSet.of(ImmutableSet.of(X86), ImmutableSet.of(ARMEABI_V7A, ARM64_V8A)),
                ImmutableSet.of(ImmutableSet.of(X86_64))))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  @Test
  public void variantMultiAbiTargetingFromAllTergeting() {
    ImmutableSet<ImmutableSet<AbiAlias>> allTargeting =
        ImmutableSet.of(
            ImmutableSet.of(ARMEABI_V7A), ImmutableSet.of(ARM64_V8A), ImmutableSet.of(X86));

    VariantTargeting expectedTargeting =
        VariantTargeting.newBuilder().setMultiAbiTargeting(SINGLE_ABI_WITH_ALTERNATIVES).build();
    assertThat(
            TargetingUtils.variantMultiAbiTargetingFromAllTargeting(
                ImmutableSet.of(X86), allTargeting))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTargeting);
  }

  private static MultiAbi multiAbi(AbiAlias... aliases) {
    return MultiAbi.newBuilder()
        .addAllAbi(
            Arrays.stream(aliases)
                .map(alias -> Abi.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .build();
  }
}
