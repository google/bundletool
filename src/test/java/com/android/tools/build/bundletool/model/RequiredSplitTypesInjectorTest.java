/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_TYPES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map.Entry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RequiredSplitTypesInjectorTest {

  @Test
  public void writeSplitTypeValidationInManifest_setsRequiredSplitTypesForModule() {
    ModuleSplit baseSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setVariantTargeting(lPlusVariantTargeting())
            .setMasterSplit(true)
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .build();
    ModuleSplit featureSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("feature"))
            .setVariantTargeting(lPlusVariantTargeting())
            .setMasterSplit(true)
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .build();

    ImmutableList<BundleModuleName> requiredModules =
        ImmutableList.of(baseSplit.getModuleName(), featureSplit.getModuleName());
    ImmutableList<ModuleSplit> allSplits = ImmutableList.of(baseSplit, featureSplit);
    ImmutableList<ModuleSplit> newSplits =
        RequiredSplitTypesInjector.injectSplitTypeValidation(
            allSplits, requiredModules, /* enableSystemAttribute= */ true);

    baseSplit = newSplits.get(0);
    assertThat(getProvidedSplitTypes(baseSplit)).isEmpty();

    featureSplit = newSplits.get(1);
    assertThat(getProvidedSplitTypes(featureSplit)).containsExactly("feature__module");
  }

  @Test
  public void writeSplitTypeValidationInManifest_setsRequiredSplitTypesForTargeting() {
    ImmutableMap<ApkTargeting, String> targetingTests =
        ImmutableMap.of(
            apkAbiTargeting(AbiTargeting.getDefaultInstance()),
            "base__abi",
            apkDensityTargeting(ScreenDensityTargeting.getDefaultInstance()),
            "base__density",
            apkDeviceTierTargeting(DeviceTierTargeting.getDefaultInstance()),
            "base__tier",
            apkCountrySetTargeting(CountrySetTargeting.getDefaultInstance()),
            "base__countries",
            apkTextureTargeting(TextureCompressionFormatTargeting.getDefaultInstance()),
            "base__textures");

    for (Entry<ApkTargeting, String> entry : targetingTests.entrySet()) {
      ModuleSplit baseSplit =
          ModuleSplit.builder()
              .setModuleName(BundleModuleName.create("base"))
              .setVariantTargeting(lPlusVariantTargeting())
              .setApkTargeting(ApkTargeting.getDefaultInstance())
              .setMasterSplit(true)
              .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
              .build();
      ModuleSplit otherSplit =
          ModuleSplit.builder()
              .setModuleName(BundleModuleName.create("base"))
              .setVariantTargeting(lPlusVariantTargeting())
              .setApkTargeting(entry.getKey())
              .setMasterSplit(false)
              .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
              .build();

      ImmutableList<BundleModuleName> requiredModules = ImmutableList.of(baseSplit.getModuleName());
      ImmutableList<ModuleSplit> allSplits = ImmutableList.of(baseSplit, otherSplit);
      ImmutableList<ModuleSplit> newSplits =
          RequiredSplitTypesInjector.injectSplitTypeValidation(
              allSplits, requiredModules, /* enableSystemAttribute= */ true);

      baseSplit = newSplits.get(0);
      assertThat(getProvidedSplitTypes(baseSplit)).isEmpty();
      assertThat(getRequiredSplitTypes(baseSplit)).containsExactly(entry.getValue());

      otherSplit = newSplits.get(1);
      assertThat(getProvidedSplitTypes(otherSplit)).containsExactly(entry.getValue());
      assertThat(getRequiredSplitTypes(otherSplit)).isEmpty();
    }
  }

  private static ImmutableList<String> getRequiredSplitTypes(ModuleSplit moduleSplit) {
    String value =
        moduleSplit
            .getAndroidManifest()
            .getManifestElement()
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME)
            .orElse(
                XmlProtoAttribute.create(
                    DISTRIBUTION_NAMESPACE_URI, REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME))
            .getValueAsString();
    if (value.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(value.split(","));
  }

  private static ImmutableList<String> getProvidedSplitTypes(ModuleSplit moduleSplit) {
    String value =
        moduleSplit
            .getAndroidManifest()
            .getManifestElement()
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, SPLIT_TYPES_ATTRIBUTE_NAME)
            .get()
            .getValueAsString();
    if (value.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(value.split(","));
  }
}
