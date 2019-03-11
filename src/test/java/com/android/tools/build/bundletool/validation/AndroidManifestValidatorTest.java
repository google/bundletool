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

import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.clearApplication;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withVersionCode;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withoutVersionCode;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestDuplicateAttributeException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MaxSdkLessThanMinInstantSdk;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MinSdkGreaterThanMaxSdkException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestVersionCodeConflictException;
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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));

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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));

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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception).hasMessageThat().contains("The base module cannot have conditions");
  }

  @Test
  public void nonBase_withNoDeliverySettings_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
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

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Module '%s' cannot use <dist:delivery> settings and legacy dist:onDemand "
                    + "attribute at the same time",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void onDemandAndInstantAttributeSetToTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withOnDemandAttribute(true),
                    withInstant(true),
                    withFusingAttribute(true)))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Module cannot be on-demand and 'instant' at the same time (module '%s').",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void onDemandElementAndInstantAttributeSetToTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME, withOnDemandDelivery(), withInstant(true), withFusingAttribute(true)))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Module cannot be on-demand and 'instant' at the same time (module '%s').",
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
  public void moduleConditionsSetAndInstantAttributeTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withFeatureCondition("com.android.feature"),
                    withInstant(true),
                    withFusingAttribute(true)))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "The attribute 'instant' cannot be true for conditional module" + " (module '%s').",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void withNegativeMinSdk_throws() throws Exception {
    BundleModule module = baseModule(withMinSdkVersion(-1));

    MinSdkInvalidException e =
        assertThrows(
            MinSdkInvalidException.class,
            () -> new AndroidManifestValidator().validateModule(module));

    assertThat(e).hasMessageThat().isEqualTo("minSdkVersion must be nonnegative, found: (-1).");
  }

  @Test
  public void withNegativeMaxSdk_throws() throws Exception {
    BundleModule module = baseModule(withMaxSdkVersion(-1));

    MaxSdkInvalidException e =
        assertThrows(
            MaxSdkInvalidException.class,
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

    MinSdkGreaterThanMaxSdkException e =
        assertThrows(
            MinSdkGreaterThanMaxSdkException.class,
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

    MaxSdkLessThanMinInstantSdk e =
        assertThrows(
            MaxSdkLessThanMinInstantSdk.class,
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

    ManifestDuplicateAttributeException e =
        assertThrows(
            ManifestDuplicateAttributeException.class,
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
            ValidationException.class,
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

    ManifestVersionCodeConflictException exception =
        assertThrows(
            ManifestVersionCodeConflictException.class,
            () -> new AndroidManifestValidator().validateAllModules(bundleModules));
    assertThat(exception)
        .hasMessageThat()
        .contains("App Bundle modules should have the same version code but found [2,3]");
  }

  @Test
  public void assetModule_noApplication_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("asset_module")
            .setManifest(
                androidManifest(
                    "com.test.app",
                    withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                    withOnDemandDelivery(),
                    withFusingAttribute(true),
                    clearApplication()))
            .build();
    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void assetModule_withApplication_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("asset_module")
            .setManifest(
                androidManifest(
                    "com.test.app",
                    withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                    withOnDemandDelivery(),
                    withFusingAttribute(true)))
            .build();

    Throwable exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
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
            .setManifest(
                androidManifest(
                    "com.test.app",
                    withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                    withOnDemandDelivery(),
                    withFusingAttribute(true),
                    clearApplication(),
                    sdkMutator))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
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
                .setManifest(
                    androidManifest(
                        "com.test.app",
                        withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                        withOnDemandDelivery(),
                        withFusingAttribute(true),
                        clearApplication(),
                        withoutVersionCode()))
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
                .setManifest(
                    androidManifest(
                        "com.test.app",
                        withTypeAttribute(MODULE_TYPE_ASSET_VALUE),
                        withOnDemandDelivery(),
                        withFusingAttribute(true),
                        clearApplication()))
                .build());

    Throwable exception =
        assertThrows(
            ValidationException.class,
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
            ValidationException.class,
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
}
