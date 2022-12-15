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
package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RuntimeEnabledSdkTableInjectorTest {

  private static final String VALID_CERT_FINGERPRINT =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";

  @Test
  public void appBundleHasNoSdkDependencies_noChange() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/apk1.apk")
                        .addFile("assets/apk2.apk")
                        .setManifest(androidManifest("com.app")))
            .addModule("feature", module -> module.setManifest(androidManifest("com.app")))
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result).isEqualTo(split);
  }

  @Test
  public void appBundleHasSdkDependencies_sdkRuntimeVariant_noChange() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/apk1.apk")
                        .addFile("assets/apk2.apk")
                        .setManifest(androidManifest("com.app"))
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(
                                    RuntimeEnabledSdk.newBuilder()
                                        .setPackageName("com.test.sdk1")
                                        .setVersionMajor(1)
                                        .setVersionMinor(2)
                                        .setCertificateDigest(VALID_CERT_FINGERPRINT))
                                .build()))
            .addModule("feature", module -> module.setManifest(androidManifest("com.app")))
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .setVariantTargeting(sdkRuntimeVariantTargeting())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result).isEqualTo(split);
  }

  @Test
  public void appBundleHasSdkDependencies_notMasterSplit_noChange() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/apk1.apk")
                        .addFile("assets/apk2.apk")
                        .setManifest(androidManifest("com.app"))
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(
                                    RuntimeEnabledSdk.newBuilder()
                                        .setPackageName("com.test.sdk1")
                                        .setVersionMajor(1)
                                        .setVersionMinor(2)
                                        .setCertificateDigest(VALID_CERT_FINGERPRINT))
                                .build()))
            .addModule("feature", module -> module.setManifest(androidManifest("com.app")))
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(false)
            .setSplitType(SplitType.SPLIT)
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result).isEqualTo(split);
  }

  @Test
  public void appBundleHasSdkDependencies_notBaseModule_noChange() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .addFile("assets/apk1.apk")
                        .addFile("assets/apk2.apk")
                        .setManifest(androidManifest("com.app"))
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(
                                    RuntimeEnabledSdk.newBuilder()
                                        .setPackageName("com.test.sdk1")
                                        .setVersionMajor(1)
                                        .setVersionMinor(2)
                                        .setCertificateDigest(VALID_CERT_FINGERPRINT))
                                .build()))
            .addModule("feature", module -> module.setManifest(androidManifest("com.app")))
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("notBaseModule"))
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result).isEqualTo(split);
  }

  @Test
  public void appBundleHasSdkDependencies_runtimeEnabledSdkTableAdded_splitVariant()
      throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("assets/apk1.apk")
            .addFile("assets/apk2.apk")
            .setManifest(androidManifest("com.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("com.test.sdk1")
                            .setVersionMajor(1)
                            .setVersionMinor(2)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT))
                    .build())
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .setManifest(androidManifest("com.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("com.test.sdk2")
                            .setVersionMajor(3)
                            .setVersionMinor(4)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT))
                    .build())
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(baseModule)
            .addModule(featureModule)
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result.getEntries()).hasSize(1);
    assertThat(Iterables.getOnlyElement(result.getEntries()).getPath())
        .isEqualTo(ZipPath.create("assets/RuntimeEnabledSdkTable.xml"));
    assertThat(
            Iterables.getOnlyElement(result.getEntries())
                .getContent()
                .asCharSource(UTF_8)
                .readLines())
        .containsExactly(
            "<runtime-enabled-sdk-table>",
            "  <runtime-enabled-sdk>",
            "    <package-name>com.test.sdk1</package-name>",
            "    <compat-config-path>RuntimeEnabledSdk-com.test.sdk1/CompatSdkConfig.xml</compat-config-path>",
            "  </runtime-enabled-sdk>",
            "  <runtime-enabled-sdk>",
            "    <package-name>com.test.sdk2</package-name>",
            "    <compat-config-path>RuntimeEnabledSdk-com.test.sdk2/CompatSdkConfig.xml</compat-config-path>",
            "  </runtime-enabled-sdk>",
            "</runtime-enabled-sdk-table>");
  }

  @Test
  public void appBundleHasSdkDependencies_runtimeEnabledSdkTableAdded_standaloneVariant()
      throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("assets/apk1.apk")
            .addFile("assets/apk2.apk")
            .setManifest(androidManifest("com.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("com.test.sdk1")
                            .setVersionMajor(1)
                            .setVersionMinor(2)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT))
                    .build())
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .setManifest(androidManifest("com.app"))
            .setRuntimeEnabledSdkConfig(
                RuntimeEnabledSdkConfig.newBuilder()
                    .addRuntimeEnabledSdk(
                        RuntimeEnabledSdk.newBuilder()
                            .setPackageName("com.test.sdk2")
                            .setVersionMajor(3)
                            .setVersionMinor(4)
                            .setCertificateDigest(VALID_CERT_FINGERPRINT))
                    .build())
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(baseModule)
            .addModule(featureModule)
            .setBundleConfig(BundleConfig.getDefaultInstance())
            .build();
    RuntimeEnabledSdkTableInjector injector = new RuntimeEnabledSdkTableInjector(appBundle);
    ModuleSplit split =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setMasterSplit(false)
            .setSplitType(SplitType.STANDALONE)
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ModuleSplit result = injector.inject(split);

    assertThat(result.getEntries()).hasSize(1);
    assertThat(Iterables.getOnlyElement(result.getEntries()).getPath())
        .isEqualTo(ZipPath.create("assets/RuntimeEnabledSdkTable.xml"));
    assertThat(
            Iterables.getOnlyElement(result.getEntries())
                .getContent()
                .asCharSource(UTF_8)
                .readLines())
        .containsExactly(
            "<runtime-enabled-sdk-table>",
            "  <runtime-enabled-sdk>",
            "    <package-name>com.test.sdk1</package-name>",
            "    <compat-config-path>RuntimeEnabledSdk-com.test.sdk1/CompatSdkConfig.xml</compat-config-path>",
            "  </runtime-enabled-sdk>",
            "  <runtime-enabled-sdk>",
            "    <package-name>com.test.sdk2</package-name>",
            "    <compat-config-path>RuntimeEnabledSdk-com.test.sdk2/CompatSdkConfig.xml</compat-config-path>",
            "  </runtime-enabled-sdk>",
            "</runtime-enabled-sdk-table>");
  }

  private static ModuleSplit.Builder getModuleSplitBuilder() {
    return ModuleSplit.builder()
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setAndroidManifest(
            AndroidManifest.create(androidManifest("com.test.app"))
                .toEditor()
                .setMinSdkVersion(28)
                .save());
  }
}
