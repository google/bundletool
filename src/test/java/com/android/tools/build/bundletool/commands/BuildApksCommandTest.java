/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile.Type.DEX;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.ARCHIVE;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.DEFAULT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.SystemApkOption.UNCOMPRESSED_NATIVE_LIBRARIES;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.testing.Aapt2Helper.AAPT2_PATH;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.createDeviceSpecFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.createSdkModulesConfig;
import static com.android.tools.build.bundletool.testing.TestUtils.addKeyToKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createDebugKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createInvalidSdkAndroidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createKeystore;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModulesWithoutManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkAsarWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundle;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundleWithModules;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.apksig.SigningCertificateLineage;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.RuntimeEnabledSdkConfigProto.CertificateOverride;
import com.android.bundle.RuntimeEnabledSdkConfigProto.CertificateOverrides;
import com.android.bundle.RuntimeEnabledSdkConfigProto.LocalDeploymentRuntimeEnabledSdkConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.ApkSerializer;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApksigSigningConfiguration;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.SdkBundleBuilder;
import com.android.tools.build.bundletool.testing.TargetingUtils;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import dagger.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Properties;
import javax.inject.Inject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class BuildApksCommandTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";
  private static final Path ADB_PATH =
      Paths.get("third_party/java/android/android_sdk_linux/platform-tools/adb.static");
  private static final String DEVICE_ID = "id1";
  private static final String DEBUG_KEYSTORE_PASSWORD = "android";
  private static final String DEBUG_KEY_PASSWORD = "android";
  private static final String DEBUG_KEY_ALIAS = "AndroidDebugKey";
  private static final String STAMP_SOURCE = "https://www.example.com";
  private static final String STAMP_KEYSTORE_PASSWORD = "stamp-keystore-password";
  private static final String STAMP_KEY_PASSWORD = "stamp-key-password";
  private static final String STAMP_KEY_ALIAS = "stamp-key-alias";
  private static final String OLDEST_SIGNER_KEYSTORE_PASSWORD = "oldest-signer-keystore-password";
  private static final String OLDEST_SIGNER_KEY_PASSWORD = "oldest-signer-key-password";
  private static final String OLDEST_SIGNER_KEY_ALIAS = "oldest-signer-key-alias";
  private static final String VALID_CERT_FINGERPRINT =
      "96:C7:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:3E:02:0A";
  private static final String VALID_CERT_FINGERPRINT2 =
      "C7:96:EC:89:3E:69:2A:25:BA:4D:EE:C1:84:E8:33:3F:34:7D:6D:12:26:A1:C1:AA:70:A2:8A:DB:75:0A:02:3E";
  private static final String APP_STORE_PACKAGE_NAME = "my.store";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static PrivateKey privateKey;
  private static X509Certificate certificate;
  private static PrivateKey stampPrivateKey;
  private static X509Certificate stampCertificate;
  private static PrivateKey oldestSignerPrivateKey;
  private static X509Certificate oldestSignerCertificate;

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private Path bundlePath;
  private Path outputFilePath;
  private Path tmpDir;
  private Path keystorePath;
  private Path stampKeystorePath;
  private Path oldestSignerPropertiesPath;
  private Path sdkBundlePath1;
  private Path sdkBundlePath2;
  private Path sdkArchivePath1;
  private Path sdkArchivePath2;
  private Path extractedSdkBundleModulesPath;
  private Path localDeploymentRuntimeEnabledSdkConfigPath;

  private final AdbServer fakeAdbServer = mock(AdbServer.class);
  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(
          ImmutableMap.of(ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID));

  @Inject ApkSerializer apkSerializer;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Creating a new key takes in average 75ms (with peaks at 200ms), so creating a single one for
    // all the tests.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=BuildApksCommandTest");

    // Stamp key.
    KeyPair stampKeyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    stampPrivateKey = stampKeyPair.getPrivate();
    stampCertificate =
        CertificateFactory.buildSelfSignedCertificate(stampKeyPair, "CN=BuildApksCommandTest");

    // Oldest signer key.
    KeyPair oldestSignerKeyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    oldestSignerPrivateKey = oldestSignerKeyPair.getPrivate();
    oldestSignerCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            oldestSignerKeyPair, "CN=BuildApksCommandTest");
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
    outputFilePath = tmpDir.resolve("output.apks");

    // Keystore.
    keystorePath = tmpDir.resolve("keystore.jks");
    createKeystore(keystorePath, KEYSTORE_PASSWORD);
    addKeyToKeystore(
        keystorePath, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, privateKey, certificate);
    addKeyToKeystore(
        keystorePath,
        KEYSTORE_PASSWORD,
        STAMP_KEY_ALIAS,
        STAMP_KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);

    // Stamp Keystore.
    stampKeystorePath = tmpDir.resolve("stamp-keystore.jks");
    createKeystore(stampKeystorePath, STAMP_KEYSTORE_PASSWORD);
    addKeyToKeystore(
        stampKeystorePath,
        STAMP_KEYSTORE_PASSWORD,
        STAMP_KEY_ALIAS,
        STAMP_KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);
    addKeyToKeystore(
        stampKeystorePath,
        STAMP_KEYSTORE_PASSWORD,
        KEY_ALIAS,
        KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);

    // Oldest signer Keystore.
    Path oldestSignerKeystorePath = tmpDir.resolve("oldest-signer-keystore.jks");
    createKeystore(oldestSignerKeystorePath, OLDEST_SIGNER_KEYSTORE_PASSWORD);
    addKeyToKeystore(
        oldestSignerKeystorePath,
        OLDEST_SIGNER_KEYSTORE_PASSWORD,
        OLDEST_SIGNER_KEY_ALIAS,
        OLDEST_SIGNER_KEY_PASSWORD,
        oldestSignerPrivateKey,
        oldestSignerCertificate);
    oldestSignerPropertiesPath =
        createKeystorePropertiesFile(
            oldestSignerKeystorePath.toString(),
            OLDEST_SIGNER_KEY_ALIAS,
            OLDEST_SIGNER_KEYSTORE_PASSWORD,
            OLDEST_SIGNER_KEY_PASSWORD);

    // Runtime-enabled-SDK-related fields.
    sdkBundlePath1 = tmpDir.resolve("bundle1.asb");
    sdkBundlePath2 = tmpDir.resolve("bundle2.asb");
    sdkArchivePath1 = tmpDir.resolve("archive1.asar");
    sdkArchivePath2 = tmpDir.resolve("archive2.asar");
    extractedSdkBundleModulesPath = tmpDir.resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
    localDeploymentRuntimeEnabledSdkConfigPath =
        tmpDir.resolve("local-runtime-enabled-sdk-config.json");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOptimizeFor() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--optimize-for=screen_density"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOptimizationDimensions(ImmutableSet.of(SCREEN_DENSITY))
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optimizeForTextureCompressionFormat()
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--optimize-for=texture_compression_format"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOptimizationDimensions(ImmutableSet.of(TEXTURE_COMPRESSION_FORMAT))
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalSigning() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD),
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setSigningConfiguration(
                SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get())
            .build();

    assertThat(commandViaBuilder.getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalUniversal() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--mode=UNIVERSAL"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setApkBuildMode(UNIVERSAL)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOverwrite() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--overwrite"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOverwriteOutput(true)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalOutputFormat() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--output-format=" + DIRECTORY),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setOutputFormat(DIRECTORY)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalVerbose() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--verbose"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setVerbose(true)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_deviceId() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--device-id=" + DEVICE_ID,
                    "--connected-device",
                    "--adb=" + ADB_PATH,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceId(DEVICE_ID)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_androidSerialVariable() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--connected-device",
                    "--adb=" + ADB_PATH,
                    "--aapt2=" + AAPT2_PATH),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceId(DEVICE_ID)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalLocalTestingMode() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--local-testing"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setLocalTestingMode(true)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalDeviceTier() throws Exception {
    Path deviceSpecPath =
        createDeviceSpecFile(
            mergeSpecs(sdkVersion(28), density(DensityAlias.HDPI), abis("x86"), locales("en")),
            tmpDir.resolve("device.json"));
    final int deviceTier = 1;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--device-spec=" + deviceSpecPath,
                    "--device-tier=" + deviceTier),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setDeviceSpec(deviceSpecPath)
            .setDeviceTier(deviceTier)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_optionalCountrySet() throws Exception {
    Path deviceSpecPath =
        createDeviceSpecFile(
            mergeSpecs(sdkVersion(28), density(DensityAlias.HDPI), abis("x86"), locales("en")),
            tmpDir.resolve("device.json"));
    final String countrySet = "latam";
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--device-spec=" + deviceSpecPath,
                    "--country-set=" + countrySet),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setDeviceSpec(deviceSpecPath)
            .setCountrySet(countrySet)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void countrySetWithoutDeviceSpecOrConnectedDevice_throws() throws Exception {
    final String countrySet = "latam";
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    InvalidCommandException exception =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            // Optional values.
                            "--country-set=" + countrySet),
                    new PrintStream(output),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Setting --country-set requires using either the --connected-device or the"
                + " --device-spec flag.");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_7zipPath() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--7zip=/path/to/7za"),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get())
            .setP7ZipCommand(commandViaFlags.getP7ZipCommand().get());

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void outputNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "outputFile",
        () ->
            BuildApksCommand.builder()
                .setBundlePath(bundlePath)
                .setAapt2Command(aapt2Command)
                .build());

    expectMissingRequiredFlagException(
        "output",
        () ->
            BuildApksCommand.fromFlags(
                new FlagParser().parse("--bundle=" + bundlePath), fakeAdbServer));
  }

  @Test
  public void bundleNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "bundlePath",
        () ->
            BuildApksCommand.builder()
                .setOutputFile(outputFilePath)
                .setAapt2Command(aapt2Command)
                .build());

    expectMissingRequiredFlagException(
        "bundle",
        () ->
            BuildApksCommand.fromFlags(
                new FlagParser().parse("--output=" + outputFilePath), fakeAdbServer));
  }

  @Test
  public void optimizationDimensionsWithUniversal_throws() throws Exception {
    InvalidCommandException builderException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setAapt2Command(aapt2Command)
                    .setApkBuildMode(UNIVERSAL)
                    .setOptimizationDimensions(ImmutableSet.of(ABI))
                    .build());
    assertThat(builderException)
        .hasMessageThat()
        .contains("Optimization dimension can be only set when running with 'default' mode flag.");

    InvalidCommandException flagsException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--optimize-for=abi",
                            "--mode=UNIVERSAL"),
                    fakeAdbServer));
    assertThat(flagsException)
        .hasMessageThat()
        .contains("Optimization dimension can be only set when running with 'default' mode flag.");
  }

  @Test
  public void modulesFlagWithDefault_throws() throws Exception {
    InvalidCommandException builderException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setAapt2Command(aapt2Command)
                    .setApkBuildMode(DEFAULT)
                    .setModules(ImmutableSet.of("base"))
                    .build());
    assertThat(builderException)
        .hasMessageThat()
        .contains("Modules can be only set when running with 'universal' or 'system' mode flag.");
  }

  @Test
  public void nonPositiveMaxThreads_throws() throws Exception {
    FlagParseException zeroException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--max-threads=0"),
                    fakeAdbServer));
    assertThat(zeroException).hasMessageThat().contains("flag --max-threads has illegal value");

    FlagParseException negativeException =
        assertThrows(
            FlagParseException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--max-threads=-1"),
                    fakeAdbServer));
    assertThat(negativeException).hasMessageThat().contains("flag --max-threads has illegal value");
  }

  @Test
  public void positiveMaxThreads_succeeds() throws Exception {
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--max-threads=3"),
        fakeAdbServer);
  }

  @Test
  public void keystoreFlags_keyAliasNotSet() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks=" + keystorePath),
                    fakeAdbServer));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks-key-alias is required when --ks is set.");
  }

  @Test
  public void keystoreFlags_keystoreNotSet() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks-key-alias=" + KEY_ALIAS),
                    fakeAdbServer));
    assertThat(e).hasMessageThat().isEqualTo("Flag --ks is required when --ks-key-alias is set.");
  }

  @Test
  public void printHelpDoesNotCrash() {
    BuildApksCommand.help();
  }

  @Test
  public void noKeystoreProvidedPrintsWarning() {
    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), "/"));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--aapt2=" + AAPT2_PATH),
        new PrintStream(output),
        provider,
        fakeAdbServer);

    assertThat(new String(output.toByteArray(), UTF_8))
        .contains("WARNING: The APKs won't be signed");
  }

  @Test
  public void noKeystoreProvidedPrintsWarning_debugKeystore() throws Exception {
    Path debugKeystorePath = tmpDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(debugKeystorePath);
    createDebugKeystore(
        debugKeystorePath, DEBUG_KEYSTORE_PASSWORD, DEBUG_KEY_ALIAS, DEBUG_KEY_PASSWORD);

    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), tmpDir.toString()));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--aapt2=" + AAPT2_PATH),
        new PrintStream(output),
        provider,
        fakeAdbServer);

    assertThat(new String(output.toByteArray(), UTF_8))
        .contains("INFO: The APKs will be signed with the debug keystore");
  }

  @Test
  public void keystoreProvidedDoesNotPrintWarning() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand.fromFlags(
        new FlagParser()
            .parse(
                "--bundle=" + bundlePath,
                "--output=" + outputFilePath,
                "--aapt2=" + AAPT2_PATH,
                "--ks=" + keystorePath,
                "--ks-key-alias=" + KEY_ALIAS,
                "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                "--key-pass=pass:" + KEY_PASSWORD),
        new PrintStream(output),
        systemEnvironmentProvider,
        fakeAdbServer);

    assertThat(new String(output.toByteArray(), UTF_8))
        .doesNotContain("WARNING: The APKs won't be signed");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_noStamp() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--create-stamp=" + false),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_sameSigningKey() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--create-stamp=" + true),
            fakeAdbServer);
    SigningConfiguration signingConfiguration =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setSigningConfiguration(signingConfiguration)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder().setSigningConfiguration(signingConfiguration).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get())
            .build();

    assertThat(commandViaBuilder.getSourceStamp()).isEqualTo(commandViaFlags.getSourceStamp());
    assertThat(commandViaBuilder.getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_separateKeystore() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--create-stamp=" + true,
                    "--stamp-ks=" + stampKeystorePath,
                    "--stamp-ks-pass=pass:" + STAMP_KEYSTORE_PASSWORD),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    SigningConfiguration signingConfiguration =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();
    SigningConfiguration stampSigningConfiguration =
        SigningConfiguration.builder().setSignerConfig(stampPrivateKey, stampCertificate).build();

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setSigningConfiguration(signingConfiguration)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder().setSigningConfiguration(stampSigningConfiguration).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build().getSourceStamp())
        .isEqualTo(commandViaFlags.getSourceStamp());
    assertThat(commandViaBuilder.build().getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_separateKeyAlias() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--create-stamp=" + true,
                    "--stamp-key-alias=" + STAMP_KEY_ALIAS,
                    "--stamp-key-pass=pass:" + STAMP_KEY_PASSWORD),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    SigningConfiguration signingConfiguration =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();
    SigningConfiguration stampSigningConfiguration =
        SigningConfiguration.builder().setSignerConfig(stampPrivateKey, stampCertificate).build();

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setSigningConfiguration(signingConfiguration)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder().setSigningConfiguration(stampSigningConfiguration).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build().getSourceStamp())
        .isEqualTo(commandViaFlags.getSourceStamp());
    assertThat(commandViaBuilder.build().getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_separateKeystoreAndKeyAlias()
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--create-stamp=" + true,
                    "--stamp-ks=" + stampKeystorePath,
                    "--stamp-key-alias=" + STAMP_KEY_ALIAS,
                    "--stamp-ks-pass=pass:" + STAMP_KEYSTORE_PASSWORD,
                    "--stamp-key-pass=pass:" + STAMP_KEY_PASSWORD),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    SigningConfiguration stampSigningConfiguration =
        SigningConfiguration.builder().setSignerConfig(stampPrivateKey, stampCertificate).build();

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder().setSigningConfiguration(stampSigningConfiguration).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_debugKey() throws Exception {
    Path debugKeystorePath = tmpDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(debugKeystorePath);
    SigningConfiguration signingConfiguration =
        createDebugKeystore(
            debugKeystorePath, DEBUG_KEYSTORE_PASSWORD, DEBUG_KEY_ALIAS, DEBUG_KEY_PASSWORD);
    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), tmpDir.toString()));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--create-stamp=" + true),
            new PrintStream(output),
            provider,
            fakeAdbServer);

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setSigningConfiguration(signingConfiguration)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder().setSigningConfiguration(signingConfiguration).build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get())
            .build();

    assertThat(commandViaBuilder.getSourceStamp()).isEqualTo(commandViaFlags.getSourceStamp());
    assertThat(commandViaBuilder.getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void stampKeystoreFlags_noKeystore_fails() throws Exception {
    SystemEnvironmentProvider provider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), "/"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--create-stamp=" + true),
                    new PrintStream(output),
                    provider,
                    fakeAdbServer));

    assertThat(e).hasMessageThat().isEqualTo("No key was found to sign the stamp.");
  }

  @Test
  public void stampKeystoreFlags_noKeyAlias_fails() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--create-stamp=" + true,
                            "--stamp-ks=" + keystorePath),
                    fakeAdbServer));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Flag --stamp-key-alias or --ks-key-alias are required when --stamp-ks or --ks are"
                + " set.");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_source() throws Exception {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    // Optional values.
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--create-stamp=" + true,
                    "--stamp-source=" + STAMP_SOURCE),
            fakeAdbServer);
    SigningConfiguration signingConfiguration =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();

    BuildApksCommand commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setSigningConfiguration(signingConfiguration)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder()
                    .setSigningConfiguration(signingConfiguration)
                    .setSource(STAMP_SOURCE)
                    .build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get())
            .build();

    assertThat(commandViaBuilder.getSourceStamp()).isEqualTo(commandViaFlags.getSourceStamp());
    assertThat(commandViaBuilder.getSigningConfiguration())
        .isEqualTo(commandViaFlags.getSigningConfiguration());
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_stamp_withNoTimestamp() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--create-stamp=" + true,
                    "--stamp-ks=" + stampKeystorePath,
                    "--stamp-key-alias=" + STAMP_KEY_ALIAS,
                    "--stamp-ks-pass=pass:" + STAMP_KEYSTORE_PASSWORD,
                    "--stamp-key-pass=pass:" + STAMP_KEY_PASSWORD,
                    "--stamp-exclude-timestamp=" + true),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);
    SigningConfiguration stampSigningConfiguration =
        SigningConfiguration.builder().setSignerConfig(stampPrivateKey, stampCertificate).build();

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Stamp
            .setSourceStamp(
                SourceStamp.builder()
                    .setSigningConfiguration(stampSigningConfiguration)
                    .setIncludeTimestamp(false)
                    .build())
            // Must copy instance of the internal executor service.
            .setAapt2Command(commandViaFlags.getAapt2Command().get())
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_runtimeEnabledSdkBundlesSet() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setRuntimeEnabledSdkBundlePaths(ImmutableSet.of(sdkBundlePath1, sdkBundlePath2))
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_runtimeEnabledSdkArchivesSet() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1 + "," + sdkArchivePath2),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setRuntimeEnabledSdkArchivePaths(ImmutableSet.of(sdkArchivePath1, sdkArchivePath2))
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_localRuntimeEnabledSdkConfigSet()
      throws Exception {
    LocalDeploymentRuntimeEnabledSdkConfig config =
        LocalDeploymentRuntimeEnabledSdkConfig.newBuilder()
            .setCertificateOverrides(
                CertificateOverrides.newBuilder()
                    .addPerSdkCertificateOverride(
                        CertificateOverride.newBuilder()
                            .setSdkPackageName("com.test.sdk1")
                            .setCertificateDigest(VALID_CERT_FINGERPRINT))
                    .addPerSdkCertificateOverride(
                        CertificateOverride.newBuilder()
                            .setSdkPackageName("com.test.sdk2")
                            .setCertificateDigest(VALID_CERT_FINGERPRINT2)))
            .build();
    Files.writeString(
        localDeploymentRuntimeEnabledSdkConfigPath, JsonFormat.printer().print(config));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1,
                    "--local-runtime-enabled-sdk-config="
                        + localDeploymentRuntimeEnabledSdkConfigPath),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setRuntimeEnabledSdkBundlePaths(ImmutableSet.of(sdkBundlePath1))
            .setLocalDeploymentRuntimeEnabledSdkConfig(config)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_customStorePackage() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--mode=" + ARCHIVE,
                    "--store-package=" + APP_STORE_PACKAGE_NAME),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setApkBuildMode(ApkBuildMode.ARCHIVE)
            .setAppStorePackageName(APP_STORE_PACKAGE_NAME)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void missingBundleFile_throws() throws Exception {
    Path bundlePath = tmpDir.resolve("bundle.aab");
    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void outputFileAlreadyExists_throws() throws Exception {
    createAppBundle(bundlePath);
    Files.createFile(outputFilePath);

    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void overwriteRequestedForDirectoryOutputFormat_throws() throws Exception {
    createAppBundle(bundlePath);

    ParsedFlags flags =
        new FlagParser()
            .parse(
                "--bundle=" + bundlePath,
                "--output=" + tmpDir,
                "--output-format=" + DIRECTORY,
                "--overwrite");
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e).hasMessageThat().contains("flag is not supported");
  }

  @Test
  public void allParentDirectoriesCreated() throws Exception {
    createAppBundle(bundlePath);
    Path outputApks = tmpDir.resolve("non-existing-dir").resolve("app.apks");

    ParsedFlags flags = new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputApks);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    command.execute();

    assertThat(Files.exists(outputApks)).isTrue();
  }

  @Test
  public void systemApkOptions_systemMode_succeeds() throws Exception {
    Path deviceSpecPath =
        createDeviceSpecFile(
            mergeSpecs(density(DensityAlias.MDPI), abis("x86")), tmpDir.resolve("device.json"));
    BuildApksCommand buildApksCommand =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--mode=" + SYSTEM,
                    "--device-spec=" + deviceSpecPath,
                    "--system-apk-options=" + UNCOMPRESSED_NATIVE_LIBRARIES),
            fakeAdbServer);
    assertThat(buildApksCommand.getSystemApkOptions())
        .containsExactly(UNCOMPRESSED_NATIVE_LIBRARIES);
  }

  @Test
  public void systemApkOptions_nonSystemMode_throws() throws Exception {
    InvalidCommandException flagsException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--mode=" + DEFAULT,
                            "--system-apk-options=" + UNCOMPRESSED_NATIVE_LIBRARIES),
                    fakeAdbServer));
    assertThat(flagsException)
        .hasMessageThat()
        .contains("'system-apk-options' flag is available in system mode only.");
  }

  @Test
  public void useDeviceTargeting_noDeviceSpec_throws() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--fuse-only-device-matching-modules"),
                    fakeAdbServer));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Device spec must be provided when using 'fuse-only-device-matching-modules' flag.");
  }

  @Test
  public void populateMinV3SigningApi() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int minV3Api = 30;

    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--min-v3-rotation-api-version=" + minV3Api),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    SigningConfiguration signingConfiguration = commandViaFlags.getSigningConfiguration().get();
    assertThat(signingConfiguration.getMinimumV3RotationApiVersion()).hasValue(minV3Api);
  }

  @Test
  public void populateRotationMinSdkVersion() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int rotationMinSdkVersion = 30;

    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--rotation-min-sdk-version=" + rotationMinSdkVersion),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    SigningConfiguration signingConfiguration = commandViaFlags.getSigningConfiguration().get();
    assertThat(signingConfiguration.getRotationMinSdkVersion()).hasValue(rotationMinSdkVersion);
  }

  @Test
  public void badTransparencyFile_throws() throws Exception {
    createAppBundleWithCodeTransparency(
        bundlePath,
        CodeTransparency.newBuilder()
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(DEX)
                    .setApkPath("non/existent/path/dex.file")
                    .setSha256("sha-256")
                    .setPath("non/existent/path/dex.file"))
            .build());

    ParsedFlags flags =
        new FlagParser().parse("--bundle=" + bundlePath, "--output=" + outputFilePath);
    BuildApksCommand command = BuildApksCommand.fromFlags(flags, fakeAdbServer);

    Throwable e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Verification failed because code was modified after transparency metadata"
                + " generation.");
  }

  @Test
  public void populateLineage_binaryFile() throws Exception {
    SigningCertificateLineage.SignerConfig signerConfig =
        new SigningCertificateLineage.SignerConfig.Builder(privateKey, certificate).build();
    SigningCertificateLineage.SignerConfig oldestSignerConfig =
        new SigningCertificateLineage.SignerConfig.Builder(
                oldestSignerPrivateKey, oldestSignerCertificate)
            .build();
    SigningCertificateLineage lineage =
        new SigningCertificateLineage.Builder(oldestSignerConfig, signerConfig).build();

    File lineageFile = tmpDir.resolve("lineage-file").toFile();
    lineage.writeToFile(lineageFile);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--lineage=" + lineageFile,
                    "--oldest-signer=" + oldestSignerPropertiesPath),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    SigningConfiguration signingConfiguration = commandViaFlags.getSigningConfiguration().get();
    assertThat(signingConfiguration.getSigningCertificateLineage().get().getCertificatesInLineage())
        .containsExactly(oldestSignerCertificate, certificate);
    assertThat(signingConfiguration.getOldestSigner().get().getPrivateKey())
        .isEqualTo(oldestSignerConfig.getPrivateKey());
    assertThat(signingConfiguration.getOldestSigner().get().getCertificates())
        .containsExactly(oldestSignerConfig.getCertificate());
  }

  @Test
  public void populateLineage_apkFile() throws Exception {
    SigningCertificateLineage.SignerConfig signerConfig =
        new SigningCertificateLineage.SignerConfig.Builder(privateKey, certificate).build();
    SigningCertificateLineage.SignerConfig oldestSignerConfig =
        new SigningCertificateLineage.SignerConfig.Builder(
                oldestSignerPrivateKey, oldestSignerCertificate)
            .build();
    SigningCertificateLineage lineage =
        new SigningCertificateLineage.Builder(oldestSignerConfig, signerConfig).build();

    SignerConfig oldestSigner =
        SignerConfig.builder()
            .setPrivateKey(oldestSignerPrivateKey)
            .setCertificates(ImmutableList.of(oldestSignerCertificate))
            .build();

    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withSigningConfig(
                SigningConfiguration.builder()
                    .setSignerConfig(privateKey, certificate)
                    .setSigningCertificateLineage(lineage)
                    .setOldestSigner(oldestSigner)
                    .build())
            .build());

    File lineageFile = createMinimalistSignedApkFile().toFile();

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--aapt2=" + AAPT2_PATH,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD,
                    "--lineage=" + lineageFile,
                    "--oldest-signer=" + oldestSignerPropertiesPath),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    SigningConfiguration signingConfiguration = commandViaFlags.getSigningConfiguration().get();
    assertThat(signingConfiguration.getSigningCertificateLineage().get().getCertificatesInLineage())
        .containsExactly(oldestSignerCertificate, certificate);
    assertThat(signingConfiguration.getOldestSigner().get().getPrivateKey())
        .isEqualTo(oldestSignerConfig.getPrivateKey());
    assertThat(signingConfiguration.getOldestSigner().get().getCertificates())
        .containsExactly(oldestSignerConfig.getCertificate());
  }

  @Test
  public void populateLineage_invalidFile() {
    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD,
                            "--lineage=" + tmp.newFile(),
                            "--oldest-signer=" + oldestSignerPropertiesPath),
                    fakeAdbServer));

    assertThat(e).hasMessageThat().isEqualTo("The input file is not a valid lineage file.");
  }

  @Test
  public void lineageFlag_noOldestSigner_fails() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD,
                            "--lineage=" + tmpDir.resolve("lineage-file")),
                    fakeAdbServer));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Flag 'oldest-signer' is required when 'lineage' is set.");
  }

  @Test
  public void oldestSignerFlag_noLineage_fails() {
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--aapt2=" + AAPT2_PATH,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD,
                            "--oldest-signer=" + oldestSignerPropertiesPath),
                    fakeAdbServer));

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Flag 'lineage' is required when 'oldest-signer' is set.");
  }

  @Test
  public void settingBothSigningConfigAndSigningConfigProvider_throwsError() {
    SigningConfiguration signingConfig =
        SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();
    ApksigSigningConfiguration apksigSigningConfig =
        ApksigSigningConfiguration.builder()
            .setSignerConfigs(
                ImmutableList.of(
                    SignerConfig.builder()
                        .setPrivateKey(privateKey)
                        .setCertificates(ImmutableList.of(certificate))
                        .build()))
            .build();

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                BuildApksCommand.builder()
                    .setSigningConfiguration(signingConfig)
                    .setSigningConfigurationProvider(apkDescription -> apksigSigningConfig)
                    .build());

    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Only one of SigningConfiguration or SigningConfigurationProvider should be set.");
  }

  @Test
  public void settingBothSdkBundlesAndSdkArchives_fromFlags_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2,
                            "--sdk-archives=" + sdkArchivePath1 + "," + sdkArchivePath2),
                    fakeAdbServer));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Only one of 'sdk-bundles' and 'sdk-archives' flags can be set.");
  }

  @Test
  public void settingBothSdkBundlesAndSdkArchives_fromBuilder_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setRuntimeEnabledSdkBundlePaths(
                        ImmutableSet.of(sdkBundlePath1, sdkBundlePath2))
                    .setRuntimeEnabledSdkArchivePaths(
                        ImmutableSet.of(sdkArchivePath1, sdkArchivePath2))
                    .build());
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Command can only set either runtime-enabled SDK bundles or runtime-enabled SDK"
                + " archives, but both were set.");
  }

  @Test
  public void localDeploymentRuntimeEnabledSdkConfig_withoutSdkDependencies_fromFlags_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--local-runtime-enabled-sdk-config="
                                + localDeploymentRuntimeEnabledSdkConfigPath),
                    fakeAdbServer));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "'local-runtime-enabled-sdk-config' flag can only be set together with 'sdk-bundles' or"
                + " 'sdk-archives' flags.");
  }

  @Test
  public void localDeploymentRuntimeEnabledSdkConfig_withoutSdkDependencies_fromBuilder_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setLocalDeploymentRuntimeEnabledSdkConfig(
                        LocalDeploymentRuntimeEnabledSdkConfig.getDefaultInstance())
                    .build());
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Using --local-deployment-runtime-enabled-sdk-config flag requires either"
                + " --sdk-bundles or --sdk-archives flag to be also present.");
  }

  @Test
  public void sdkBundleFileDoesNotExist_throws() throws Exception {
    // Creating SDK bundle for sdkBundlePath1, but not for SdkBundlePath2.
    createSdkBundle(sdkBundlePath1, "com.test.sdk", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void sdkArchiveFileDoesNotExist_throws() throws Exception {
    // Creating SDK archive for sdkArchivePath1, but not for sdkArchivePath2.
    createSdkArchive(sdkArchivePath1, "com.test.sdk", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1 + "," + sdkArchivePath2),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void badSdkBundleFileExtension_throws() throws Exception {
    Path badSdkBundlePath = tmpDir.resolve("sdk_bundle.aab");
    createSdkBundle(
        badSdkBundlePath, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkBundle(sdkBundlePath1, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk2")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + badSdkBundlePath),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("ASB file 'sdk_bundle.aab' is expected to have '.asb' extension.");
  }

  @Test
  public void badSdkArchiveFileExtension_throws() throws Exception {
    Path badSdkArchivePath = tmpDir.resolve("sdk_archive.aab");
    createSdkArchive(
        badSdkArchivePath, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk2")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1 + "," + badSdkArchivePath),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("ASAR file 'sdk_archive.aab' is expected to have '.asar' extension.");
  }

  @Test
  public void sdkBundlesSetInUnsupportedMode_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1,
                    "--mode=ARCHIVE"),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("runtimeEnabledSdkBundlePaths can not be present in 'ARCHIVE' mode.");
  }

  @Test
  public void sdkArchivessSetInUnsupportedMode_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1,
                    "--mode=ARCHIVE"),
            fakeAdbServer);

    Exception e = assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("runtimeEnabledSdkArchivePaths can not be present in 'ARCHIVE' mode.");
  }

  @Test
  public void sdkBundleZipMissingModulesFile_sdkBundleZipFileValidationFails() throws Exception {
    createZipBuilderForSdkBundle().writeTo(sdkBundlePath1);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "The archive doesn't seem to be an SDK Bundle, it is missing required file"
                + " 'modules.resm'.");
  }

  @Test
  public void modulesZipMissingManifestInsideSdkBundle_modulesZipFileValidationFails()
      throws Exception {
    createZipBuilderForSdkBundleWithModules(
            createZipBuilderForModulesWithoutManifest(), extractedSdkBundleModulesPath)
        .writeTo(sdkBundlePath1);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
  }

  @Test
  public void sdkBundleHasInvalidManifestEntry_sdkBundleValidationFails() throws Exception {
    new SdkBundleSerializer()
        .writeToDisk(
            new SdkBundleBuilder()
                .setModule(
                    new BundleModuleBuilder("base")
                        .setManifest(createInvalidSdkAndroidManifest())
                        .build())
                .build(),
            sdkBundlePath1);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "installLocation' in <manifest> must be 'internalOnly' for SDK bundles if it is set.");
  }

  @Test
  public void modulesZipMissingManifestInAsar_modulesZipFileValidationFails() throws Exception {
    createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModulesWithoutManifest(), extractedSdkBundleModulesPath)
        .writeTo(sdkArchivePath1);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
  }

  @Test
  public void multipleSdkBundlesWithSamePackageName_validationFails() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkBundle(sdkBundlePath2, "com.test.sdk1", /* majorVersion= */ 2, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Received multiple SDK bundles with the same package name: com.test.sdk1");
  }

  @Test
  public void multipleSdkAsarsWithSamePackageName_validationFails() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkArchive(
        sdkArchivePath2, "com.test.sdk1", /* majorVersion= */ 2, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1 + "," + sdkArchivePath2),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Received multiple SDK archives with the same package name: com.test.sdk1");
  }

  @Test
  public void missingSdkBundleInInput_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk2")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(3))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("App bundle depends on SDK 'com.test.sdk2', but no SDK bundle was provided.");
  }

  @Test
  public void appBundleHasSdkDeps_noSdkBundleInInput_modeWithoutSdkRuntimeVariant_succeeds()
      throws Exception {
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());

    BuildApksCommand.fromFlags(
            new FlagParser()
                .parse("--bundle=" + bundlePath, "--output=" + outputFilePath, "--mode=INSTANT"),
            fakeAdbServer)
        .execute();
  }

  @Test
  public void missingSdkArchiveInInput_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk2")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(3))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("App bundle depends on SDK 'com.test.sdk2', but no ASAR was provided.");
  }

  @Test
  public void extraSdkBundleInInput_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkBundle(sdkBundlePath2, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 0);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "App bundle does not depend on SDK 'com.test.sdk2', but SDK bundle was provided.");
  }

  @Test
  public void extraSdkArchiveInInput_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkArchive(
        sdkArchivePath2, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 0);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1 + "," + sdkArchivePath2),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "App bundle does not depend on SDK 'com.test.sdk2', but SDK archive was provided.");
  }

  @Test
  public void sdkBundleMajorVersionMismatchWithAppBundleDependency_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "App bundle depends on SDK 'com.test.sdk1' with major version '2', but provided SDK"
                + " bundle has major version '1'.");
  }

  @Test
  public void sdkArchiveMajorVersionMismatchWithAppBundleDependency_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 3);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(2)
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "App bundle depends on SDK 'com.test.sdk1' with major version '2', but provided SDK"
                + " archive has major version '1'.");
  }

  @Test
  public void sdkBundleMinorVersionMismatchWithAppBundleDependency_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 0, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "App bundle depends on SDK 'com.test.sdk1' with minor version '3', but provided SDK"
                + " bundle has minor version '2'.");
  }

  @Test
  public void sdkArchiveMinorVersionMismatchWithAppBundleDependency_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1, "com.test.sdk1", /* majorVersion= */ 0, /* minorVersion= */ 2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMinor(3)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "App bundle depends on SDK 'com.test.sdk1' with minor version '3', but provided SDK"
                + " archive has minor version '2'.");
  }

  @Test
  public void sdkArchiveCertificateMismatchWithAppBundleDependency_throws() throws Exception {
    createSdkArchive(
        sdkArchivePath1,
        "com.test.sdk1",
        /* majorVersion= */ 1,
        /* minorVersion= */ 2,
        VALID_CERT_FINGERPRINT2);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-archives=" + sdkArchivePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidCommandException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "App bundle depends on SDK 'com.test.sdk1' with signing certificate '"
                + VALID_CERT_FINGERPRINT
                + "', but provided ASAR is for SDK with signing certificate '"
                + VALID_CERT_FINGERPRINT2
                + "'.");
  }

  @Test
  public void validateRuntimeEnabledSdkConfig_missingRequiredField_throws() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(androidManifest("com.app"))
                        .setRuntimeEnabledSdkConfig(
                            RuntimeEnabledSdkConfig.newBuilder()
                                .addRuntimeEnabledSdk(
                                    RuntimeEnabledSdk.newBuilder()
                                        // missing required package name.
                                        .setVersionMajor(1)
                                        .setVersionMinor(2)
                                        .setCertificateDigest(VALID_CERT_FINGERPRINT))
                                .build())
                        .build());
    createAppBundle(bundlePath, appBundle.build());
    BuildApksCommand command =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1),
            fakeAdbServer);

    Exception e = assertThrows(InvalidBundleException.class, command::execute);
    assertThat(e)
        .hasMessageThat()
        .contains("Found dependency on runtime-enabled SDK with an empty package name.");
  }

  @Test
  public void buildApks_fromAppBundleWithRuntimeEnabledSdkDeps_succeeds() throws Exception {
    createSdkBundle(sdkBundlePath1, "com.test.sdk1", /* majorVersion= */ 1, /* minorVersion= */ 2);
    createSdkBundle(sdkBundlePath2, "com.test.sdk2", /* majorVersion= */ 2, /* minorVersion= */ 0);
    createAppBundleWithRuntimeEnabledSdkConfig(
        bundlePath,
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk1")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(2))
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk2")
                    .setVersionMajor(2)
                    .setVersionMinor(0)
                    .setCertificateDigest(VALID_CERT_FINGERPRINT)
                    .setResourcesPackageId(3))
            .build());

    BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--sdk-bundles=" + sdkBundlePath1 + "," + sdkBundlePath2),
            fakeAdbServer)
        .execute();

    BuildApksResult buildApksResult = ResultUtils.readTableOfContents(outputFilePath);
    // createAppBundleWithRuntimeEnabledSdkConfig sets min SDK version to Android L, so 2 split
    // variants should be generated: for sdk-runtime and non-sdk-runtime devices.
    assertThat(buildApksResult.getVariantCount()).isEqualTo(2);

    Variant nonSdkRuntimeVariant = buildApksResult.getVariant(0);
    assertThat(nonSdkRuntimeVariant.getTargeting())
        .isEqualTo(TargetingUtils.variantSdkTargeting(ANDROID_L_API_VERSION));
    // non-sdk-runtime variant contains additional modules - one per SDK dependency.
    assertThat(nonSdkRuntimeVariant.getApkSetCount()).isEqualTo(3);
    assertThat(
            nonSdkRuntimeVariant.getApkSetList().stream()
                .map(ApkSet::getModuleMetadata)
                .map(ModuleMetadata::getName))
        .containsExactly("base", "comtestsdk1", "comtestsdk2");

    Variant sdkRuntimeVariant = buildApksResult.getVariant(1);
    assertThat(sdkRuntimeVariant.getTargeting())
        .isEqualTo(TargetingUtils.sdkRuntimeVariantTargeting());
    // non-sdk-runtime variant contains only the base module - like the original AAB.
    assertThat(sdkRuntimeVariant.getApkSetCount()).isEqualTo(1);
    assertThat(sdkRuntimeVariant.getApkSet(0).getModuleMetadata().getName()).isEqualTo("base");
  }

  @Test
  public void packageStoreWithDefault_throws() throws Exception {
    InvalidCommandException builderException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.builder()
                    .setBundlePath(bundlePath)
                    .setOutputFile(outputFilePath)
                    .setApkBuildMode(DEFAULT)
                    .setAppStorePackageName("my.store")
                    .build());
    assertThat(builderException)
        .hasMessageThat()
        .contains(
            "Providing custom store package is only possible when running with 'archive' mode"
                + " flag.");

    InvalidCommandException flagsException =
        assertThrows(
            InvalidCommandException.class,
            () ->
                BuildApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputFilePath,
                            "--store-package=my.store",
                            "--mode=DEFAULT"),
                    fakeAdbServer));
    assertThat(flagsException)
        .hasMessageThat()
        .contains(
            "Providing custom store package is only possible when running with 'archive' mode"
                + " flag.");
  }

  private void createAppBundle(Path path) throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")).build());
    createAppBundle(path, appBundle.build());
  }

  private void createAppBundle(Path path, AppBundle appBundle) throws Exception {
    new AppBundleSerializer().writeToDisk(appBundle, path);
  }

  private void createAppBundleWithCodeTransparency(Path path, CodeTransparency codeTransparency)
      throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")).build())
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(createJwsToken(JsonFormat.printer().print(codeTransparency)))
                    .asByteSource(Charset.defaultCharset()));
    createAppBundle(path, appBundle.build());
  }

  private void createAppBundleWithRuntimeEnabledSdkConfig(
      Path path, RuntimeEnabledSdkConfig runtimeEnabledSdkConfig) throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(
                            androidManifest("com.app", withMinSdkVersion(ANDROID_L_API_VERSION)))
                        .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
                        .build());
    createAppBundle(path, appBundle.build());
  }

  private void createSdkBundle(Path path, String packageName, int majorVersion, int minorVersion)
      throws Exception {
    new SdkBundleSerializer()
        .writeToDisk(
            new SdkBundleBuilder()
                .setSdkModulesConfig(
                    createSdkModulesConfig()
                        .setSdkPackageName(packageName)
                        .setSdkVersion(
                            RuntimeEnabledSdkVersion.newBuilder()
                                .setMajor(majorVersion)
                                .setMinor(minorVersion))
                        .build())
                .build(),
            path);
  }

  private void createSdkArchive(Path path, String packageName, int majorVersion, int minorVersion)
      throws Exception {
    createSdkArchive(path, packageName, majorVersion, minorVersion, VALID_CERT_FINGERPRINT);
  }

  private void createSdkArchive(
      Path path, String packageName, int majorVersion, int minorVersion, String certificateHash)
      throws Exception {
    createZipBuilderForSdkAsarWithModules(
            createZipBuilderForModules(),
            SdkMetadata.newBuilder()
                .setPackageName(packageName)
                .setSdkVersion(
                    RuntimeEnabledSdkVersion.newBuilder()
                        .setMajor(majorVersion)
                        .setMinor(minorVersion)
                        .setPatch(1))
                .setCertificateDigest(certificateHash)
                .build(),
            /* modulesPath= */ tmpDir.resolve(
                packageName + majorVersion + minorVersion + "-modules.resm"))
        .writeTo(path);
  }

  private Path createKeystorePropertiesFile(
      String keystorePath, String keyAlias, String keystorePassword, String keyPassword)
      throws IOException {
    Properties properties = new Properties();

    properties.setProperty("ks", keystorePath);
    properties.setProperty("ks-key-alias", keyAlias);
    properties.setProperty("ks-pass", "pass:" + keystorePassword);
    properties.setProperty("key-pass", "pass:" + keyPassword);

    File keystorePropertiesFile = tmp.newFile();
    try (OutputStream outputStream = new FileOutputStream(keystorePropertiesFile)) {
      properties.store(outputStream, null);
    }

    return keystorePropertiesFile.toPath();
  }

  /** Create an arbitrary but valid APK file. */
  private Path createMinimalistSignedApkFile() {
    checkState(
        apkSerializer != null,
        "The test must call TestComponent.useTestModule() to inject the required objects.");
    Path outPath = tmp.getRoot().toPath();
    ZipPath zipPath = ZipPath.create("minimalist-apk.apk");
    apkSerializer.serialize(outPath, zipPath.toString(), createMinimalistModuleSplit());
    return outPath.resolve(zipPath.toString());
  }

  private static ModuleSplit createMinimalistModuleSplit() {
    return ModuleSplit.builder()
        .setModuleName(BundleModuleName.create("base"))
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .setMasterSplit(true)
        .build();
  }

  private String createJwsToken(String payload) throws JoseException {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
    jws.setCertificateChainHeaderValue(certificate);
    jws.setPayload(payload);
    jws.setKey(privateKey);
    return jws.getCompactSerialization();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(BuildApksCommandTest test);

    static void useTestModule(BuildApksCommandTest testInstance, TestModule testModule) {
      DaggerBuildApksCommandTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
