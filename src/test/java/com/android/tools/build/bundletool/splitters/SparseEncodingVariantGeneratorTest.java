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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_V2_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SparseEncodingVariantGeneratorTest {

  @Test
  public void variantsGeneration_sparseEncoding_generatesSV2Variant() throws Exception {
    SparseEncodingVariantGenerator sparseEncodingVariantGenerator =
        new SparseEncodingVariantGenerator(
            ApkGenerationConfiguration.builder().setEnableSparseEncodingVariant(true).build());
    ImmutableList<VariantTargeting> splits =
        sparseEncodingVariantGenerator
            .generate(createModuleWithSdk26Targeting(withTargetSdkVersion("O.fingerprint")))
            .collect(toImmutableList());
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_S_V2_API_VERSION));
  }

  @Test
  public void variantsGeneration_sparseEncodingDisabled_generatesNoVariant() throws Exception {
    SparseEncodingVariantGenerator sparseEncodingVariantGenerator =
        new SparseEncodingVariantGenerator(ApkGenerationConfiguration.getDefaultInstance());

    ImmutableList<VariantTargeting> splits =
        sparseEncodingVariantGenerator
            .generate(createModuleWithSdk26Targeting(withTargetSdkVersion("O.fingerprint")))
            .collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  /** Creates a minimal module */
  private static BundleModule createModuleWithSdk26Targeting(ManifestMutator... manifestMutators)
      throws IOException {
    return new BundleModuleBuilder("testModule")
        .setManifest(androidManifest("com.test.app", manifestMutators))
        .build();
  }
}
