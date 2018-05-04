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

package com.android.tools.build.bundletool.targeting;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/** Utility functions for Targeting proto. */
public final class TargetingUtils {

  /** Moves targeting values to the alternatives. */
  public static AssetsDirectoryTargeting toAlternativeTargeting(
      AssetsDirectoryTargeting targeting) {
    AssetsDirectoryTargeting.Builder alternativeTargeting = AssetsDirectoryTargeting.newBuilder();
    if (targeting.hasTextureCompressionFormat()) {
      alternativeTargeting
          .getTextureCompressionFormatBuilder()
          .addAllAlternatives(targeting.getTextureCompressionFormat().getValueList());
    }
    if (targeting.hasGraphicsApi()) {
      alternativeTargeting
          .getGraphicsApiBuilder()
          .addAllAlternatives(targeting.getGraphicsApi().getValueList());
    }
    if (targeting.hasAbi()) {
      alternativeTargeting.getAbiBuilder().addAllAlternatives(targeting.getAbi().getValueList());
    }
    if (targeting.hasLanguage()) {
      alternativeTargeting
          .getLanguageBuilder()
          .addAllAlternatives(targeting.getLanguage().getValueList());
    }
    return alternativeTargeting.build();
  }

  /** Returns the targeting dimensions of the targeting proto. */
  public static ImmutableList<TargetingDimension> getTargetingDimensions(
      AssetsDirectoryTargeting targeting) {
    ImmutableList.Builder<TargetingDimension> dimensions = new ImmutableList.Builder<>();
    if (targeting.hasAbi()) {
      dimensions.add(TargetingDimension.ABI);
    }
    if (targeting.hasGraphicsApi()) {
      dimensions.add(TargetingDimension.GRAPHICS_API);
    }
    if (targeting.hasTextureCompressionFormat()) {
      dimensions.add(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    }
    if (targeting.hasLanguage()) {
      dimensions.add(TargetingDimension.LANGUAGE);
    }
    return dimensions.build();
  }

  /** Extracts ABI values from the targeting. */
  public static ImmutableSet<Abi> abiValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getAbiTargeting().getValueList());
  }

  /** Extracts ABI alternatives from the targeting. */
  public static ImmutableSet<Abi> abiAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getAbiTargeting().getAlternativesList());
  }

  /** Extracts targeted ABI universe (values and alternatives) from the targeting. */
  public static ImmutableSet<Abi> abiUniverse(ApkTargeting targeting) {
    return ImmutableSet.<Abi>builder()
        .addAll(abiValues(targeting))
        .addAll(abiAlternatives(targeting))
        .build();
  }

  /** Extracts screen density values from the targeting. */
  public static ImmutableSet<ScreenDensity> densityValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getScreenDensityTargeting().getValueList());
  }

  /** Extracts screen density alternatives from the targeting. */
  public static ImmutableSet<ScreenDensity> densityAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getScreenDensityTargeting().getAlternativesList());
  }

  /** Extracts targeted screen density universe (values and alternatives) from the targeting. */
  public static ImmutableSet<ScreenDensity> densityUniverse(ApkTargeting targeting) {
    return ImmutableSet.<ScreenDensity>builder()
        .addAll(densityValues(targeting))
        .addAll(densityAlternatives(targeting))
        .build();
  }
}
