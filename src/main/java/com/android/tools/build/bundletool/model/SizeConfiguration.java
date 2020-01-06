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
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMaxSdk;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;

import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * Configuration representing different targetings in a readable format. Ex: {sdk=21-22,locale=en}.
 *
 * <p>We set only the configuration fields based on provided dimensions.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class SizeConfiguration {
  public abstract Optional<String> getAbi();

  public abstract Optional<String> getLocale();

  public abstract Optional<String> getScreenDensity();

  public abstract Optional<String> getSdkVersion();

  public abstract Builder toBuilder();

  public static SizeConfiguration.Builder builder() {
    return new AutoValue_SizeConfiguration.Builder();
  }

  public static SizeConfiguration getDefaultInstance() {
    return SizeConfiguration.builder().build();
  }

  public static Optional<String> getAbiName(AbiTargeting abiTargeting) {
    if (abiTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        AbiName.fromProto(Iterables.getOnlyElement(abiTargeting.getValueList()).getAlias())
            .getPlatformName());
  }

  public static Optional<String> getSdkName(SdkVersionTargeting sdkVersionTargeting) {
    int maxSdk = getMaxSdk(sdkVersionTargeting);
    return Optional.of(
        String.format(
            "%d-%s",
            getMinSdk(sdkVersionTargeting),
            maxSdk != Integer.MAX_VALUE ? Integer.toString(maxSdk - 1) : ""));
  }

  public static Optional<String> getLocaleName(LanguageTargeting languageTargeting) {
    if (languageTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Iterables.getOnlyElement(languageTargeting.getValueList()));
  }

  public static Optional<String> getScreenDensityName(
      ScreenDensityTargeting screenDensityTargeting) {
    if (screenDensityTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }

    ScreenDensity screenDensity = Iterables.getOnlyElement(screenDensityTargeting.getValueList());
    return Optional.of(
        screenDensity.getDensityOneofCase().equals(DENSITY_ALIAS)
            ? screenDensity.getDensityAlias().name()
            : Integer.toString(screenDensity.getDensityDpi()));
  }

  /** Builder for the {@link SizeConfiguration}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAbi(String abiName);

    public abstract Builder setLocale(String locale);

    public abstract Builder setScreenDensity(String screenDensity);

    public abstract Builder setSdkVersion(String sdkVersion);

    public abstract SizeConfiguration build();
  }
}
