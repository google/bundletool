/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleNamesValidatorTest {

  @Test
  public void allModuleValid() {
    BundleModule base =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();
    BundleModule feature1 =
        buildBundleModule("feature1")
            .setAndroidManifestProto(androidManifestForFeature("com.app", withSplitId("feature1")))
            .build();
    BundleModule feature2 =
        buildBundleModule("feature2")
            .setAndroidManifestProto(androidManifest("com.app", withSplitId("feature2")))
            .build();

    // No exception = pass.
    new ModuleNamesValidator().validateAllModules(ImmutableList.of(base, feature1, feature2));
  }

  @Test
  public void moreThanOneModuleWithoutSplitId() {
    BundleModule base1 =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();
    BundleModule base2 =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();

    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleNamesValidator().validateAllModules(ImmutableList.of(base1, base2)));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "More than one module was found without the 'split' attribute set in the "
                + "AndroidManifest.xml.");
  }

  @Test
  public void baseAndAssetModules() {
    BundleModule base =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();
    BundleModule assetModule =
        buildBundleModule("asset")
            .setAndroidManifestProto(androidManifestForAssetModule("com.app", withSplitId("asset")))
            .build();

    new ModuleNamesValidator().validateAllModules(ImmutableList.of(base, assetModule));
  }

  @Test
  public void moreThanOneSameFeatureModule() {
    BundleModule base =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();
    BundleModule feature1 =
        buildBundleModule("feature")
            .setAndroidManifestProto(androidManifestForFeature("com.app", withSplitId("feature")))
            .build();
    BundleModule feature2 =
        buildBundleModule("feature")
            .setAndroidManifestProto(androidManifest("com.app", withSplitId("feature")))
            .build();

    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ModuleNamesValidator()
                    .validateAllModules(ImmutableList.of(base, feature1, feature2)));
    assertThat(expected)
        .hasMessageThat()
        .contains("More than one module have the 'split' attribute set to 'feature'");
  }

  @Test
  public void splitIdSetOnBaseModule() {
    BundleModule base =
        buildBundleModule("base")
            .setAndroidManifestProto(androidManifest("com.app", withSplitId("base")))
            .build();

    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleNamesValidator().validateAllModules(ImmutableList.of(base)));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "The base module should not have the 'split' attribute set in the AndroidManifest.xml");
  }

  @Test
  public void splitIdDoesNotMatchModuleName() {
    BundleModule base =
        buildBundleModule("base").setAndroidManifestProto(androidManifest("com.app")).build();
    BundleModule feature =
        buildBundleModule("module")
            .setAndroidManifestProto(androidManifest("com.app", withSplitId("feature")))
            .build();
    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleNamesValidator().validateAllModules(ImmutableList.of(base, feature)));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "The 'split' attribute in the AndroidManifest.xml of modules must be the name of the "
                + "module, but has the value 'feature' in module 'module'");
  }

  @Test
  public void noBaseModule() {
    BundleModule feature =
        buildBundleModule("feature")
            .setAndroidManifestProto(androidManifest("com.app", withSplitId("feature")))
            .build();

    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleNamesValidator().validateAllModules(ImmutableList.of(feature)));
    assertThat(expected).hasMessageThat().contains("No base module found.");
  }

  @Test
  public void assetOnly_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(
                "asset", BundleConfig.newBuilder().setType(BundleType.ASSET_ONLY).build())
            .setManifest(androidManifestForAssetModule("com.app"))
            .build();

    new ModuleNamesValidator().validateAllModules(ImmutableList.of(module));
  }

  private static BundleModule.Builder buildBundleModule(String moduleName) {
    return BundleModule.builder()
        .setName(BundleModuleName.create(moduleName))
        .setBundleType(BundleType.REGULAR)
        .setBundletoolVersion(BundleToolVersion.getCurrentVersion());
  }
}
