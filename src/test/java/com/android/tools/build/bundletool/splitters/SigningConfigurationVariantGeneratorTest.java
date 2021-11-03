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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_R_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SigningConfigurationVariantGeneratorTest {
  @Test
  public void variantsGeneration_v3SigningRestrictedToRPlus_generatesRPlusVariant()
      throws Exception {
    SigningConfigurationVariantGenerator signingConfigurationVariantGenerator =
        new SigningConfigurationVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setMinSdkForAdditionalVariantWithV3Rotation(ANDROID_R_API_VERSION)
                .build());

    ImmutableCollection<VariantTargeting> splits =
        signingConfigurationVariantGenerator.generate(createModule()).collect(toImmutableList());

    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_R_API_VERSION));
  }

  @Test
  public void variantsGeneration_v3SigningNotRestrictedToRPlus_generatesNoVariant()
      throws Exception {
    SigningConfigurationVariantGenerator signingConfigurationVariantGenerator =
        new SigningConfigurationVariantGenerator(ApkGenerationConfiguration.getDefaultInstance());

    ImmutableCollection<VariantTargeting> splits =
        signingConfigurationVariantGenerator.generate(createModule()).collect(toImmutableList());

    assertThat(splits).isEmpty();
  }

  /** Creates a minimal module. */
  private static BundleModule createModule() throws IOException {
    return new BundleModuleBuilder("testModule")
        .setManifest(androidManifest("com.test.app"))
        .build();
  }
}
