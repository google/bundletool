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

package com.android.tools.build.bundletool.device;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * A {@link TargetingDimensionMatcher} that provides matching on language.
 *
 * <p>It matches all language splits that target any language supported by the device.
 *
 * <p>Fallback language splits (ie. splits with alternative language targeting) are matched iff the
 * alternative languages don't fully cover languages of the device.
 */
public class LanguageMatcher extends TargetingDimensionMatcher<LanguageTargeting> {

  private final ImmutableSet<String> deviceLanguages;

  public LanguageMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
    this.deviceLanguages =
        deviceSpec
            .getSupportedLocalesList()
            .stream()
            .map(ResourcesUtils::convertLocaleToLanguage)
            .collect(toImmutableSet());
  }

  @Override
  protected LanguageTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getLanguageTargeting();
  }

  /** Matching languages on variants is not supported. */
  @Override
  protected LanguageTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return LanguageTargeting.getDefaultInstance();
  }

  @Override
  public boolean matchesTargeting(LanguageTargeting targetingValue) {
    if (targetingValue.equals(LanguageTargeting.getDefaultInstance())) {
      return true;
    }

    if (targetingValue.getValueCount() > 0) {
      // We return all positive matches, so we don't look at alternatives.
      Set<String> targetingLanguages = ImmutableSet.copyOf(targetingValue.getValueList());
      return !Sets.intersection(targetingLanguages, deviceLanguages).isEmpty();

    } else {
      // Fallback split has only alternatives set. Match the fallback iff the alternative languages
      // don't fully cover languages of the device.
      // The rationale is that if alternatives only partially cover the device languages, then
      // removing language(s) from the device can cause the app to start using the fallback
      // directory. But it would be confusing for the user if removal of a language triggered
      // download of additional language split(s). Therefore the fallback split should be present
      // from the beginning.
      Set<String> alternativeLanguages = ImmutableSet.copyOf(targetingValue.getAlternativesList());
      return !alternativeLanguages.containsAll(deviceLanguages);
    }
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !deviceLanguages.isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(LanguageTargeting targetingValue) {}
}
