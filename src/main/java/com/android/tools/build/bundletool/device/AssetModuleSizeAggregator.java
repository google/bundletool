/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.device;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;

/**
 * Get total size (min and max) for a list of asset modules, for each {@link SizeConfiguration}
 * based on the requested dimensions passed to {@link GetSizeCommand}.
 *
 * <p>Asset module slices are filtered based on the given {@link VariantTargeting} and {@link
 * DeviceSpec}.
 */
public class AssetModuleSizeAggregator extends AbstractSizeAggregator {

  private final Collection<AssetSliceSet> assetModules;
  private final VariantTargeting variantTargeting;

  public AssetModuleSizeAggregator(
      Collection<AssetSliceSet> assetModules,
      VariantTargeting variantTargeting,
      ImmutableMap<String, Long> sizeByApkPaths,
      GetSizeRequest getSizeRequest) {
    super(sizeByApkPaths, getSizeRequest);
    this.assetModules = assetModules;
    this.variantTargeting = variantTargeting;
  }

  @Override
  public ConfigurationSizes getSize() {

    ImmutableList<ApkDescription> apkDescriptions =
        assetModules.stream()
            .flatMap(assetModule -> assetModule.getApkDescriptionList().stream())
            .collect(toImmutableList());

    ImmutableSet<SdkVersionTargeting> sdkVersionTargetingOptions =
        variantTargeting.hasSdkVersionTargeting()
            ? ImmutableSet.of(variantTargeting.getSdkVersionTargeting())
            : getAllSdkVersionTargetings(apkDescriptions);
    ImmutableSet<AbiTargeting> abiTargetingOptions =
        variantTargeting.hasAbiTargeting()
            ? ImmutableSet.of(variantTargeting.getAbiTargeting())
            : getAllAbiTargetings(apkDescriptions);
    ImmutableSet<LanguageTargeting> languageTargetingOptions =
        getAllLanguageTargetings(apkDescriptions);
    ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions =
        variantTargeting.hasScreenDensityTargeting()
            ? ImmutableSet.of(variantTargeting.getScreenDensityTargeting())
            : getAllScreenDensityTargetings(apkDescriptions);

    return getSizesPerConfiguration(
        sdkVersionTargetingOptions,
        abiTargetingOptions,
        languageTargetingOptions,
        screenDensityTargetingOptions);
  }

  @Override
  protected ImmutableList<ZipPath> getMatchingApks(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting) {
    return new ApkMatcher(
            getDeviceSpec(
                getSizeRequest.getDeviceSpec(),
                sdkVersionTargeting,
                abiTargeting,
                screenDensityTargeting,
                languageTargeting),
            getSizeRequest.getModules(),
            getSizeRequest.getInstant())
        .getMatchingApksFromAssetModules(assetModules);
  }
}
