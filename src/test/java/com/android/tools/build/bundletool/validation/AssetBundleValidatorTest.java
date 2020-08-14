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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetBundleValidatorTest {

  private static final String PKG_NAME = "com.test.app";

  @Test
  public void validateAllModules_validAssetOnly_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(assetOnlyModule("asset", androidManifestForAssetModule(PKG_NAME)));

    new AssetBundleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_assetOnlyWithBase_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            assetOnlyModule(
                "asset", androidManifestForAssetModule(PKG_NAME, withOnDemandDelivery())),
            assetOnlyModule("base", androidManifest(PKG_NAME)));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetBundleValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("Asset only bundle contains a module that is not an asset module");
  }

  @Test
  public void validateAllModules_assetOnlyWithInstallTime_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            assetOnlyModule(
                "asset", androidManifestForAssetModule(PKG_NAME, withInstallTimeDelivery())));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetBundleValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("Asset-only bundle contains an install-time asset module");
  }

  private static BundleModule assetOnlyModule(String moduleName, XmlNode manifest)
      throws IOException {
    return new BundleModuleBuilder(
            moduleName,
            BundleConfig.newBuilder().setType(BundleConfig.BundleType.ASSET_ONLY).build())
        .setManifest(manifest)
        .build();
  }
}
