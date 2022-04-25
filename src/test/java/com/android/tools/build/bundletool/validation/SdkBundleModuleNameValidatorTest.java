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

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkBundleModuleNameValidatorTest {

  @Test
  public void nonBaseModuleName_throws() {
    BundleModule base =
        BundleModule.builder()
            .setName(BundleModuleName.create("invalidModuleName"))
            .setBundleType(BundleType.REGULAR)
            .setBundletoolVersion(BundleToolVersion.getCurrentVersion())
            .setAndroidManifestProto(androidManifest("com.foo.bar"))
            .build();

    InvalidBundleException expected =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkBundleModuleNameValidator().validateModule(base));

    assertThat(expected)
        .hasMessageThat()
        .contains("The SDK bundle module must be named '" + BASE_MODULE_NAME + "'");
  }

  @Test
  public void baseModuleName_ok() {
    BundleModule base =
        BundleModule.builder()
            .setName(BASE_MODULE_NAME)
            .setBundleType(BundleType.REGULAR)
            .setBundletoolVersion(BundleToolVersion.getCurrentVersion())
            .setAndroidManifestProto(androidManifest("com.foo.bar"))
            .build();

    new SdkBundleModuleNameValidator().validateModule(base);
  }
}
