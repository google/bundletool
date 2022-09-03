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

import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.testing.AsarUtils.extractSdkInterfaceDescriptors;
import static com.android.tools.build.bundletool.testing.AsarUtils.extractSdkManifest;
import static com.android.tools.build.bundletool.testing.AsarUtils.extractSdkMetadata;
import static com.android.tools.build.bundletool.testing.AsarUtils.extractSdkModuleData;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.createSdkModulesConfig;
import static com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils.getCertificateFingerprint;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.Arrays.stream;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

/** Tests for {@link BuildSdkAsarManager}. */
@RunWith(Theories.class)
public class BuildSdkAsarManagerTest {

  private static final String TEST_PACKAGE_NAME = "com.ads.foo";

  private static X509Certificate apkSigningKeyCertificate;

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path sdkBundlePath;
  private Path modulesPath;
  private Path outputFilePath;
  private Path apkSigningKeyCertificatePath;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so create a single key for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair apkSigningKeyPair = kpg.genKeyPair();
    apkSigningKeyCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            apkSigningKeyPair, "CN=BuildSdkAsarManagerTest_ApkSigningKey");
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkBundlePath = tmpDir.resolve("SdkBundle.asb");
    modulesPath = tmpDir.resolve("extracted-modules.resm");
    outputFilePath = tmpDir.resolve("output.asar");
    apkSigningKeyCertificatePath = tmpDir.resolve("apk-signing-key-public.cert");
    Files.write(apkSigningKeyCertificatePath, apkSigningKeyCertificate.getEncoded());
  }

  @Test
  public void moduleIsCopiedToAsar() throws Exception {
    SdkBundle sdkBundle = new SdkBundleBuilder().build();

    execute(sdkBundle);

    ZipFile asarFile = new ZipFile(outputFilePath.toFile());
    byte[] moduleData = extractSdkModuleData(asarFile);
    assertThat(moduleData).isEqualTo(Files.readAllBytes(modulesPath));
  }

  @Test
  public void manifestIsCopiedToAsar() throws Exception {
    XmlNode sdkManifest = androidManifest(TEST_PACKAGE_NAME, withMinSdkVersion(32));

    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setModule(new BundleModuleBuilder("base").setManifest(sdkManifest).build())
            .build();

    execute(sdkBundle);

    ZipFile asarFile = new ZipFile(outputFilePath.toFile());
    String asarManifest = extractSdkManifest(asarFile);

    assertThat(asarManifest)
        .isEqualTo(
            XmlUtils.documentToString(
                XmlProtoToXmlConverter.convert(new XmlProtoNode(sdkManifest))));
  }

  @Test
  public void sdkInterfaceDescriptorsAreCopiedToAsar() throws Exception {
    ByteSource apiDescriptors = ByteSource.wrap(new byte[] {1, 2, 3});
    SdkBundle sdkBundle = new SdkBundleBuilder().setSdkInterfaceDescriptors(apiDescriptors).build();

    execute(sdkBundle);

    ZipFile asarFile = new ZipFile(outputFilePath.toFile());
    assertThat(extractSdkInterfaceDescriptors(asarFile)).isEqualTo(apiDescriptors.read());
  }

  @Test
  public void sdkVersionInformationIsSet() throws Exception {
    int major = 15;
    int minor = 0;
    int patch = 5;

    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setSdkModulesConfig(
                createSdkModulesConfig()
                    .setSdkPackageName(TEST_PACKAGE_NAME)
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder()
                            .setMajor(major)
                            .setMinor(minor)
                            .setPatch(patch))
                    .build())
            .build();

    execute(sdkBundle);

    ZipFile asarFile = new ZipFile(outputFilePath.toFile());
    SdkMetadata asarSdkMetadata = extractSdkMetadata(asarFile);

    assertThat(asarSdkMetadata)
        .isEqualTo(
            SdkMetadata.newBuilder()
                .setPackageName(TEST_PACKAGE_NAME)
                .setSdkVersion(
                    RuntimeEnabledSdkVersion.newBuilder()
                        .setMajor(major)
                        .setMinor(minor)
                        .setPatch(patch))
                .setCertificateDigest(
                    getCertificateFingerprint(apkSigningKeyCertificate).replace(' ', ':'))
                .build());
  }

  @Test
  public void certificateDigestDefaultsToEmpty() throws Exception {
    int major = 15;
    int minor = 0;
    int patch = 5;

    SdkBundle sdkBundle =
        new SdkBundleBuilder()
            .setSdkModulesConfig(
                createSdkModulesConfig()
                    .setSdkPackageName(TEST_PACKAGE_NAME)
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder()
                            .setMajor(major)
                            .setMinor(minor)
                            .setPatch(patch))
                    .build())
            .build();

    execute(
        sdkBundle,
        BuildSdkAsarCommand.fromFlags(
            new FlagParser().parse("--sdk-bundle=" + sdkBundlePath, "--output=" + outputFilePath)));

    ZipFile asarFile = new ZipFile(outputFilePath.toFile());
    SdkMetadata asarSdkMetadata = extractSdkMetadata(asarFile);

    assertThat(asarSdkMetadata)
        .isEqualTo(
            SdkMetadata.newBuilder()
                .setPackageName(TEST_PACKAGE_NAME)
                .setSdkVersion(
                    RuntimeEnabledSdkVersion.newBuilder()
                        .setMajor(major)
                        .setMinor(minor)
                        .setPatch(patch))
                .build());
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

  private void execute(SdkBundle sdkBundle, BuildSdkAsarCommand command) throws Exception {
    new SdkBundleSerializer().writeToDisk(sdkBundle, sdkBundlePath);

    ZipFile bundleZip = new ZipFile(sdkBundlePath.toFile());
    getModulesZip(bundleZip, modulesPath);

    DaggerBuildSdkAsarManagerComponent.builder()
        .setBuildSdkAsarCommand(command)
        .setSdkBundle(sdkBundle)
        .build()
        .create()
        .execute(modulesPath);
  }

  private BuildSdkAsarCommand createCommand(String... additionalFlags) {
    String[] flags =
        Stream.concat(getDefaultFlagList().stream(), stream(additionalFlags))
            .toArray(String[]::new);
    return BuildSdkAsarCommand.fromFlags(new FlagParser().parse(flags));
  }

  private ImmutableList<String> getDefaultFlagList() {
    return ImmutableList.of(
        "--sdk-bundle=" + sdkBundlePath,
        "--output=" + outputFilePath,
        "--apk-signing-key-certificate=" + apkSigningKeyCertificatePath);
  }
}
