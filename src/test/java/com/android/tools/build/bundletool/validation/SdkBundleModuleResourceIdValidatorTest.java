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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.validation.SdkBundleModuleResourceIdValidator.SDK_BUNDLE_PACKAGE_ID;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkBundleModuleResourceIdValidatorTest {

  @Test
  public void resourceWithValidPackageId_ok() {
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        SDK_BUNDLE_PACKAGE_ID,
                        "com.test.sdk",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.sdk"))
            .build();

    new SdkBundleModuleResourceIdValidator().validateModule(module);
  }

  @Test
  public void resourceWithInvalidPackageId_throws() {
    int invalidPackageId = 0x00;
    BundleModule module =
        new BundleModuleBuilder("base")
            .setResourceTable(
                resourceTable(
                    pkg(
                        invalidPackageId,
                        "com.test.sdk",
                        type(0x01, "drawable", entry(0x0002, "logo")))))
            .setManifest(androidManifest("com.test.sdk"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkBundleModuleResourceIdValidator().validateModule(module));

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "SDK Bundle Resource IDs must be in the "
                + Integer.toHexString(SDK_BUNDLE_PACKAGE_ID)
                + " space.");
  }
}
