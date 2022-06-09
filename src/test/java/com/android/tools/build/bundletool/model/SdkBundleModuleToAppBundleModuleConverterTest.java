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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SdkBundleModuleToAppBundleModuleConverter}. */
@RunWith(JUnit4.class)
public final class SdkBundleModuleToAppBundleModuleConverterTest {

  private static final String PACKAGE_NAME = "com.test.sdk";

  @Test
  public void convert_modifiesModuleName_modifiesManifest_setsIsSdkDependencyModule() {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
                    .build())
            .build();

    BundleModule modifiedModule =
        SdkBundleModuleToAppBundleModuleConverter.getAppBundleModule(
            sdkBundle, RuntimeEnabledSdk.getDefaultInstance());

    // Verify that module name was modified.
    assertThat(modifiedModule.getName()).isNotEqualTo(sdkBundle.getModule().getName());
    assertThat(modifiedModule.getName().getName()).isEqualTo("comtestsdk");

    // Verify the manifest.
    AndroidManifest androidManifest = modifiedModule.getAndroidManifest();
    assertThat(androidManifest.getIsFeatureSplit()).hasValue(true);
    assertThat(androidManifest.getSplitId()).hasValue("comtestsdk");
    assertThat(androidManifest.getIsModuleIncludedInFusing()).hasValue(true);
    assertThat(
            androidManifest
                .getManifestElement()
                .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module"))
        .isPresent();

    // Verify that module type is updated.
    assertThat(modifiedModule.getModuleType()).isEqualTo(ModuleType.SDK_DEPENDENCY_MODULE);
  }

  @Test
  public void convert_remapsResourceIdsInResourceTable() {
    int originalResourcesPackageId = 1;
    int newResourcesPackageId = 2;
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest(PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION)))
                    .setResourceTable(
                        ResourceTable.newBuilder()
                            .addPackage(
                                Package.newBuilder()
                                    .setPackageId(
                                        PackageId.newBuilder().setId(originalResourcesPackageId)))
                            .build())
                    .build())
            .build();

    BundleModule modifiedModule =
        SdkBundleModuleToAppBundleModuleConverter.getAppBundleModule(
            sdkBundle,
            RuntimeEnabledSdk.newBuilder().setResourcesPackageId(newResourcesPackageId).build());

    assertThat(modifiedModule.getResourceTable().get().getPackage(0).getPackageId().getId())
        .isEqualTo(newResourcesPackageId);
  }
}
