/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.StandaloneConfig.FeatureModulesMode;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StandaloneFeatureModuleValidatorTest {

  private final StandaloneFeatureModulesValidator validator =
      new StandaloneFeatureModulesValidator();

  @Test
  public void fusedFeatureModules_minSdkHigherThan21_ok() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.FUSED_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
                    .setManifest(androidManifest("com.app", withMinSdkVersion(22)))
                    .build())
            .build();

    validator.validateBundle(appBundle);
  }

  @Test
  public void assetPackOnlyBundle_ok() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.FUSED_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder("asset_pack")
                    .setManifest(androidManifestForAssetModule("com.app"))
                    .build())
            .build();

    validator.validateBundle(appBundle);
  }

  @Test
  public void standaloneFeatureModules_multipleOnDemandFeatureModules_ok() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.SEPARATE_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
                    .setManifest(androidManifest("com.app", withMinSdkVersion(19)))
                    .build())
            .addModule(
                new BundleModuleBuilder("feature1")
                    .setManifest(androidManifestForFeature("com.app", withOnDemandDelivery()))
                    .build())
            .addModule(
                new BundleModuleBuilder("feature2")
                    .setManifest(androidManifestForFeature("com.app", withOnDemandDelivery()))
                    .build())
            .build();

    validator.validateBundle(appBundle);
  }

  @Test
  public void standaloneFeatureModules_minSdkHigherThan21_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.SEPARATE_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
                    .setManifest(androidManifest("com.app", withMinSdkVersion(22)))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(InvalidBundleException.class, () -> validator.validateBundle(appBundle));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "STANDALONE_FEATURE_MODULES can only be used for Android App Bundles with minSdk <"
                + " 21.");
  }

  @Test
  public void standaloneFeatureModules_assetModule_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.SEPARATE_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
                    .setManifest(androidManifest("com.app", withMinSdkVersion(19)))
                    .build())
            .addModule(
                new BundleModuleBuilder("asset_pack")
                    .setManifest(androidManifestForAssetModule("com.app"))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(InvalidBundleException.class, () -> validator.validateBundle(appBundle));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Asset modules are not supported for Android App Bundles with"
                + " STANDALONE_FEATURE_MODULES enabled.");
  }

  @Test
  public void standaloneFeatureModules_conditionalFeatureModule_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .setFeatureModulesModeForStandalone(FeatureModulesMode.SEPARATE_FEATURE_MODULES)
                    .build())
            .addModule(
                new BundleModuleBuilder(BundleModuleName.BASE_MODULE_NAME.getName())
                    .setManifest(androidManifest("com.app", withMinSdkVersion(19)))
                    .build())
            .addModule(
                new BundleModuleBuilder("feature")
                    .setManifest(androidManifestForFeature("com.app", withMinSdkCondition(23)))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(InvalidBundleException.class, () -> validator.validateBundle(appBundle));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Only on-demand feature modules are supported for Android App Bundles with "
                + "STANDALONE_FEATURE_MODULES enabled.");
  }
}
