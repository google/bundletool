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

import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MAJOR_MAX_VALUE;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MINOR_MAX_VALUE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.SdkBundleConfigProto.SdkBundle;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkBundleConfigValidatorTest {

  private static final String VALID_CERT_FINGERPRINT =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";

  @Test
  public void validSdkBundleConfig_ok() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk2",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ 34,
                VALID_CERT_FINGERPRINT));

    new SdkBundleConfigValidator().validateSdkBundleConfig(sdkBundleConfig);
  }

  @Test
  public void emptyPackageName_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ 34,
                VALID_CERT_FINGERPRINT));

    assertIsInvalid(
        sdkBundleConfig,
        "Package names of SDK dependencies in the SdkBundleConfig file cannot be empty.");
  }

  @Test
  public void duplicatePackageName_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk1",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ 34,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk3",
                /* versionMajor= */ 1,
                /* versionMinor= */ 2,
                /* versionPatch= */ 3,
                VALID_CERT_FINGERPRINT));

    assertIsInvalid(
        sdkBundleConfig,
        "SDK dependencies in the SdkBundleConfig file have repeated package names: [sdk1].");
  }

  @Test
  public void invalidMajorVersion_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ VERSION_MAJOR_MAX_VALUE + 1,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk2",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ 34,
                VALID_CERT_FINGERPRINT));

    assertIsInvalid(
        sdkBundleConfig,
        String.format(
            "SDK major version for dependency 'sdk1' must be an integer between 0 and %d.",
            VERSION_MAJOR_MAX_VALUE));
  }

  @Test
  public void invalidMinorVersion_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk2",
                /* versionMajor= */ 12,
                /* versionMinor= */ VERSION_MINOR_MAX_VALUE + 1,
                /* versionPatch= */ 34,
                VALID_CERT_FINGERPRINT));

    assertIsInvalid(
        sdkBundleConfig,
        String.format(
            "SDK minor version for dependency 'sdk2' must be an integer between 0 and %d.",
            VERSION_MINOR_MAX_VALUE));
  }

  @Test
  public void invalidPatchVersion_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk2",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ -1,
                VALID_CERT_FINGERPRINT));

    assertIsInvalid(sdkBundleConfig, "SDK patch version must be a non-negative integer.");
  }

  @Test
  public void invalidCertificate_throws() {
    SdkBundleConfig sdkBundleConfig =
        sdkBundleConfig(
            dependency(
                "sdk1",
                /* versionMajor= */ 123,
                /* versionMinor= */ 234,
                /* versionPatch= */ 345,
                VALID_CERT_FINGERPRINT),
            dependency(
                "sdk2",
                /* versionMajor= */ 12,
                /* versionMinor= */ 23,
                /* versionPatch= */ 34,
                "invalid_cert"));

    assertIsInvalid(
        sdkBundleConfig, "Certificate digest for dependency 'sdk2' has an invalid format.");
  }

  private SdkBundleConfig sdkBundleConfig(SdkBundle... dependencies) {
    return SdkBundleConfig.newBuilder()
        .addAllSdkDependencies(ImmutableList.copyOf(dependencies))
        .build();
  }

  private SdkBundle dependency(
      String packageName,
      int versionMajor,
      int versionMinor,
      int versionPatch,
      String certificate) {
    return SdkBundle.newBuilder()
        .setPackageName(packageName)
        .setVersionMajor(versionMajor)
        .setVersionMinor(versionMinor)
        .setBuildTimeVersionPatch(versionPatch)
        .setCertificateDigest(certificate)
        .build();
  }

  private void assertIsInvalid(SdkBundleConfig sdkBundleConfig, String message) {
    InvalidBundleException invalidBundleException =
        assertThrows(
            InvalidBundleException.class,
            () -> new SdkBundleConfigValidator().validateSdkBundleConfig(sdkBundleConfig));

    assertThat(invalidBundleException.getUserMessage()).isEqualTo(message);
  }
}
