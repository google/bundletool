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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Generates variants that targets devices with SDK Runtime support. */
public final class SdkRuntimeVariantGenerator {

  private final AppBundle appBundle;

  @Inject
  SdkRuntimeVariantGenerator(AppBundle appBundle) {
    this.appBundle = appBundle;
  }

  /**
   * Generates variants targeting SDK Runtime devices, based on all other SDK version variant
   * targetings.
   *
   * <p>It is assumed that the {@code sdkVersionVariantTargetings} contain only {@link
   * SdkVersionTargeting}.
   *
   * <p>This method generates a new {@link VariantTargeting} for each element of {@code
   * sdkVersionVariantTargetings} that targets SDK version higher than {@link
   * Versions#ANDROID_T_API_VERSION}, as well as one variant targeting {@link
   * Versions#ANDROID_T_API_VERSION}.
   *
   * <p>For example, if {@code sdkVersionVariantTargetings} contains 2 variants: targeting Android S
   * API and Android U API, the method will return 2 new variants: targeting Android T API and
   * Android U API.
   */
  public ImmutableSet<VariantTargeting> generate(
      ImmutableSet<VariantTargeting> sdkVersionVariantTargetings) {
    if (appBundle.getRuntimeEnabledSdkDependencies().isEmpty()) {
      return ImmutableSet.of();
    }

    return Streams.concat(
            sdkVersionVariantTargetings.stream()
                .filter(
                    variantTargeting ->
                        variantTargeting.getSdkVersionTargeting().getValue(0).getMin().getValue()
                            > ANDROID_T_API_VERSION)
                .map(
                    variantTargeting ->
                        variantTargeting.toBuilder()
                            .setSdkRuntimeTargeting(
                                SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
                            .build()),
            Stream.of(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION)))
        .collect(toImmutableSet());
  }
}
