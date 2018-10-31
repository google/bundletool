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

import static com.android.bundle.Targeting.ScreenDensity.DensityOneofCase.DENSITY_ALIAS;
import static com.android.tools.build.bundletool.targeting.TargetingUtils.getMaxSdk;
import static com.android.tools.build.bundletool.targeting.TargetingUtils.getMinSdk;

import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import java.util.Optional;

/**
 * Configuration representing different targetings in a readable format, to be outputted by the
 * {@link GetSizeCommand}. Ex: {sdk=21-22,locale=en}.
 *
 * <p>We set only the configuration fields based on the dimensions passed to {@link GetSizeCommand}.
 */
@AutoValue
public abstract class GetSizeConfiguration {
  public abstract Optional<String> getAbi();

  public abstract Optional<String> getLocale();

  public abstract Optional<String> getScreenDensity();

  public abstract Optional<String> getSdkVersion();

  public static GetSizeConfiguration.Builder builder() {
    return new AutoValue_GetSizeConfiguration.Builder();
  }

  public static GetSizeConfiguration getDefaultInstance() {
    return GetSizeConfiguration.builder().build();
  }

  public static GetSizeConfiguration getSizeConfiguration(
      Optional<SdkVersionTargeting> sdkVersionTargeting,
      Optional<AbiTargeting> abiTargeting,
      Optional<ScreenDensityTargeting> screenDensityTargeting,
      Optional<LanguageTargeting> languageTargeting) {

    return GetSizeConfiguration.builder()
        .setAbi(abiTargeting.flatMap(GetSizeConfiguration::getAbiName))
        .setSdkVersion(sdkVersionTargeting.flatMap(GetSizeConfiguration::getSdkName))
        .setLocale(languageTargeting.flatMap(GetSizeConfiguration::getLocaleName))
        .setScreenDensity(
            screenDensityTargeting.flatMap(GetSizeConfiguration::getScreenDensityName))
        .build();
  }

  @VisibleForTesting
  static Optional<String> getAbiName(AbiTargeting abiTargeting) {
    if (abiTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        AbiName.fromProto(Iterables.getOnlyElement(abiTargeting.getValueList()).getAlias())
            .getPlatformName());
  }

  @VisibleForTesting
  static Optional<String> getSdkName(SdkVersionTargeting sdkVersionTargeting) {
    int maxSdk = getMaxSdk(sdkVersionTargeting);
    return Optional.of(
        String.format(
            "%d-%s",
            getMinSdk(sdkVersionTargeting),
            maxSdk != Integer.MAX_VALUE ? Integer.toString(maxSdk - 1) : ""));
  }

  @VisibleForTesting
  static Optional<String> getLocaleName(LanguageTargeting languageTargeting) {
    if (languageTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Iterables.getOnlyElement(languageTargeting.getValueList()));
  }

  @VisibleForTesting
  static Optional<String> getScreenDensityName(ScreenDensityTargeting screenDensityTargeting) {
    if (screenDensityTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }

    ScreenDensity screenDensity = Iterables.getOnlyElement(screenDensityTargeting.getValueList());
    return Optional.of(
        screenDensity.getDensityOneofCase() == DENSITY_ALIAS
            ? screenDensity.getDensityAlias().name()
            : Integer.toString(screenDensity.getDensityDpi()));
  }

  /** Builder for the {@link GetSizeConfiguration}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAbi(Optional<String> abiName);

    public abstract Builder setLocale(Optional<String> locale);

    public abstract Builder setScreenDensity(Optional<String> screenDensity);

    public abstract Builder setSdkVersion(Optional<String> sdkVersion);

    public abstract GetSizeConfiguration build();
  }
}
