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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.ModuleSplit.DEFAULT_SDK_PATCH_VERSION;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromSdkApkSetFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSdkLibraryElement;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.DEFAULT_BUNDLE_CONFIG;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.PACKAGE_NAME;
import static com.android.tools.build.bundletool.testing.TestUtils.addKeyToKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.extractAndroidManifest;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.apksig.ApkVerifier;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.SdkVersionInformation;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.io.ZipReader;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class BuildSdkApksManagerTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";
  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path sdkBundlePath;
  private Path outputFilePath;
  private Path keystorePath;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildSdkApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkBundlePath = tmpDir.resolve("SdkBundle.asb");
    outputFilePath = tmpDir.resolve("output.apks");

    // Keystore.
    keystorePath = tmpDir.resolve("keystore.jks");
    createKeystore(keystorePath, KEYSTORE_PASSWORD);
    addKeyToKeystore(
        keystorePath, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, privateKey, certificate);
  }

  @Test
  public void tocIsCorrect() throws Exception {
    execute(new SdkBundleBuilder().build());
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    assertThat(result.getVariantCount()).isEqualTo(1);
    assertThat(result.getPackageName()).isEqualTo(PACKAGE_NAME);
    assertThat(result.getBundletool().getVersion())
        .isEqualTo(DEFAULT_BUNDLE_CONFIG.getBundletool().getVersion());
  }

  @Test
  public void manifestIsMutated() throws Exception {
    Integer versionCode = 1253;
    execute(new SdkBundleBuilder().setVersionCode(versionCode).build());
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    Variant variant = result.getVariant(0);

    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);

    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);
    AndroidManifest manifest = extractAndroidManifest(apkFile, tmpDir);

    assertThat(manifest.getMinSdkVersion()).hasValue(SDK_SANDBOX_MIN_VERSION);
    assertThat(
            manifest
                .getManifestRoot()
                .getElement()
                .getAndroidAttribute(VERSION_NAME_RESOURCE_ID)
                .get()
                .getValueAsString())
        .isEqualTo("15.0.5");
    assertThat(
            manifest
                .getManifestRoot()
                .getElement()
                .getAndroidAttribute(VERSION_CODE_RESOURCE_ID)
                .get()
                .getValueAsDecimalInteger())
        .isEqualTo(versionCode);
  }

  @Test
  public void sdkManifestMutation_highMinSdkVersion_minSdkVersionUnchanged() throws Exception {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest(
                            PACKAGE_NAME,
                            withMinSdkVersion(SDK_SANDBOX_MIN_VERSION + 5),
                            withSdkLibraryElement("20"),
                            withMetadataValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME, "12")))
                    .build())
            .build();

    execute(sdkBundle);
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    Variant variant = result.getVariant(0);

    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);

    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);
    AndroidManifest manifest = extractAndroidManifest(apkFile, tmpDir);

    assertThat(manifest.getMinSdkVersion()).hasValue(SDK_SANDBOX_MIN_VERSION + 5);
  }

  @Test
  public void sdkManifestMutation_patchVersionNotSet_defaultPatchVersionAdded() throws Exception {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest(PACKAGE_NAME, withSdkLibraryElement("20")))
                    .build())
            .build();

    execute(sdkBundle);

    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);
    Variant variant = result.getVariant(0);
    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);
    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);
    AndroidManifest manifest = extractAndroidManifest(apkFile, tmpDir);

    assertThat(manifest.getMetadataValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME))
        .hasValue(DEFAULT_SDK_PATCH_VERSION);
  }

  @Test
  public void apksSigned() throws Exception {
    execute(new SdkBundleBuilder().build());
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    Variant variant = result.getVariant(0);
    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);
    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);

    ApkVerifier.Result verifierResult = new ApkVerifier.Builder(apkFile).build().verify();
    assertThat(verifierResult.isVerified()).isTrue();
    assertThat(verifierResult.getSignerCertificates()).containsExactly(certificate);
  }

  @Test
  public void sdkVersionInformationIsSet() throws Exception {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest(
                            PACKAGE_NAME,
                            withSdkLibraryElement("100"),
                            withMetadataValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME, "132")))
                    .build())
            .setVersionCode(99)
            .build();

    execute(sdkBundle);
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    SdkVersionInformation version = result.getVersion();

    assertThat(version.getVersionCode()).isEqualTo(99);
    assertThat(version.getMajor()).isEqualTo(100);
    assertThat(version.getPatch()).isEqualTo(132);
  }

  @Test
  public void sdkPatchVersionIsSetToDefaultValue() throws Exception {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest(PACKAGE_NAME, withSdkLibraryElement("1181894")))
                    .build())
            .build();

    execute(sdkBundle);
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    SdkVersionInformation version = result.getVersion();

    assertThat(version.getPatch()).isEqualTo(Long.parseLong(DEFAULT_SDK_PATCH_VERSION));
  }

  private BuildSdkApksCommand createCommand() {
    return BuildSdkApksCommand.fromFlags(
        new FlagParser()
            .parse(
                "--sdk-bundle=" + sdkBundlePath,
                "--output=" + outputFilePath,
                "--ks=" + keystorePath,
                "--ks-key-alias=" + KEY_ALIAS,
                "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                "--key-pass=pass:" + KEY_PASSWORD));
  }

  private void execute(SdkBundle sdkBundle) throws Exception {
    new SdkBundleSerializer().writeToDisk(sdkBundle, sdkBundlePath);

    try (ZipReader zipReader = ZipReader.createFromFile(sdkBundlePath)) {
      DaggerBuildSdkApksManagerComponent.builder()
          .setBuildSdkApksCommand(createCommand())
          .setTempDirectory(new TempDirectory(getClass().getSimpleName()))
          .setSdkBundle(sdkBundle)
          .setZipReader(zipReader)
          .setUseBundleCompression(false)
          .build()
          .create()
          .execute();
    }
  }
}
