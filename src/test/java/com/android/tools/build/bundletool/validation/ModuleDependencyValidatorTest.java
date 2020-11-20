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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withIsolatedSplits;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleDependencyValidatorTest {

  private static final String PKG_NAME = "com.test.app";

  @Test
  public void validateAllModules_validJustBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("base", androidManifest(PKG_NAME)));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_missingBase_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("not_base", androidManifest(PKG_NAME, withSplitId("not_base"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("Mandatory 'base' module is missing");
  }

  @Test
  public void validateAllModules_validTree_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("featureA", androidManifest(PKG_NAME)), // implicitly depends on base
            module("subFeatureA1", androidManifest(PKG_NAME, withUsesSplit("featureA"))),
            module("featureB", androidManifest(PKG_NAME)), // implicitly depends on base
            module("subFeatureB1", androidManifest(PKG_NAME, withUsesSplit("featureB"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_validDiamond_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("f1", androidManifest(PKG_NAME)), // implicitly depends on base
            module("f2", androidManifest(PKG_NAME)), // implicitly depends on base
            module("f12", androidManifest(PKG_NAME, withUsesSplit("f1", "f2"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_reflexiveDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature", androidManifest(PKG_NAME, withUsesSplit("feature"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("depends on itself");
  }

  @Test
  public void validateAllModules_duplicateDependencies_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature", androidManifest(PKG_NAME)), // implicitly depends on base
            module("sub_feature", androidManifest(PKG_NAME, withUsesSplit("feature", "feature"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("declares dependency on module 'feature' multiple times");
  }

  @Test
  public void validateAllModules_referencesUnknownModule_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("featureA", androidManifest(PKG_NAME, withUsesSplit("unknown"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("Module 'unknown' is referenced by <uses-split> but does not exist");
  }

  @Test
  public void validateAllModules_cycle_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("module1", androidManifest(PKG_NAME, withUsesSplit("module2"))),
            module("module2", androidManifest(PKG_NAME, withUsesSplit("module3"))),
            module("module3", androidManifest(PKG_NAME, withUsesSplit("module1"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("Found cyclic dependency between modules");
  }

  private static BundleModule module(String moduleName, XmlNode manifest) throws IOException {
    return new BundleModuleBuilder(moduleName).setManifest(manifest).build();
  }

  @Test
  public void validateAllModules_installTimeToOnDemandModulesDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME, withOnDemandAttribute(true))),
            module("feature2", androidManifest(PKG_NAME, withUsesSplit("feature1"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature2' cannot depend on a module 'feature1' that is not "
                + "install-time");
  }

  @Test
  public void validateAllModules_installTimeToInstallTimeModulesDependency_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME)),
            module("feature2", androidManifest(PKG_NAME, withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandToInstallTimeModulesDependency_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME)),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandToOnDemandModulesDependency_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME, withOnDemandAttribute(true))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkSmallerThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withMinSdkVersion(19))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleEffectiveMinSdkSmallerThanBase_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withOnDemandAttribute(true))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkSmallerThanOnDemandModule_fails()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(
                    PKG_NAME,
                    withOnDemandAttribute(true),
                    withUsesSplit("feature2"),
                    withMinSdkVersion(19))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withMinSdkVersion(20))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Conditional or on-demand module 'feature1' has a minSdkVersion(19), which is smaller "
                + "than the minSdkVersion(20) of its dependency 'feature2'.");
  }

  @Test
  public void validateAllModules_onDemandModuleEffectiveMinSdkSmallerThanOnDemandModule_fails()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withUsesSplit("feature2"))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withMinSdkVersion(20))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Conditional or on-demand module 'feature1' has a minSdkVersion(1), which is smaller "
                + "than the minSdkVersion(20) of its dependency 'feature2'.");
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkGreaterThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withMinSdkVersion(21))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkEqualToBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withMinSdkVersion(20))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkGreaterThanBase_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(21))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(21) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkSmallerThanBase_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(19))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(19) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkEqualToBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(20))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_installTimeModuleEffectiveMinSdk_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME)));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(1) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }

  @Test
  public void validateAllModules_conditionalModuleMinSdkHigherThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(
                    PKG_NAME, withMinSdkVersion(24), withFeatureCondition("android.feature"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_conditionalModuleMinSdkLowerThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(
                    PKG_NAME, withMinSdkVersion(18), withFeatureCondition("android.feature"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_assetModuleWithoutSdk_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("asset", androidManifestForAssetModule(PKG_NAME)));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_conditionalModule_dependsOnConditional_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "conditional",
                androidManifest(
                    PKG_NAME,
                    withFeatureCondition("android.feature"),
                    withUsesSplit("conditional2"))),
            module(
                "conditional2",
                androidManifest(PKG_NAME, withFeatureCondition("android.feature2"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Conditional module 'conditional' cannot depend on a module 'conditional2' that is "
                + "not install-time.");
  }

  @Test
  public void validateAllModules_conditionalModule_dependsOnInstallTime_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "conditional",
                androidManifest(
                    PKG_NAME,
                    withFeatureCondition("android.feature"),
                    withUsesSplit("installTime"))),
            module("installTime", androidManifest(PKG_NAME, withInstallTimeDelivery())));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_installTime_dependsOnConditional_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "installtime",
                androidManifest(PKG_NAME, withInstallTimeDelivery(), withUsesSplit("conditional"))),
            module(
                "conditional", androidManifest(PKG_NAME, withFeatureCondition("android.feature"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'installtime' cannot depend on a module 'conditional' that is "
                + "not install-time");
  }

  @Test
  public void validateAllModules_onDemandOnly_dependsOnConditional_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "ondemand",
                androidManifest(PKG_NAME, withOnDemandDelivery(), withUsesSplit("conditional"))),
            module(
                "conditional", androidManifest(PKG_NAME, withFeatureCondition("android.feature"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "An on-demand module 'ondemand' cannot depend on a conditional module 'conditional'");
  }

  @Test
  public void validateAllModules_assetModuleHasDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module(
                "asset",
                androidManifestForAssetModule(
                    PKG_NAME, withOnDemandDelivery(), withUsesSplit("feature"))),
            module("feature", androidManifest(PKG_NAME, withOnDemandDelivery())));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'asset' cannot depend on module 'feature' because one of them is an asset"
                + " pack.");
  }

  @Test
  public void validateAllModules_assetModuleHasDependee_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("asset", androidManifestForAssetModule(PKG_NAME, withOnDemandDelivery())),
            module(
                "feature",
                androidManifest(PKG_NAME, withOnDemandDelivery(), withUsesSplit("asset"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'feature' cannot depend on module 'asset' because one of them is an asset"
                + " pack.");
  }

  @Test
  public void validateAllModules_instantModuleToInstallTimeDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true))),
            module("feature1", androidManifest(PKG_NAME, withInstant(false))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withUsesSplit("feature1"), withInstant(true))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Instant module 'feature2' cannot depend on a module 'feature1' that is not instant.");
  }

  @Test
  public void validateAllModules_instantModuleToOnDemandModulesDependency_throws()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withInstant(false))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withUsesSplit("feature1"), withInstant(true))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Instant module 'feature2' cannot depend on a module 'feature1' that is not instant.");
  }

  @Test
  public void validateAllModules_instantModuleToConditionalModulesDependency_throws()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true))),
            module(
                "feature1",
                androidManifest(
                    PKG_NAME, withFeatureCondition("android.feature"), withInstant(false))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withUsesSplit("feature1"), withInstant(true))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Instant module 'feature2' cannot depend on a module 'feature1' that is not instant.");
  }

  @Test
  public void validateAllModules_instantToInstantModulesDependency_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true))),
            module("feature1", androidManifest(PKG_NAME, withInstant(true))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withInstant(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_isolatedSplitsWithSingleModuleDependency_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true), withIsolatedSplits(true))),
            module("feature1", androidManifest(PKG_NAME, withInstant(true))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withInstant(true), withUsesSplit("feature1"))),
            module(
                "feature3",
                androidManifest(PKG_NAME, withInstant(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_isolatedSplitsWithMultipleModuleDependencies_throws()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withInstant(true), withIsolatedSplits(true))),
            module("feature1", androidManifest(PKG_NAME, withInstant(true))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withInstant(true), withUsesSplit("feature1"))),
            module(
                "feature3",
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withUsesSplit("feature1"),
                    withUsesSplit("feature2"))));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Isolated module 'feature3' cannot depend on more than one other module, "
                + "but it depends on [feature1, feature2].");
  }
}
