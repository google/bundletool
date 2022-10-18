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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RuntimeEnabledSdkConfigValidatorTest {

  private static final String VALID_CERT_FINGERPRINT =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";

  @Test
  public void validateAllModules_noRuntimeEnabledSdkConfig_succeeds() {
    BundleModule module =
        new BundleModuleBuilder("module").setManifest(androidManifest("com.test.app")).build();

    new RuntimeEnabledSdkConfigValidator().validateAllModules(ImmutableList.of(module));
  }

  @Test
  public void validateAllModules_emptyRuntimeEnabledSdkConfig_succeeds() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(RuntimeEnabledSdkConfig.getDefaultInstance())
            .build();

    new RuntimeEnabledSdkConfigValidator().validateAllModules(ImmutableList.of(module));
  }

  @Test
  public void validateAllModules_validRuntimeEnabledSdkConfig_succeeds() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(1234)
                            .setVersionMinor(2345)
                            .setBuildTimeVersionPatch(3456)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    new RuntimeEnabledSdkConfigValidator().validateAllModules(ImmutableList.of(module));
  }

  @Test
  public void validateAllModules_sameSdkPackageNameMultipleTimes_throws() {
    RuntimeEnabledSdk runtimeEnabledSdk =
        RuntimeEnabledSdk.newBuilder()
            .setPackageName("com.test.sdk")
            .setVersionMajor(1234)
            .setCertificateDigest(VALID_CERT_FINGERPRINT)
            .setResourcesPackageId(2)
            .build();
    BundleModule module1 =
        new BundleModuleBuilder("module1")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(runtimeEnabledSdk)
                    .build())
            .build();
    BundleModule module2 =
        new BundleModuleBuilder("module2")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(runtimeEnabledSdk)
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module1, module2)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Found multiple dependencies on the same runtime-enabled SDK 'com.test.sdk'.");
  }

  @Test
  public void validateAllModules_missingPackageNameInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            // not setting package name.
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Found dependency on runtime-enabled SDK with an empty package name.");
  }

  @Test
  public void validateAllModules_emptyPackageNameInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            // setting emtpy package name.
                            .setPackageName("")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Found dependency on runtime-enabled SDK with an empty package name.");
  }

  @Test
  public void validateAllModules_negativeVersionMajorInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            // not setting major version.
                            .setPackageName("package.name.1")
                            .setVersionMajor(-1)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with a negative major"
                + " version.");
  }

  @Test
  public void validateAllModules_versionMajorTooBigInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(VERSION_MAJOR_MAX_VALUE + 1)
                            .setVersionMinor(0)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with illegal major version."
                + " Major version must be <= "
                + VERSION_MAJOR_MAX_VALUE);
  }

  @Test
  public void validateAllModules_negativeVersionMinorInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(0)
                            .setVersionMinor(-1)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with a negative minor"
                + " version.");
  }

  @Test
  public void validateAllModules_negativeVersionPatchInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(0)
                            .setVersionMinor(1)
                            .setBuildTimeVersionPatch(-1)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with a negative patch"
                + " version.");
  }

  @Test
  public void validateAllModules_versionMinorTooBigInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(0)
                            .setVersionMinor(VERSION_MINOR_MAX_VALUE + 1)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with illegal minor version."
                + " Minor version must be <= "
                + VERSION_MINOR_MAX_VALUE);
  }

  @Test
  public void validateAllModules_missingCertificateDigestInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            // not setting certificate digest.
                            .setPackageName("package.name.1")
                            .setVersionMajor(1234)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with a signing certificate"
                + " digest of unexpected format.");
  }

  @Test
  public void validateAllModules_badCertificateDigestFormatInRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            // setting certificate digest with bad format.
                            .setPackageName("package.name.1")
                            .setVersionMajor(1234)
                            .setCertificateDigest("abcd")
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(3))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependency on runtime-enabled SDK 'package.name.1' with a signing certificate"
                + " digest of unexpected format.");
  }

  @Test
  public void validateAllModules_illegalResourcesPackageId_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(-1))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Illegal value of resources_package_id in RuntimeEnabledSdkConfig for SDK"
                + " 'package.name.2': value must be an integer between 2 and 255, but was -1");
  }

  @Test
  public void validateAllModules_duplicateResourcesPackageIds_throws() {
    BundleModule module =
        new BundleModuleBuilder("module")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.1")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("package.name.2")
                            .setVersionMajor(1234)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT)
                            .setResourcesPackageId(2))
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkConfigValidator()
                    .validateAllModules(ImmutableList.of(module)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found dependencies on runtime-enabled SDKs 'package.name.1, package.name.2', which"
                + " specify the same 'resources_package_id' value 2. resources_package_id values"
                + " must be unique across all runtime-enabled SDK dependencies.");
  }
}
