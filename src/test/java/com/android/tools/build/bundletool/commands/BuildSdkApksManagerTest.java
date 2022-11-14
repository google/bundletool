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

import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_MAJOR_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractFromApkSetFile;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromSdkApkSetFile;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.DEFAULT_SDK_MODULES_CONFIG;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.PACKAGE_NAME;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.createSdkModulesConfig;
import static com.android.tools.build.bundletool.testing.TestUtils.addKeyToKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.extractAndroidManifest;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

import com.android.apksig.ApkVerifier;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.SdkVersionInformation;
import com.android.bundle.Commands.Variant;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Int32Value;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;
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
    assertThat(result.getVariant(0).getTargeting())
        .isEqualTo(
            VariantTargeting.newBuilder()
                .setSdkRuntimeTargeting(
                    SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
                .setSdkVersionTargeting(
                    SdkVersionTargeting.newBuilder()
                        .addValue(
                            SdkVersion.newBuilder().setMin(Int32Value.of(SDK_SANDBOX_MIN_VERSION))))
                .build());
    assertThat(result.getPackageName()).isEqualTo(PACKAGE_NAME);
    assertThat(result.getBundletool().getVersion())
        .isEqualTo(DEFAULT_SDK_MODULES_CONFIG.getBundletool().getVersion());
  }

  @Test
  public void manifestIsMutated() throws Exception {
    Integer versionCode = 1253;
    String packageName = "com.ads.foo";
    int major = 15;
    int minor = 0;
    int patch = 5;
    String sdkProviderClassName = "com.example.sandboxservice.MyNewAdsSdkEntryPoint";
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setVersionCode(versionCode)
            .setSdkModulesConfig(
                createSdkModulesConfig()
                    .setSdkPackageName(packageName)
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder()
                            .setMajor(major)
                            .setMinor(minor)
                            .setPatch(patch))
                    .setSdkProviderClassName(sdkProviderClassName)
                    .build())
            .build();
    execute(sdkBundle);
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    Variant variant = result.getVariant(0);

    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);

    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);
    AndroidManifest manifest = extractAndroidManifest(apkFile, tmpDir);

    // <manifest> mutations.
    assertThat(manifest.getPackageName())
        .isEqualTo(
            packageName
                + "_"
                + RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(major, minor));
    assertThat(
            manifest
                .getManifestRoot()
                .getElement()
                .getAndroidAttribute(VERSION_NAME_RESOURCE_ID)
                .get()
                .getValueAsString())
        .isEqualTo(major + "." + minor + "." + patch);
    assertThat(
            manifest
                .getManifestRoot()
                .getElement()
                .getAndroidAttribute(VERSION_CODE_RESOURCE_ID)
                .get()
                .getValueAsDecimalInteger())
        .isEqualTo(versionCode);

    // <uses-sdk> mutations.
    assertThat(manifest.getMinSdkVersion()).hasValue(SDK_SANDBOX_MIN_VERSION);

    // <sdk-library> mutations.
    assertThat(
            manifest
                .getSdkLibraryElements()
                .get(0)
                .getAndroidAttribute(NAME_RESOURCE_ID)
                .get()
                .getValueAsString())
        .isEqualTo(packageName);
    assertThat(
            manifest
                .getSdkLibraryElements()
                .get(0)
                .getAndroidAttribute(VERSION_MAJOR_RESOURCE_ID)
                .get()
                .getValueAsDecimalInteger())
        .isEqualTo(RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(major, minor));

    // <property> mutations.
    assertThat(manifest.getSdkPatchVersionProperty()).hasValue(patch);
    assertThat(manifest.getSdkProviderClassNameProperty()).hasValue(sdkProviderClassName);
    assertThat(manifest.getCompatSdkProviderClassNameProperty()).isEmpty();
  }

  @Test
  public void sdkManifestMutation_highMinSdkVersion_minSdkVersionUnchanged() throws Exception {
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest(
                            PACKAGE_NAME, withMinSdkVersion(SDK_SANDBOX_MIN_VERSION + 5)))
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
  public void sdkManifestMutation_compatSdkProviderClassNameSet() throws Exception {
    String sdkProviderClassName = "com.example.sandboxservice.MyNewAdsSdkEntryPoint";
    String compatSdkProviderClassName = "com.example.MyNewAdsSdkCompatEntryPoint";
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setVersionCode(1253)
            .setSdkModulesConfig(
                createSdkModulesConfig()
                    .setSdkPackageName("com.ads.foo")
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder().setMajor(15).setMinor(0).setPatch(5))
                    .setSdkProviderClassName(sdkProviderClassName)
                    .setCompatSdkProviderClassName(compatSdkProviderClassName)
                    .build())
            .build();

    execute(sdkBundle);

    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);
    Variant variant = result.getVariant(0);
    ApkDescription apkDescription = variant.getApkSet(0).getApkDescription(0);
    File apkFile = extractFromApkSetFile(apkSetFile, apkDescription.getPath(), tmpDir);
    AndroidManifest manifest = extractAndroidManifest(apkFile, tmpDir);
    ZipFile apkZip = new ZipFile(apkFile);

    assertThat(manifest.getSdkProviderClassNameProperty()).hasValue(sdkProviderClassName);
    assertThat(manifest.getCompatSdkProviderClassNameProperty())
        .hasValue(compatSdkProviderClassName);
    String compatSdkProviderClassNameFromFile =
        ZipUtils.asByteSource(
                apkZip, apkZip.getEntry("assets/SandboxedSdkProviderCompatClassName.txt"))
            .asCharSource(UTF_8)
            .read();
    assertThat(compatSdkProviderClassNameFromFile).isEqualTo(compatSdkProviderClassName);
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
    Integer versionCode = 1253;
    int major = 15;
    int minor = 0;
    int patch = 5;
    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setVersionCode(versionCode)
            .setSdkModulesConfig(
                createSdkModulesConfig()
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder()
                            .setMajor(major)
                            .setMinor(minor)
                            .setPatch(patch))
                    .build())
            .build();

    execute(sdkBundle);
    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    BuildSdkApksResult result = extractTocFromSdkApkSetFile(apkSetFile, tmpDir);

    SdkVersionInformation version = result.getVersion();

    assertThat(version.getVersionCode()).isEqualTo(versionCode);
    assertThat(version.getMajor()).isEqualTo(major);
    assertThat(version.getMinor()).isEqualTo(minor);
    assertThat(version.getPatch()).isEqualTo(patch);
  }

  @Test
  public void overwriteFlagOn_fileOverwritten() throws Exception {
    Files.createFile(outputFilePath);

    execute(new SdkBundleBuilder().build(), createCommand("--overwrite"));

    assertThat(outputFilePath.toFile().length()).isGreaterThan(0L);
  }

  private void execute(SdkBundle sdkBundle) throws Exception {
    execute(sdkBundle, createCommand());
  }

  private void execute(SdkBundle sdkBundle, BuildSdkApksCommand command) throws Exception {
    new SdkBundleSerializer().writeToDisk(sdkBundle, sdkBundlePath);

    DaggerBuildSdkApksManagerComponent.builder()
        .setBuildSdkApksCommand(command)
        .setTempDirectory(new TempDirectory(getClass().getSimpleName()))
        .setSdkBundle(sdkBundle)
        .build()
        .create()
        .execute();
  }

  private BuildSdkApksCommand createCommand(String... additionalFlags) {
    String[] flags =
        Stream.concat(getDefaultFlagList().stream(), stream(additionalFlags))
            .toArray(String[]::new);
    return BuildSdkApksCommand.fromFlags(new FlagParser().parse(flags));
  }

  private ImmutableList<String> getDefaultFlagList() {
    return ImmutableList.of(
        "--sdk-bundle=" + sdkBundlePath,
        "--output=" + outputFilePath,
        "--ks=" + keystorePath,
        "--ks-key-alias=" + KEY_ALIAS,
        "--ks-pass=pass:" + KEYSTORE_PASSWORD,
        "--key-pass=pass:" + KEY_PASSWORD);
  }
}
