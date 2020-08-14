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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withIsolatedSplits;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleTitleValidatorTest {
  private static final String PKG_NAME = "com.test.app";

  @Test
  public void validateAllModules_validJustBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("base", androidManifest(PKG_NAME)));

    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMissingTitle_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("demand", androidManifest(PKG_NAME, withOnDemandAttribute(true))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleTitleValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("Mandatory title is missing in manifest for module 'demand'");
  }

  @Test
  public void validateAllModules_isolatedSplits_onDemandModuleMissingTitle_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withIsolatedSplits(true))),
            module("demand", androidManifest(PKG_NAME, withOnDemandAttribute(true))));
    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleOlderBundleVersionMissingTitle_succeeds()
      throws Exception {

    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME), "0.4.2"),
            module("demand", androidManifest(PKG_NAME, withOnDemandAttribute(true)), "0.4.2"));

    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_nonOnDemandModuleMissingTitle_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("nonDemand", androidManifest(PKG_NAME)));

    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleTitleMissingInResourceTable_throws()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "demand",
                androidManifest(
                    PKG_NAME,
                    withOnDemandAttribute(true),
                    withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleTitleValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("is missing in the base resource table");
  }

  @Test
  public void validateAllModules_conditionalModuleTitleMissing_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("conditional", androidManifest(PKG_NAME, withMinSdkCondition(21))));
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleTitleValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("Mandatory title is missing in manifest for " + "module 'conditional'");
  }

  @Test
  public void validateAllModules_titlePresentInResourceTable_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME), resourceTableWithTestLabel("Test feature")),
            module(
                "demand",
                androidManifest(
                    PKG_NAME,
                    withOnDemandAttribute(true),
                    withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID))));

    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_baseAndAssetModuleWithoutTitle_succeeds() throws Exception {

    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("asset", androidManifestForAssetModule(PKG_NAME)));

    new ModuleTitleValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_baseAndAssetModuleWithTitle_throws() throws Exception {

    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "asset",
                androidManifestForAssetModule(
                    PKG_NAME, withTitle("test_label", TEST_LABEL_RESOURCE_ID))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleTitleValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .matches("Module titles not supported in asset packs, but found in 'asset'.");
  }

  @Test
  public void validateAllModules_assetOnly_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(
                "asset",
                BundleConfig.newBuilder().setType(BundleConfig.BundleType.ASSET_ONLY).build())
            .setManifest(androidManifestForAssetModule("com.app"))
            .build();

    new ModuleTitleValidator().validateAllModules(ImmutableList.of(module));
  }

  private static BundleModule module(String moduleName, XmlNode manifest) throws IOException {
    return new BundleModuleBuilder(moduleName).setManifest(manifest).build();
  }

  private static BundleModule module(String moduleName, XmlNode manifest, String version)
      throws IOException {
    BundleConfig bundleConfig = BundleConfigBuilder.create().setVersion(version).build();
    return new BundleModuleBuilder(moduleName, bundleConfig).setManifest(manifest).build();
  }

  private static BundleModule module(
      String moduleName, XmlNode manifest, ResourceTable resourceTable) throws IOException {
    return new BundleModuleBuilder(moduleName)
        .setManifest(manifest)
        .setResourceTable(resourceTable)
        .build();
  }
}
