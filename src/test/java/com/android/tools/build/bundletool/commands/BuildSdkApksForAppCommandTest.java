/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.tools.build.bundletool.model.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.testing.Aapt2Helper.AAPT2_PATH;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.TestUtils.addKeyToKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModulesWithoutManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkAsarWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundleWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.android.tools.build.bundletool.testing.TestUtils.extractAndroidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.SdkSplitPropertiesInheritedFromApp;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.testing.ApkSetUtils;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
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
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildSdkApksForAppCommandTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";
  private static final SdkSplitPropertiesInheritedFromApp INHERITED_APP_PROPERTIES =
      SdkSplitPropertiesInheritedFromApp.newBuilder()
          .setPackageName("com.test.app")
          .setMinSdkVersion(26)
          .setVersionCode(12345)
          .setResourcesPackageId(2)
          .build();

  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(ImmutableMap.of(ANDROID_HOME, "/android/home"));

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path sdkAsarPath;
  private Path sdkBundlePath;
  private Path appBundlePath;
  private Path extractedModulesFilePath;
  private Path inheritedAppPropertiesConfigPath;
  private Path outputFilePath;
  private Path buildApksOutputFilePath;

  private Path keystorePath;
  private static PrivateKey privateKey;
  private static X509Certificate certificate;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkAsarPath = tmpDir.resolve("sdk.asar");
    sdkBundlePath = tmpDir.resolve("sdk.asb");
    appBundlePath = tmpDir.resolve("app.aab");
    inheritedAppPropertiesConfigPath = tmpDir.resolve("config.json");
    Files.writeString(
        inheritedAppPropertiesConfigPath, JsonFormat.printer().print(INHERITED_APP_PROPERTIES));
    extractedModulesFilePath = tmpDir.resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
    outputFilePath = tmpDir.resolve("output.apks");
    buildApksOutputFilePath = tmpDir.resolve("build-apks-output.apks");

    // Keystore.
    keystorePath = tmpDir.resolve("keystore.jks");
    createKeystore(keystorePath, KEYSTORE_PASSWORD);
    addKeyToKeystore(
        keystorePath, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, privateKey, certificate);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults_withSdkArchive() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildSdkApksForAppCommand commandViaFlags =
        BuildSdkApksForAppCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--sdk-archive=" + sdkAsarPath,
                    "--app-properties=" + inheritedAppPropertiesConfigPath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider);

    BuildSdkApksForAppCommand.Builder commandViaBuilder =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorService(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true);
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults_withSdkBundle() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildSdkApksForAppCommand commandViaFlags =
        BuildSdkApksForAppCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--sdk-bundle=" + sdkBundlePath,
                    "--app-properties=" + inheritedAppPropertiesConfigPath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider);

    BuildSdkApksForAppCommand.Builder commandViaBuilder =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorService(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true);
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalSigning() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildSdkApksForAppCommand commandViaFlags =
        BuildSdkApksForAppCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--sdk-archive=" + sdkAsarPath,
                    "--app-properties=" + inheritedAppPropertiesConfigPath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD),
            new PrintStream(output),
            systemEnvironmentProvider);

    BuildSdkApksForAppCommand.Builder commandViaBuilder =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorService(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            // Optional values.
            .setSigningConfiguration(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build());

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withDirectoryOutputFormat() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildSdkApksForAppCommand commandViaFlags =
        BuildSdkApksForAppCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--sdk-archive=" + sdkAsarPath,
                    "--app-properties=" + inheritedAppPropertiesConfigPath,
                    "--output=" + tmpDir,
                    "--output-format=" + OutputFormat.DIRECTORY,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider);

    BuildSdkApksForAppCommand.Builder commandViaBuilder =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(tmpDir)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorService(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true);
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void sdkAsarNotSet_sdkBundleNotSet_throws() {
    Throwable exceptionFromBuilder =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildSdkApksForAppCommand.builder()
                    .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
                    .setOutputFile(outputFilePath)
                    .build());
    assertThat(exceptionFromBuilder)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");

    Throwable exceptionFromFlags =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--app-properties=" + inheritedAppPropertiesConfigPath,
                            "--output=" + outputFilePath)));
    assertThat(exceptionFromFlags)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");
  }

  @Test
  public void sdkAsarSet_sdkBundleSet_throws() {
    Throwable exceptionFromBuilder =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildSdkApksForAppCommand.builder()
                    .setSdkBundlePath(sdkBundlePath)
                    .setSdkArchivePath(sdkAsarPath)
                    .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
                    .setOutputFile(outputFilePath)
                    .build());
    assertThat(exceptionFromBuilder)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");

    Throwable exceptionFromFlags =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--sdk-bundle=" + sdkBundlePath,
                            "--sdk-archive=" + sdkAsarPath,
                            "--app-properties=" + inheritedAppPropertiesConfigPath,
                            "--output=" + outputFilePath)));
    assertThat(exceptionFromFlags)
        .hasMessageThat()
        .contains("One and only one of SdkBundlePath and SdkArchivePath should be set.");
  }

  @Test
  public void inheritedAppPropertiesNotSet_throws() {
    expectMissingRequiredBuilderPropertyException(
        "inheritedAppProperties",
        () ->
            BuildSdkApksForAppCommand.builder()
                .setSdkArchivePath(sdkAsarPath)
                .setOutputFile(outputFilePath)
                .build());

    expectMissingRequiredFlagException(
        "app-properties",
        () ->
            BuildSdkApksForAppCommand.fromFlags(
                new FlagParser()
                    .parse("--sdk-archive=" + sdkAsarPath, "--output=" + outputFilePath)));
  }

  @Test
  public void outputFileNotSet_throws() {
    expectMissingRequiredBuilderPropertyException(
        "outputFile",
        () ->
            BuildSdkApksForAppCommand.builder()
                .setSdkArchivePath(sdkAsarPath)
                .setInheritedAppProperties(inheritedAppPropertiesConfigPath)
                .build());

    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildSdkApksForAppCommand.fromFlags(
                new FlagParser()
                    .parse(
                        "--sdk-archive=" + sdkAsarPath,
                        "--app-properties=" + inheritedAppPropertiesConfigPath)));
  }

  @Test
  public void sdkArchiveSet_fileDoesNotExist_throws() {
    Throwable exceptionFromFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--sdk-archive=non-existent-file.asar",
                                "--app-properties=" + inheritedAppPropertiesConfigPath,
                                "--output=" + outputFilePath))
                    .execute());
    assertThat(exceptionFromFlags)
        .hasMessageThat()
        .contains("File 'non-existent-file.asar' was not found.");
  }

  @Test
  public void sdkArchiveSet_badExtension_throws() throws Exception {
    Path filePathWithBadExtension = tmpDir.resolve("sdk.sdk");
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(filePathWithBadExtension);
    Throwable exceptionFromFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--sdk-archive=" + filePathWithBadExtension,
                                "--app-properties=" + inheritedAppPropertiesConfigPath,
                                "--output=" + outputFilePath))
                    .execute());
    assertThat(exceptionFromFlags).hasMessageThat().contains("expected to have '.asar' extension.");
  }

  @Test
  public void sdkBundleSet_fileDoesNotExist_throws() {
    Throwable exceptionFromFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--sdk-bundle=non-existent-file.asb",
                                "--app-properties=" + inheritedAppPropertiesConfigPath,
                                "--output=" + outputFilePath))
                    .execute());
    assertThat(exceptionFromFlags)
        .hasMessageThat()
        .contains("File 'non-existent-file.asb' was not found.");
  }

  @Test
  public void sdkBundleSet_badExtension_throws() throws Exception {
    Path filePathWithBadExtension = tmpDir.resolve("sdk.sdk");
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(filePathWithBadExtension);
    Throwable exceptionFromFlags =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BuildSdkApksForAppCommand.fromFlags(
                        new FlagParser()
                            .parse(
                                "--sdk-bundle=" + filePathWithBadExtension,
                                "--app-properties=" + inheritedAppPropertiesConfigPath,
                                "--output=" + outputFilePath))
                    .execute());
    assertThat(exceptionFromFlags).hasMessageThat().contains("expected to have '.asb' extension.");
  }

  @Test
  public void outputFileHasBadExtension_throws() throws Exception {
    Path outputFilePathWithBadExtension = tmpDir.resolve("apks.txt");
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), extractedModulesFilePath)
        .writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePathWithBadExtension)
            .build();

    Throwable e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Flag --output should be the path where to generate the APK Set. Its extension must be"
                + " '.apks'.");
  }

  @Test
  public void outputFileExists_throws() throws Exception {
    // create file on the output path.
    createZipBuilderForModules().writeTo(outputFilePath);
    createZipBuilderForSdkBundleWithModules(createZipBuilderForModules(), extractedModulesFilePath)
        .writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("already exists.");
  }

  @Test
  public void modulesZipMissingManifestInAsar_validationFails() throws Exception {
    createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModulesWithoutManifest(), extractedModulesFilePath)
        .writeTo(sdkAsarPath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
  }

  @Test
  public void modulesZipMissingManifestInSdkBundle_validationFails() throws Exception {
    createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModulesWithoutManifest(), extractedModulesFilePath)
        .writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
  }

  @Test
  public void generatesModuleSplit_withSdkArchive() throws Exception {
    ZipBuilder asarZipBuilder =
        createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    asarZipBuilder.writeTo(sdkAsarPath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();

    command.execute();

    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    assertThat(apkSetFile.size()).isEqualTo(1);
    String apkPathInsideArchive =
        "splits/" + SdkBundleBuilder.PACKAGE_NAME.replace(".", "") + "-master.apk";
    assertThat(ZipUtils.allFileEntriesPaths(apkSetFile))
        .containsExactly(ZipPath.create(apkPathInsideArchive));
    File apkFile = ApkSetUtils.extractFromApkSetFile(apkSetFile, apkPathInsideArchive, tmpDir);
    AndroidManifest apkManifest = extractAndroidManifest(apkFile, tmpDir);
    assertThat(apkManifest.getPackageName()).isEqualTo(INHERITED_APP_PROPERTIES.getPackageName());
    assertThat(apkManifest.getVersionCode()).hasValue(INHERITED_APP_PROPERTIES.getVersionCode());
    assertThat(apkManifest.getMinSdkVersion())
        .hasValue(INHERITED_APP_PROPERTIES.getMinSdkVersion());
  }

  @Test
  public void generatesModuleSplit_withSdkBundle() throws Exception {
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();

    command.execute();

    ZipFile apkSetFile = new ZipFile(outputFilePath.toFile());
    assertThat(apkSetFile.size()).isEqualTo(1);
    String apkPathInsideArchive =
        "splits/" + SdkBundleBuilder.PACKAGE_NAME.replace(".", "") + "-master.apk";
    assertThat(ZipUtils.allFileEntriesPaths(apkSetFile))
        .containsExactly(ZipPath.create(apkPathInsideArchive));
    File apkFile = ApkSetUtils.extractFromApkSetFile(apkSetFile, apkPathInsideArchive, tmpDir);
    AndroidManifest apkManifest = extractAndroidManifest(apkFile, tmpDir);
    assertThat(apkManifest.getPackageName()).isEqualTo(INHERITED_APP_PROPERTIES.getPackageName());
    assertThat(apkManifest.getVersionCode()).hasValue(INHERITED_APP_PROPERTIES.getVersionCode());
    assertThat(apkManifest.getMinSdkVersion())
        .hasValue(INHERITED_APP_PROPERTIES.getMinSdkVersion());
  }

  @Test
  public void generateModuleSplit_withAsar_sameAsBuildApks() throws Exception {
    String validCertDigest =
        "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";
    ZipBuilder asarZipBuilder =
        createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModules(),
            SdkMetadata.newBuilder()
                .setPackageName(SdkBundleBuilder.PACKAGE_NAME)
                .setSdkVersion(RuntimeEnabledSdkVersion.newBuilder().setMajor(1).setMinor(1))
                .setCertificateDigest(validCertDigest)
                .build(),
            extractedModulesFilePath);
    asarZipBuilder.writeTo(sdkAsarPath);
    BuildSdkApksForAppCommand buildSdkApksForAppCommand =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(sdkAsarPath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION)))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName(SdkBundleBuilder.PACKAGE_NAME)
                                    .setVersionMajor(1)
                                    .setVersionMinor(1)
                                    .setCertificateDigest(validCertDigest)
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, appBundlePath);
    BuildApksCommand buildApksCommand =
        BuildApksCommand.builder()
            .setBundlePath(appBundlePath)
            .setOutputFile(buildApksOutputFilePath)
            .setRuntimeEnabledSdkArchivePaths(ImmutableSet.of(sdkAsarPath))
            .build();

    buildSdkApksForAppCommand.execute();
    buildApksCommand.execute();

    String sdkSplitPath =
        "splits/" + SdkBundleBuilder.PACKAGE_NAME.replace(".", "") + "-master.apk";
    ZipFile buildApksOutputSet = new ZipFile(buildApksOutputFilePath.toFile());
    File buildApksOutputApk =
        ApkSetUtils.extractFromApkSetFile(buildApksOutputSet, sdkSplitPath, tmpDir);
    ZipFile buildSdkApksForApOutputSet = new ZipFile(outputFilePath.toFile());
    File buildSdkApksForAppOutputApk =
        ApkSetUtils.extractFromApkSetFile(buildSdkApksForApOutputSet, sdkSplitPath, tmpDir);
    assertThat(getFileHash(buildSdkApksForAppOutputApk)).isEqualTo(getFileHash(buildApksOutputApk));
  }

  @Test
  public void generateModuleSplit_withSdkBundle_sameAsBuildApks() throws Exception {
    String validCertDigest =
        "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand buildSdkApksForAppCommand =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(outputFilePath)
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        androidManifest("com.test.app", withMinSdkVersion(ANDROID_L_API_VERSION)))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName(SdkBundleBuilder.PACKAGE_NAME)
                                    .setVersionMajor(1)
                                    .setVersionMinor(1)
                                    .setCertificateDigest(validCertDigest)
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, appBundlePath);
    BuildApksCommand buildApksCommand =
        BuildApksCommand.builder()
            .setBundlePath(appBundlePath)
            .setOutputFile(buildApksOutputFilePath)
            .setRuntimeEnabledSdkBundlePaths(ImmutableSet.of(sdkBundlePath))
            .build();

    buildSdkApksForAppCommand.execute();
    buildApksCommand.execute();

    String sdkSplitPath =
        "splits/" + SdkBundleBuilder.PACKAGE_NAME.replace(".", "") + "-master.apk";
    ZipFile buildApksOutputSet = new ZipFile(buildApksOutputFilePath.toFile());
    File buildApksOutputApk =
        ApkSetUtils.extractFromApkSetFile(buildApksOutputSet, sdkSplitPath, tmpDir);
    ZipFile buildSdkApksForApOutputSet = new ZipFile(outputFilePath.toFile());
    File buildSdkApksForAppOutputApk =
        ApkSetUtils.extractFromApkSetFile(buildSdkApksForApOutputSet, sdkSplitPath, tmpDir);
    assertThat(getFileHash(buildSdkApksForAppOutputApk)).isEqualTo(getFileHash(buildApksOutputApk));
  }

  @Test
  public void directoryOutputFormat_success() throws Exception {
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(tmpDir)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .build();

    command.execute();

    String sdkSplitPath =
        "splits/" + SdkBundleBuilder.PACKAGE_NAME.replace(".", "") + "-master.apk";
    assertThat(Files.exists(Path.of(tmpDir.toString(), sdkSplitPath))).isTrue();
  }

  @Test
  public void serializeTableOfContents_setToDefaultValue_noTocInOutput() throws Exception {
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(tmpDir)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .build();

    command.execute();

    assertThat(Files.exists(tmpDir.resolve(TABLE_OF_CONTENTS_FILE))).isFalse();
  }

  @Test
  public void serializeTableOfContents_setToTrue_tocSerialized() throws Exception {
    ZipBuilder sdkBundleZipBuilder =
        createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModules(), extractedModulesFilePath);
    sdkBundleZipBuilder.writeTo(sdkBundlePath);
    BuildSdkApksForAppCommand command =
        BuildSdkApksForAppCommand.builder()
            .setSdkBundlePath(sdkBundlePath)
            .setInheritedAppProperties(INHERITED_APP_PROPERTIES)
            .setOutputFile(tmpDir)
            .setOutputFormat(OutputFormat.DIRECTORY)
            .setSerializeTableOfContents(true)
            .build();

    command.execute();

    BuildApksResult buildApksResult = ResultUtils.readTableOfContents(tmpDir);
    assertThat(buildApksResult.getPackageName())
        .isEqualTo(INHERITED_APP_PROPERTIES.getPackageName());
    Variant generatedVariant = Iterables.getOnlyElement(buildApksResult.getVariantList());
    ApkSet generatedModule = Iterables.getOnlyElement(generatedVariant.getApkSetList());
    assertThat(generatedModule.getApkDescriptionCount()).isEqualTo(1);
    assertThat(generatedModule.getModuleMetadata().getName())
        .isEqualTo(SdkBundleBuilder.PACKAGE_NAME.replace(".", ""));
  }

  @Test
  public void printHelpDoesNotCrash() {
    BuildSdkApksForAppCommand.help();
  }

  private static HashCode getFileHash(File file) throws Exception {
    return ByteSource.wrap(Files.newInputStream(file.toPath()).readAllBytes())
        .hash(Hashing.sha256());
  }
}
