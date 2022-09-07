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
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.base.Predicates;
import java.util.function.Predicate;

/**
 * Matches the targeting value T against a {@link DeviceSpec} in the APK and Variant targeting
 * context.
 *
 * <p>{@link TargetingDimensionMatcher#getTargetingValue} methods implement extracting the specific
 * targeting information from the targeting message proto of: variants, modules or APKs. Whenever
 * this operation is not feasible an {@link UnsupportedOperationException} is thrown.
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
   */
  public Predicate<ApkTargeting> getApkTargetingPredicate() {
    return Predicates.compose(this::matchesTargeting, this::getTargetingValue);
  }

  /**
   * Returns a convenient predicate on {@link VariantTargeting} message.
   *
   * @return a predicate that extracts the targeting value from the {@link VariantTargeting} message
   *     and calls the {@link TargetingDimensionMatcher#matchesTargeting}. If the device spec
   *     doesn't target this dimension, we match the spec with any targeting of the dimension.
   */
  public Predicate<VariantTargeting> getVariantTargetingPredicate() {
    return variantTargeting ->
        !isDeviceDimensionPresent() || matchesTargeting(getTargetingValue(variantTargeting));
  }

  /**
   * Returns a convenient predicate on {@link ModuleTargeting}.
   *
   * <p>As this is used to determine if a conditional module should be installed, the device
   * incompatibility safety checks are not performed. Should this happen, the module will simply
   * fail the matching.
   */
  public Predicate<ModuleTargeting> getModuleTargetingPredicate() {
    return Predicates.compose(
        targetingValue -> !isDeviceDimensionPresent() || matchesTargeting(targetingValue),
        this::getTargetingValue);
  }

  /**
   * Extracts dimension specific message from the targeting proto
   *
   * <p>Some of the Targeting protos may not support all dimensions, in that case if this method is
   * not overridden it will throw {@link UnsupportedOperationException}.
   */
  protected T getTargetingValue(ApkTargeting apkTargeting) {
    throw new UnsupportedOperationException();
  }

  /**
   * Extracts dimension specific message from the targeting proto
   *
   * <p>Some of the Targeting protos may not support all dimensions, in that case if this method is
   * not overridden it will throw {@link UnsupportedOperationException}.
   */
  protected T getTargetingValue(VariantTargeting variantTargeting) {
    throw new UnsupportedOperationException();
  }

  /**
   * Extracts dimension specific message from the targeting proto
   *
   * <p>Some of the Targeting protos may not support all dimensions, in that case if this method is
   * not overridden it will throw {@link UnsupportedOperationException}.
   */
  protected T getTargetingValue(ModuleTargeting moduleTargeting) {
    throw new UnsupportedOperationException();
  }

  /** Returns if the {@link DeviceSpec} has the dimension that this instance uses for matching. */
  protected abstract boolean isDeviceDimensionPresent();

  /** Returns if the given targeting value matches the device spec. */
  public abstract boolean matchesTargeting(T targetingValue);

  /**
   * Checks if a device is compatible with a given targeting considering alternatives.
   *
   * @throws CommandExecutionException if a device can't support given targeting value
   */
  public void checkDeviceCompatible(T targetingValue) {
    if (isDeviceDimensionPresent()) {
      checkDeviceCompatibleInternal(targetingValue);
    }
  }

  protected abstract void checkDeviceCompatibleInternal(T targetingValue);
}
