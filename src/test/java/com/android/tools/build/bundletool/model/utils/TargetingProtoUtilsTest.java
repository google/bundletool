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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingProtoUtilsTest {

  @Test
  public void toAlternativeTargeting_language() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                assetsDirectoryTargeting(languageTargeting("en"))))
        .isEqualTo(assetsDirectoryTargeting(alternativeLanguageTargeting("en")));
  }

  @Test
  public void toAlternativeTargeting_textureCompressionFormat() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ATC))))
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(
                    ImmutableSet.of(), ImmutableSet.of(TextureCompressionFormatAlias.ATC))));
  }

  @Test
  public void toAlternativeTargeting_abi() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                assetsDirectoryTargeting(abiTargeting(AbiAlias.ARM64_V8A))))
        .isEqualTo(
            assetsDirectoryTargeting(
                abiTargeting(ImmutableSet.of(), ImmutableSet.of(AbiAlias.ARM64_V8A))));
  }

  @Test
  public void toAlternativeTargeting_deviceTier() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                assetsDirectoryTargeting(deviceTierTargeting(1))))
        .isEqualTo(assetsDirectoryTargeting(alternativeDeviceTierTargeting(ImmutableList.of(1))));
  }

  @Test
  public void toAlternativeTargeting_countrySet() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                assetsDirectoryTargeting(countrySetTargeting("latam"))))
        .isEqualTo(
            assetsDirectoryTargeting(alternativeCountrySetTargeting(ImmutableList.of("latam"))));
  }

  @Test
  public void toAlternativeTargeting_multipleDimensionsAndValues() {
    assertThat(
            TargetingProtoUtils.toAlternativeTargeting(
                mergeAssetsTargeting(
                    assetsDirectoryTargeting(
                        textureCompressionTargeting(TextureCompressionFormatAlias.ATC)),
                    assetsDirectoryTargeting(
                        abiTargeting(
                            ImmutableSet.of(AbiAlias.ARM64_V8A, AbiAlias.ARMEABI_V7A),
                            ImmutableSet.of())))))
        .isEqualTo(
            mergeAssetsTargeting(
                assetsDirectoryTargeting(
                    textureCompressionTargeting(
                        ImmutableSet.of(), ImmutableSet.of(TextureCompressionFormatAlias.ATC))),
                assetsDirectoryTargeting(
                    abiTargeting(
                        ImmutableSet.of(),
                        ImmutableSet.of(AbiAlias.ARM64_V8A, AbiAlias.ARMEABI_V7A)))));
  }
}
