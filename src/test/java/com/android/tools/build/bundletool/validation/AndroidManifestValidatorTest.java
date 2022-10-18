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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withApplication;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeRemovableElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstantInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstantOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSupportsGlTexture;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSandboxVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSdkLibraryElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withVersionCode;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class AndroidManifestValidatorTest {

  private static final String BASE_MODULE_NAME = "base";
  private static final String FEATURE_MODULE_NAME = "feature";
  private static final String PKG_NAME = "com.test.app";

  @Test
  public void base_withFusingConfigFalse_throws() throws Exception {
    BundleModule module = baseModule(withFusingAttribute(false));

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains("The base module cannot be excluded from fusing");
  }

  @Test
  public void base_withFusingConfigTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withoutFusingConfig_ok() throws Exception {
    BundleModule module = baseModule();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withFusingConfigFalseAndIsInstant_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withOnDemandAttribute(false),
                    withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withFusingConfigFalse_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withFusingConfigTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withoutFusingConfig_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemandAttribute(true)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .contains("Module 'feature' must specify its fusing configuration in AndroidManifest.xml");
  }

  @Test
  public void nonBase_withoutFusingConfigAndIsInstant_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withInstant(true), withOnDemandAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withoutOnDemandAttribute_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withOnDemandAttributeSetToFalse_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemandAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withInstallTimeDeliveryElement_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withInstallTimeDelivery()))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withOnDemandAttributeSetToTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemandAttribute(true)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains("The base module cannot be marked on-demand since it will always be served.");
  }

  @Test
  public void base_withOnDemandDeliveryElement_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemandDelivery()))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains("The base module cannot be marked on-demand since it will always be served.");
  }

  @Test
  public void base_withConditions_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withFeatureCondition("com.android.feature")))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception).hasMessageThat().contains("The base module cannot have conditions");
  }

  @Test
  public void nonBase_withNoDeliverySettings_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "The module must explicitly set its delivery options using the <dist:delivery> "
                    + "element (module: '%s')",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void nonBase_withOnDemandAttributeSetToTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withOnDemandAttribute(true), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withOnDemandAttributeSetToFalse_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withOnDemandAttribute(false), withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withOnDemandDeliveryElement_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withOnDemandDelivery(), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withInstallTimeDeliveryElement_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withInstallTimeDelivery(), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withConditions_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withMinSdkCondition(25), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withConditionsAndOnDemandDeliveryElement_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withMinSdkCondition(25),
                    withOnDemandDelivery(),
                    withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withConditionsAndOnDemandAttribute_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withMinSdkCondition(25),
                    withOnDemandAttribute(true),
                    withFusingAttribute(true)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Module '%s' cannot use <dist:delivery> settings and legacy dist:onDemand "
                    + "attribute at the same time",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void onDemandSetToFalseAndInstantAttributeSetToTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withOnDemandAttribute(false),
                    withInstant(true),
                    withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void installTimeDeliveryAndInstantAttributeSetToTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstallTimeDelivery(),
                    withInstant(true),
                    withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void installTimeAndOnDemandDeliveryAndInstantAttributeSetToTrue_ok() throws Exception {
    // On-demand delivery element does not make a module on-demand if install-time element is
    // present.
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstallTimeDelivery(),
                    withOnDemandDelivery(),
                    withInstant(true),
                    withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void withCorrectTargetSandboxVersionCode_ok() throws Exception {
    BundleModule module = baseModule(withTargetSandboxVersion(2));

    new AndroidManifestValidator().validateAllModules(ImmutableList.of(module));
  }

  @Test
  public void withHighTargetSandboxVersionCode_throws() throws Exception {
    BundleModule module = baseModule(withTargetSandboxVersion(3));

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(ImmutableList.of(module)));

    assertThat(e).hasMessageThat().contains("cannot have a value greater than 2, but found 3");
  }

  @Test
  public void withMinSdkLowerThanBase_throws() throws Exception {
    BundleModule base = baseModule(withMinSdkVersion(20));
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withMinSdkVersion(19)))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new AndroidManifestValidator().validateAllModules(ImmutableList.of(base, module)));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "cannot have a minSdkVersion attribute with a value lower than the one from the base"
                + " module");
  }

  @Test
  public void withMinSdkEqualThanBase_ok() throws Exception {
    BundleModule base = baseModule(withMinSdkVersion(20));
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withMinSdkVersion(20)))
            .build();

    new AndroidManifestValidator().validateAllModules(ImmutableList.of(base, module));

    // No exception thrown.
  }

  @Test
  public void withMinSdkUndeclared_ok() throws Exception {
    BundleModule base = baseModule(withMinSdkVersion(20));
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    new AndroidManifestValidator().validateAllModules(ImmutableList.of(base, module));

    // No exception thrown.
  }

  @Test
  public void withMinSdkHigherThanBase_ok() throws Exception {
    BundleModule base = baseModule(withMinSdkVersion(20));
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withMinSdkVersion(21)))
            .build();

    new AndroidManifestValidator().validateAllModules(ImmutableList.of(base, module));

    // No exception thrown.
  }

  @Test
  public void withNegativeMinSdk_throws() throws Exception {
    BundleModule module = baseModule(withMinSdkVersion(-1));

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e).hasMessageThat().isEqualTo("minSdkVersion must be nonnegative, found: (-1).");
  }

  @Test
  public void withNegativeMaxSdk_throws() throws Exception {
    BundleModule module = baseModule(withMaxSdkVersion(-1));

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e).hasMessageThat().isEqualTo("maxSdkVersion must be nonnegative, found: (-1).");
  }

  @Test
  public void withMinSdkEqualMaxSdk_ok() throws Exception {
    BundleModule module = baseModule(withMaxSdkVersion(5), withMinSdkVersion(5));

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void withMinSdkEqualMaxSdk_throws() throws Exception {
    BundleModule module = baseModule(withMaxSdkVersion(4), withMinSdkVersion(5));

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("minSdkVersion (5) is greater than maxSdkVersion (4).");
  }

  @Test
  public void withMaxSdkLessThanInstantSdk_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withOnDemandAttribute(false),
                    withFusingAttribute(false),
                    withMaxSdkVersion(18)))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("maxSdkVersion (18) is less than minimum sdk allowed for instant apps (21).");
  }

  @Test
  public void withMaxSdkEqualToInstantSdk_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withOnDemandAttribute(false),
                    withFusingAttribute(false),
                    withMaxSdkVersion(21)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void instantFeature_noMaxSdk() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withOnDemandAttribute(false),
                    withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void withMultipleDistinctSplitIds_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifestForFeature(
                    PKG_NAME,
                    withSplitId(FEATURE_MODULE_NAME),
                    withSecondSplitId("modulesplitname2")))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("attribute 'split' cannot be declared more than once");
  }

  @Test
  public void withMultipleEqualSplitIds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifestForFeature(
                    PKG_NAME,
                    withSplitId(FEATURE_MODULE_NAME),
                    withSecondSplitId(FEATURE_MODULE_NAME)))
            .build();

    // We accept multiple identical split IDs, so this should not throw.
    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void withoutSplitIds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void withOneSplitId() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifestForFeature(PKG_NAME, withSplitId(FEATURE_MODULE_NAME)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  private static ManifestMutator withSecondSplitId(String splitId) {
    return manifestElement ->
        manifestElement.addAttribute(
            XmlProtoAttributeBuilder.create("split").setValueAsString(splitId));
  }

  private BundleModule baseModule(ManifestMutator... mutators) throws IOException {
    return new BundleModuleBuilder(BASE_MODULE_NAME)
        .setManifest(androidManifest(PKG_NAME, mutators))
        .build();
  }

  @Test
  public void instantModule_withoutBase_throws() throws Exception {
    BundleModule featureModule =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withInstant(true),
                    withOnDemandAttribute(false),
                    withFusingAttribute(false)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new AndroidManifestValidator()
                    .validateAllModules(ImmutableList.of(baseModule(), featureModule)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "App Bundle contains instant modules but the 'base' module is not marked 'instant'.");
  }

  @Test
  public void bundleModules_sameVersionCode_ok() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build(),
            new BundleModuleBuilder(FEATURE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build());

    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void bundleModules_differentVersionCode_throws() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build(),
            new BundleModuleBuilder(FEATURE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(3)))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("App Bundle modules should have the same version code but found [2, 3]");
  }

  @Test
  public void bundleModules_differentTargetSandboxVersionCode_throws() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withTargetSandboxVersion(1)))
                .build(),
            new BundleModuleBuilder(FEATURE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withTargetSandboxVersion(2)))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("should have the same value across modules, but found [1,2]");
  }

  @Test
  public void assetModule_noApplication_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("asset_module")
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();
    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void assetModule_withApplication_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("asset_module")
            .setManifest(
                androidManifestForAssetModule(
                    "com.test.app", withOnDemandDelivery(), withApplication()))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Unexpected element declaration in manifest of asset pack 'asset_module'.");
  }

  @DataPoints("sdkMutators")
  public static final ImmutableSet<ManifestMutator> SDK_MUTATORS =
      ImmutableSet.of(withMinSdkVersion(10), withMaxSdkVersion(20), withTargetSdkVersion("O"));

  @Test
  @Theory
  public void assetModule_withSdkConstraint_throws(
      @FromDataPoints("sdkMutators") ManifestMutator sdkMutator) throws Exception {
    BundleModule module =
        new BundleModuleBuilder("asset_module")
            .setManifest(androidManifestForAssetModule("com.test.app", sdkMutator))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Unexpected element declaration in manifest of asset pack 'asset_module'.");
  }

  @Test
  public void assetModule_noVersionCode_ok() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build(),
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule("com.test.app"))
                .build());
    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void assetModule_withVersionCode_throws() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build(),
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule("com.test.app", withVersionCode(42)))
                .build());

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));
    assertThat(exception)
        .hasMessageThat()
        .matches("Asset packs cannot specify a version code, but 'asset_module' does.");
  }

  @Test
  public void minSdkConditionLowerThanMinSdkVersion_throws() throws Exception {
    BundleModule featureModule =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withFusingAttribute(false),
                    withMinSdkCondition(21),
                    withMinSdkVersion(23)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(featureModule));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Module 'feature' has <dist:min-sdk> condition (21) lower than the "
                + "minSdkVersion(23) of the module.");
  }

  @Test
  public void minSdkConditionGreaterEqualThanMinSdkVersion_ok() throws Exception {
    BundleModule featureModule =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withFusingAttribute(true),
                    withMinSdkCondition(24),
                    withMinSdkVersion(19)))
            .build();

    new AndroidManifestValidator().validateModule(featureModule);
  }

  @Test
  public void assetModuleWithInstantAttributeAndInstantDeliveryElement_throws() throws Exception {
    BundleModule featureModule =
        new BundleModuleBuilder("asset_module")
            .setManifest(
                androidManifestForAssetModule(
                    PKG_NAME, withInstantOnDemandDelivery(), withInstant(true)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(featureModule));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The <dist:instant-delivery> element and dist:instant attribute cannot be used"
                + " together (module: 'asset_module').");
  }

  @Test
  public void assetModuleWithConditionalTargeting_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("assetmodule")
            .setManifest(androidManifestForAssetModule(PKG_NAME, withFeatureCondition("camera")))
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches(
            "Conditional targeting is not allowed in asset packs, but found in 'assetmodule'.");
  }

  @Test
  public void assetModuleWithOnDemandAndInstantAttributeSetToTrue_ok() throws Exception {
    BundleModule assetModule =
        new BundleModuleBuilder("assetmodule")
            .setManifest(
                androidManifestForAssetModule(
                    PKG_NAME, withOnDemandAttribute(true), withInstant(true)))
            .build();

    new AndroidManifestValidator().validateModule(assetModule);
  }

  @Test
  public void instantAssetModuleWithInstallTimeDelivery_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("assetmodule")
            .setManifest(
                androidManifestForAssetModule(
                    PKG_NAME, withInstallTimeDelivery(), withInstantOnDemandDelivery()))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .startsWith(
            "Instant asset packs cannot have install-time delivery (module 'assetmodule').");
  }

  @Test
  public void instantAssetModuleWithInstallTimeInstantDelivery_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("assetmodule")
            .setManifest(
                androidManifestForAssetModule(
                    PKG_NAME, withOnDemandDelivery(), withInstantInstallTimeDelivery()))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("Instant delivery cannot be install-time (module 'assetmodule').");
  }

  @Test
  public void assetModulesWithAllowedDeliveryCombinationsSucceed() throws Exception {
    ImmutableList<BundleModule> modules =
        ImmutableList.of(
            new BundleModuleBuilder("assetmodule1")
                .setManifest(
                    androidManifestForAssetModule(
                        PKG_NAME, withOnDemandDelivery(), withInstant(true)))
                .build(),
            new BundleModuleBuilder("assetmodule2")
                .setManifest(
                    androidManifestForAssetModule(
                        PKG_NAME, withOnDemandAttribute(true), withInstant(true)))
                .build(),
            new BundleModuleBuilder("assetmodule3")
                .setManifest(
                    androidManifestForAssetModule(
                        PKG_NAME, withOnDemandDelivery(), withInstantOnDemandDelivery()))
                .build(),
            new BundleModuleBuilder("assetmodule4")
                .setManifest(
                    androidManifestForAssetModule(
                        PKG_NAME, withOnDemandAttribute(true), withInstantOnDemandDelivery()))
                .build());

    AndroidManifestValidator validator = new AndroidManifestValidator();
    modules.forEach(validator::validateModule);
  }

  @Test
  public void noSupportsGlTextureWithTcfs_ok() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .addFile("assets/textures#tcf_astc/level1.assets")
                .setManifest(androidManifest("com.test", withVersionCode(2)))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures#tcf_astc/astc_file.assets")
                .setManifest(androidManifestForAssetModule("com.test.app"))
                .build());

    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void supportsGlTextureWithoutTcfs_ok() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .addFile("assets/textures/level1.assets")
                .setManifest(
                    androidManifest(
                        "com.test",
                        withVersionCode(2),
                        withSupportsGlTexture("GL_OES_compressed_ETC1_RGB8_texture")))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures/file.assets")
                .setManifest(androidManifestForAssetModule("com.test.app"))
                .build());

    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void supportsGlTextureWithTcfs_throws() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .setManifest(
                    androidManifest(
                        "com.test",
                        withVersionCode(2),
                        withSupportsGlTexture("GL_OES_compressed_ETC1_RGB8_texture")))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures#tcf_astc/astc_file.assets")
                .setManifest(androidManifestForAssetModule("com.test.app"))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Modules cannot have supports-gl-texture in their manifest (found:"
                + " [GL_OES_compressed_ETC1_RGB8_texture]) and"
                + " texture targeted directories in modules (found: [ASTC]).");
  }

  @Test
  public void installTimeModuleNonRemovable_ok() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .addFile("assets/textures#tcf_astc/level1.assets")
                .setManifest(androidManifest("com.test"))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures#tcf_astc/astc_file.assets")
                .setManifest(
                    androidManifestForAssetModule(
                        "com.test.app", withInstallTimeRemovableElement(false)))
                .build());
    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void conditionalModuleNonRemovable_throws() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .addFile("assets/textures#tcf_astc/level1.assets")
                .setManifest(androidManifest("com.test"))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures#tcf_astc/astc_file.assets")
                .setManifest(
                    androidManifestForAssetModule(
                        "com.test.app",
                        withInstallTimeRemovableElement(false),
                        withMinSdkVersion(24),
                        withFeatureCondition("android.feature")))
                .build());

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("Conditional modules cannot be set to non-removable.");
  }

  @Test
  public void conditionalModuleRemovableNotSet() throws Exception {
    ImmutableList<BundleModule> bundleModules =
        ImmutableList.of(
            new BundleModuleBuilder(BASE_MODULE_NAME)
                .addFile("assets/textures#tcf_astc/level1.assets")
                .setManifest(androidManifest("com.test"))
                .build(),
            new BundleModuleBuilder("asset_module")
                .addFile("assets/other_textures#tcf_astc/astc_file.assets")
                .setManifest(
                    androidManifestForAssetModule(
                        "com.test.app",
                        withMinSdkVersion(24),
                        withFeatureCondition("android.feature")))
                .build());

    new AndroidManifestValidator().validateAllModules(bundleModules);
  }

  @Test
  public void assetOnly_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(
                BASE_MODULE_NAME,
                BundleConfig.newBuilder().setType(BundleConfig.BundleType.ASSET_ONLY).build())
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .build();

    new AndroidManifestValidator().validateAllModules(ImmutableList.of(module));
  }

  @Test
  public void usesSdkLibraryTagPresent_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withUsesSdkLibraryElement(
                        "com.test.sdk", /* versionMajor= */ 1, "cert-digest")))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "<uses-sdk-library> element not allowed in the manifest of App Bundle. An App Bundle"
                + " should depend on a runtime-enabled SDK by specifying its details in the"
                + " runtime_enabled_sdk_config.pb, not in its manifest.");
  }
}
