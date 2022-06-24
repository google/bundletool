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

import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.testing.TestUtils.createSdkAndroidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModulesWithInvalidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundleWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildSdkAsarCommandTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static final byte[] TEST_CONTENT = new byte[1];

  private Path tmpDir;
  private Path sdkBundlePath;
  private Path modulesPath;
  private Path outputFilePath;
  private X509Certificate apkSigningKeyCertificate;
  private Path apkSigningKeyCertificatePath;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkBundlePath = tmpDir.resolve("SdkBundle.asb");
    modulesPath = tmpDir.resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
    outputFilePath = tmpDir.resolve("output.asar");

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair apkSigningKeyPair = kpg.genKeyPair();
    apkSigningKeyCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            apkSigningKeyPair, "CN=BuildSdkAsarCommandTest_ApkSigningKey");
    apkSigningKeyCertificatePath = tmpDir.resolve("apk-signing-key-public.cert");
    Files.write(apkSigningKeyCertificatePath, apkSigningKeyCertificate.getEncoded());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult() {
    BuildSdkAsarCommand commandViaFlags =
        BuildSdkAsarCommand.fromFlags(
            getDefaultFlagsWithAdditionalFlags(
                "--apk-signing-key-certificate=" + apkSigningKeyCertificatePath));

    BuildSdkAsarCommand.Builder commandViaBuilder =
        BuildSdkAsarCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setOutputFile(outputFilePath)
            .setApkSigningCertificate(apkSigningKeyCertificate);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void certificateDigestNotSet_usesEmptyValue() {
    BuildSdkAsarCommand commandViaFlags =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    BuildSdkAsarCommand.Builder commandViaBuilder =
        BuildSdkAsarCommand.builder().setSdkBundlePath(sdkBundlePath).setOutputFile(outputFilePath);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_sdkBundlePathNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () -> BuildSdkAsarCommand.fromFlags(new FlagParser().parse("")));
    assertThat(e).hasMessageThat().contains("Missing the required --sdk-bundle flag");
  }

  @Test
  public void outputNotSetViaFlags_throws() {
    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildSdkAsarCommand.fromFlags(new FlagParser().parse("--sdk-bundle=" + sdkBundlePath)));
  }

  @Test
  public void outputNotSetViaBuilder_throws() {
    expectMissingRequiredBuilderPropertyException(
        "outputFile",
        () ->
            BuildSdkAsarCommand.builder()
                .setSdkBundlePath(sdkBundlePath)
                .setApkSigningCertificate(apkSigningKeyCertificate)
                .build());
  }

  @Test
  public void bundleNotSetViaFlags_throws() {
    expectMissingRequiredFlagException(
        "sdk-bundle",
        () -> BuildSdkAsarCommand.fromFlags(new FlagParser().parse("--output=" + outputFilePath)));
  }

  @Test
  public void bundleNotSetViaBuilder_throws() {
    expectMissingRequiredBuilderPropertyException(
        "sdkBundlePath", () -> BuildSdkAsarCommand.builder().setOutputFile(outputFilePath).build());
  }

  @Test
  public void overwriteNotSetOutputFileAlreadyExists_throws() throws Exception {
    new ZipBuilder().writeTo(outputFilePath); // Existing file

    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);

    BuildSdkAsarCommand command =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void unknownFlag_throws() {
    UnknownFlagsException exception =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                BuildSdkAsarCommand.fromFlags(
                    getDefaultFlagsWithAdditionalFlags("--unknownFlag=notSure")));

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Unrecognized flags: --%s", "unknownFlag"));
  }

  @Test
  public void missingBundleFile_throws() {
    BuildSdkAsarCommand command =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  // Ensures that validations are run on the bundle zip file.
  @Test
  public void bundleMissingFiles_throws() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder();
    zipBuilder.writeTo(sdkBundlePath);
    BuildSdkAsarCommand command =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "The archive doesn't seem to be an SDK Bundle, it is missing required file '"
                + SDK_MODULES_FILE_NAME
                + "'.");
  }

  // Ensures that validations are run on the module zip file.
  @Test
  public void bundleMultipleModules_throws() throws Exception {
    ZipBuilder modules =
        createZipBuilderForModules()
            .addFileWithProtoContent(
                ZipPath.create("feature/manifest/AndroidManifest.xml"), createSdkAndroidManifest())
            .addFileWithContent(ZipPath.create("feature/dex/classes.dex"), TEST_CONTENT);
    createZipBuilderForSdkBundleWithModules(modules, modulesPath).writeTo(sdkBundlePath);

    BuildSdkAsarCommand command =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e).hasMessageThat().contains("SDK bundles need exactly one module");
  }

  // Ensures that validations are run on the bundle object.
  @Test
  public void invalidManifest_throws() throws Exception {
    createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModulesWithInvalidManifest(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkAsarCommand command =
        BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags());

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "'installLocation' in <manifest> must be 'internalOnly' for SDK bundles if it is set.");
  }

  @Test
  public void executeCreatesFile() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags()).execute();
    assertThat(Files.exists(outputFilePath)).isTrue();
  }

  @Test
  public void executeReturnsOutputFile() throws Exception {
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), modulesPath)
        .writeTo(sdkBundlePath);
    assertThat(BuildSdkAsarCommand.fromFlags(getDefaultFlagsWithAdditionalFlags()).execute())
        .isEqualTo(outputFilePath);
  }

  @Test
  public void printHelpDoesNotCrash() {
    BuildSdkAsarCommand.help();
  }

  private ParsedFlags getDefaultFlagsWithAdditionalFlags(String... additionalFlags) {
    String[] flags =
        Stream.concat(getDefaultFlagList().stream(), stream(additionalFlags))
            .toArray(String[]::new);
    return new FlagParser().parse(flags);
  }

  private ImmutableList<String> getDefaultFlagList() {
    return ImmutableList.of("--sdk-bundle=" + sdkBundlePath, "--output=" + outputFilePath);
  }
}
