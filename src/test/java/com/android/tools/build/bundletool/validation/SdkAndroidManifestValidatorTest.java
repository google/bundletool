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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_LOCATION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_GROUP_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_TREE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_ID_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallLocation;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withPermission;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withPermissionGroup;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withPermissionTree;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSdkLibraryElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSdkPatchVersionProperty;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class SdkAndroidManifestValidatorTest {

  private static final String BASE_MODULE_NAME = "base";
  private static final String PKG_NAME = "com.test.app";

  @Test
  public void manifest_withSdkLibraryElement_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME, withSdkLibraryElement(PKG_NAME, /* versionMajor= */ 13499)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "<"
                + SDK_LIBRARY_ELEMENT_NAME
                + "> cannot be declared in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withSdkPatchVersionProperty_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(
                androidManifest(PKG_NAME, withSdkPatchVersionProperty(/* patchVersion= */ 14)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "<"
                + PROPERTY_ELEMENT_NAME
                + "> cannot be declared with name='"
                + SDK_PATCH_VERSION_ATTRIBUTE_NAME
                + "' in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withPreferExternal_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withInstallLocation("preferExternal")))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "'"
                + INSTALL_LOCATION_ATTRIBUTE_NAME
                + "' in <manifest> must be 'internalOnly' for SDK bundles if it is set.");
  }

  @Test
  public void manifest_withPermission_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withPermission()))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "<"
                + PERMISSION_ELEMENT_NAME
                + "> cannot be declared in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withPermissionGroup_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withPermissionGroup()))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "<"
                + PERMISSION_GROUP_ELEMENT_NAME
                + "> cannot be declared in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withPermissionTree_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withPermissionTree()))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "<"
                + PERMISSION_TREE_ELEMENT_NAME
                + "> cannot be declared in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withSharedUserId_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withSharedUserId("sharedUserId")))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "'"
                + SHARED_USER_ID_ATTRIBUTE_NAME
                + "' attribute cannot be used in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withActivityComponent_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withMainActivity("myFunActivity")))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "None of <activity>, <service>, <provider>, or <receiver> can be declared in the"
                + " manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withServiceComponent_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(
                androidManifest(
                    PKG_NAME,
                    withSplitNameService("serviceName", "splitName")))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "None of <activity>, <service>, <provider>, or <receiver> can be declared in the"
                + " manifest of an SDK bundle.");
  }

  @Test
  public void manifest_withSplitId_throws() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME, withSplitId(BASE_MODULE_NAME)))
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkAndroidManifestValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .contains("'split' attribute cannot be used in the manifest of an SDK bundle.");
  }

  @Test
  public void manifest_valid_ok() {
    BundleModule module =
        new BundleModuleBuilder(BASE_MODULE_NAME).setManifest(androidManifest(PKG_NAME)).build();

    new SdkAndroidManifestValidator().validateModule(module);
  }
}
