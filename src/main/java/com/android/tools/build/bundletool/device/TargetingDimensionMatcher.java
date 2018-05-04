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

package com.android.tools.build.bundletool.device;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.base.Predicates;
import java.util.function.Predicate;

/**
 * Matches the targeting value T against a {@link DeviceSpec} in the APK and Variant targeting
 * context.
 */
public abstract class TargetingDimensionMatcher<T> {

  private final DeviceSpec deviceSpec;

  public TargetingDimensionMatcher(DeviceSpec deviceSpec) {
    this.deviceSpec = deviceSpec;
  }

  protected DeviceSpec getDeviceSpec() {
    return deviceSpec;
  }

  /**
   * Returns a convenient predicate on {@link ApkTargeting} message.
   *
   * @return a predicate that extracts the targeting value from the {@link ApkTargeting} message and
   *     calls the {@link TargetingDimensionMatcher#matchesTargeting}
   */
  public Predicate<ApkTargeting> getApkTargetingPredicate() {
    return Predicates.compose(this::matchesTargeting, this::getTargetingValue);
  }

  /**
   * Returns a convenient predicate on {@link VariantTargeting} message.
   *
   * @return a predicate that extracts the targeting value from the {@link VariantTargeting} message
   *     and calls the {@link TargetingDimensionMatcher#matchesTargeting}
   */
  public Predicate<VariantTargeting> getVariantTargetingPredicate() {
    return Predicates.compose(this::matchesTargeting, this::getTargetingValue);
  }

  protected abstract T getTargetingValue(ApkTargeting apkTargeting);

  protected abstract T getTargetingValue(VariantTargeting variantTargeting);

  /** Returns if the given targeting value matches the device spec. */
  public abstract boolean matchesTargeting(T targetingValue);
}
