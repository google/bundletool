/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import java.util.Optional;

/**
 * Splits assets by country set.
 *
 * <p>See {@link AssetsDimensionSplitterFactory} for details of the implementation.
 */
public class CountrySetAssetsSplitter {

  /** Creates a {@link ModuleSplitSplitter} capable of splitting assets by country set. */
  public static ModuleSplitSplitter create(boolean stripTargetingSuffix) {
    return AssetsDimensionSplitterFactory.createSplitter(
        AssetsDirectoryTargeting::getCountrySet,
        CountrySetAssetsSplitter::fromCountrySet,
        ApkTargeting::hasCountrySetTargeting,
        stripTargetingSuffix ? Optional.of(TargetingDimension.COUNTRY_SET) : Optional.empty());
  }

  private static ApkTargeting fromCountrySet(CountrySetTargeting targeting) {
    return ApkTargeting.newBuilder().setCountrySetTargeting(targeting).build();
  }

  // Do not instantiate.
  private CountrySetAssetsSplitter() {}
}
