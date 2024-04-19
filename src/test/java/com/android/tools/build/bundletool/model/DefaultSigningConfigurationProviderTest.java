/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_R_API_VERSION;
import static com.android.tools.build.bundletool.testing.CertificateFactory.buildSelfSignedCertificate;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.google.common.truth.Truth.assertThat;

import com.android.apksig.SigningCertificateLineage;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider.ApkDescription;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.protobuf.Int32Value;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultSigningConfigurationProviderTest {

  private static SignerConfig signerConfig;
  private static SignerConfig oldSignerConfig;
  private static SigningCertificateLineage lineage;
  private static SigningConfiguration signingConfig;
  private static SigningConfiguration signingConfigForV3Rotation;

  @BeforeClass
  public static void setUp() throws Exception {
    oldSignerConfig = createSignerConfig("CN=DefaultSigningConfigurationProviderTest oldSigner");
    signerConfig = createSignerConfig("CN=DefaultSigningConfigurationProviderTest newSigner");
    lineage =
        new SigningCertificateLineage.Builder(
                convertToApksigSignerConfig(oldSignerConfig),
                convertToApksigSignerConfig(signerConfig))
            .build();
    signingConfig = SigningConfiguration.builder().setSignerConfig(signerConfig).build();
    signingConfigForV3Rotation =
        SigningConfiguration.builder()
            .setSignerConfig(signerConfig)
            .setOldestSigner(oldSignerConfig)
            .setSigningCertificateLineage(lineage)
            .build();
  }

  @Test
  public void minSdkM_shouldSignWithV1() throws Exception {
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(ANDROID_M_API_VERSION);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getV1SigningEnabled()).isTrue();
  }

  @Test
  public void minSdkN_shouldNotSignWithV1() throws Exception {
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(ANDROID_N_API_VERSION);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getV1SigningEnabled()).isFalse();
  }

  @Test
  public void noV3SigningRestriction_shouldSignWithV3() throws Exception {
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfigForV3Rotation, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(1);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs())
        .containsExactly(oldSignerConfig, signerConfig)
        .inOrder();
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).hasValue(lineage);
  }

  @Test
  public void v3SigningRestrictedToRPlus_minSdkRPlus_shouldSignWithV3Rotation() throws Exception {
    SigningConfiguration signingConfig =
        signingConfigForV3Rotation.toBuilder()
            .setMinimumV3RotationApiVersion(Optional.of(ANDROID_R_API_VERSION))
            .build();
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(ANDROID_R_API_VERSION);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs())
        .containsExactly(oldSignerConfig, signerConfig)
        .inOrder();
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).hasValue(lineage);
  }

  @Test
  public void v3SigningRestrictedToRPlus_minSdkpreR_shouldNotSignWithV3Rotation() throws Exception {
    SigningConfiguration signingConfig =
        signingConfigForV3Rotation.toBuilder()
            .setMinimumV3RotationApiVersion(Optional.of(ANDROID_R_API_VERSION))
            .build();
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(ANDROID_Q_API_VERSION);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs()).containsExactly(oldSignerConfig);
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).isEmpty();
  }

  @Test
  public void v3SigningRestrictedToRPlus_apkTargetingRPlus_shouldSignWithV3Rotation()
      throws Exception {
    SigningConfiguration signingConfig =
        signingConfigForV3Rotation.toBuilder()
            .setMinimumV3RotationApiVersion(Optional.of(ANDROID_R_API_VERSION))
            .build();
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split =
        createModuleSplitWithMinSdk(ANDROID_Q_API_VERSION).toBuilder()
            .setApkTargeting(createSdkVersionTargeting(ANDROID_R_API_VERSION))
            .build();

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs())
        .containsExactly(oldSignerConfig, signerConfig)
        .inOrder();
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).hasValue(lineage);
  }

  @Test
  public void v3SigningRestrictedToRPlus_apkTargetingPreR_shouldNotSignWithV3Rotation()
      throws Exception {
    SigningConfiguration signingConfig =
        signingConfigForV3Rotation.toBuilder()
            .setMinimumV3RotationApiVersion(Optional.of(ANDROID_R_API_VERSION))
            .build();
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split = createModuleSplitWithMinSdk(ANDROID_Q_API_VERSION);

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs()).containsExactly(oldSignerConfig);
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).isEmpty();
  }

  @Test
  public void v3SigningRestrictedToRPlus_variantTargetingRPlus_shouldSignWithV3Rotation() {
    SigningConfiguration signingConfig =
        signingConfigForV3Rotation.toBuilder()
            .setMinimumV3RotationApiVersion(Optional.of(ANDROID_R_API_VERSION))
            .build();
    SigningConfigurationProvider signingConfigProvider =
        new DefaultSigningConfigurationProvider(
            signingConfig, BundleToolVersion.getCurrentVersion());
    ModuleSplit split =
        createModuleSplitWithMinSdk(ANDROID_Q_API_VERSION).toBuilder()
            .setVariantTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(
                                SdkVersion.newBuilder()
                                    .setMin(
                                        Int32Value.newBuilder().setValue(ANDROID_R_API_VERSION))))
                    .build())
            .build();

    ApksigSigningConfiguration apksigSigningConfig =
        signingConfigProvider.getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    assertThat(apksigSigningConfig.getSignerConfigs())
        .containsExactly(oldSignerConfig, signerConfig)
        .inOrder();
    assertThat(apksigSigningConfig.getSigningCertificateLineage()).hasValue(lineage);
  }

  private static ModuleSplit createModuleSplitWithMinSdk(int minSdkVersion) {
    return createModuleSplit().toBuilder()
        .setAndroidManifest(
            AndroidManifest.create(androidManifest("com.app", withMinSdkVersion(minSdkVersion))))
        .build();
  }

  private static ModuleSplit createModuleSplit() {
    return ModuleSplit.builder()
        .setModuleName(BundleModuleName.create("base"))
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .setMasterSplit(true)
        .build();
  }

  private static ApkTargeting createSdkVersionTargeting(int minSdkVersion) {
    return ApkTargeting.newBuilder()
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder()
                .addValue(
                    SdkVersion.newBuilder()
                        .setMin(Int32Value.newBuilder().setValue(minSdkVersion))))
        .build();
  }

  private static SignerConfig createSignerConfig(String distinguishedName) throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    X509Certificate oldCertificate = buildSelfSignedCertificate(keyPair, distinguishedName);
    return SignerConfig.builder()
        .setPrivateKey(privateKey)
        .setCertificates(ImmutableList.of(oldCertificate))
        .build();
  }

  private static SigningCertificateLineage.SignerConfig convertToApksigSignerConfig(
      SignerConfig signerConfig) {
    return new SigningCertificateLineage.SignerConfig.Builder(
            signerConfig.getPrivateKey(), Iterables.getOnlyElement(signerConfig.getCertificates()))
        .build();
  }
}
