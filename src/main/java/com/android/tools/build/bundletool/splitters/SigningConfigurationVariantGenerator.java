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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.variantTargeting;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import java.util.stream.Stream;

/** Generates variant targetings based on signing configuration. */
public final class SigningConfigurationVariantGenerator implements BundleModuleVariantGenerator {

  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public SigningConfigurationVariantGenerator(
      ApkGenerationConfiguration apkGenerationConfiguration) {
    this.apkGenerationConfiguration = apkGenerationConfiguration;
  }

  @Override
  public Stream<VariantTargeting> generate(BundleModule module) {
    return apkGenerationConfiguration.getMinSdkForAdditionalVariantWithV3Rotation().isPresent()
        ? Stream.of(
            variantTargeting(
                sdkVersionTargeting(
                    sdkVersionFrom(
                        apkGenerationConfiguration
                            .getMinSdkForAdditionalVariantWithV3Rotation()
                            .get()))))
        : Stream.of();
  }
}
