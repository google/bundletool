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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemand;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkLessThanMinInstantSdk;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkGreaterThanMaxSdkException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
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
                    PKG_NAME, withInstant(true), withOnDemand(false), withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withFusingConfigFalse_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(true), withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withFusingConfigTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(true), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withoutFusingConfig_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(true)))
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
            .setManifest(androidManifest(PKG_NAME, withInstant(true), withOnDemand(false)))
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
            .setManifest(androidManifest(PKG_NAME, withOnDemand(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void base_withOnDemandAttributeSetToTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(true)))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains("The base module cannot be marked as onDemand='true'");
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
  public void nonBase_withOnDemandAttributeNotSet_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "The element <dist:module> in the AndroidManifest.xml must have the attribute "
                    + "'onDemand' explicitly set (module: '%s')",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void nonBase_withOnDemandAttributeSetToTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(true), withFusingAttribute(true)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void nonBase_withOnDemandAttributeSetToFalse_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withOnDemand(false), withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void onDemandAndInstantAttributeSetToTrue_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME, withOnDemand(true), withInstant(true), withFusingAttribute(true)))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "The attribute 'onDemand' and 'instant' cannot both be true"
                    + " at the same time (module '%s').",
                FEATURE_MODULE_NAME));
  }

  @Test
  public void onDemandSetToFalseAndInstantAttributeSetToTrue_ok() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME, withOnDemand(false), withInstant(true), withFusingAttribute(true)))
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
  public void conditionalModuleAndOnDemandAttribute_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withOnDemand(true),
                    withFusingAttribute(true),
                    withFeatureCondition("com.hardware.camera.ar")))
            .build();

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new AndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "The element <dist:module> in the AndroidManifest.xml must not have the attribute "
                    + "'onDemand' set if the module is conditional (module: '%s').",
                module.getName()));
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
            .setManifest(androidManifest(
                PKG_NAME,
                withInstant(true),
                withOnDemand(false),
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
            .setManifest(androidManifest(
                PKG_NAME,
                withInstant(true),
                withOnDemand(false),
                withFusingAttribute(false),
                withMaxSdkVersion(21)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  @Test
  public void instantFeature_noMaxSdk() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(FEATURE_MODULE_NAME)
            .setManifest(androidManifest(
                PKG_NAME,
                withInstant(true),
                withOnDemand(false),
                withFusingAttribute(false)))
            .build();

    new AndroidManifestValidator().validateModule(module);
  }

  private BundleModule baseModule(ManifestMutator... mutators) throws IOException {
    return new BundleModuleBuilder(BASE_MODULE_NAME)
        .setManifest(androidManifest(PKG_NAME, mutators))
        .build();
  }
}
