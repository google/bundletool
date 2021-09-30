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
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.DEFAULT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.SystemApkOption.UNCOMPRESSED_NATIVE_LIBRARIES;
import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
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
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.SigningCertificateLineage.SignerConfig;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.StandaloneApkSerializer;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import dagger.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
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

  private final AdbServer fakeAdbServer = mock(AdbServer.class);
  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(
          ImmutableMap.of(ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID));

  @Inject StandaloneApkSerializer standaloneApkSerializer;

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

    // KeyStore.
    keystorePath = tmpDir.resolve("keystore.jks");
    createKeyStore(keystorePath, KEYSTORE_PASSWORD);
    addKeyToKeyStore(
        keystorePath, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, privateKey, certificate);
    addKeyToKeyStore(
        keystorePath,
        KEYSTORE_PASSWORD,
        STAMP_KEY_ALIAS,
        STAMP_KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);

    // Stamp KeyStore.
    stampKeystorePath = tmpDir.resolve("stamp-keystore.jks");
    createKeyStore(stampKeystorePath, STAMP_KEYSTORE_PASSWORD);
    addKeyToKeyStore(
        stampKeystorePath,
        STAMP_KEYSTORE_PASSWORD,
        STAMP_KEY_ALIAS,
        STAMP_KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);
    addKeyToKeyStore(
        stampKeystorePath,
        STAMP_KEYSTORE_PASSWORD,
        KEY_ALIAS,
        KEY_PASSWORD,
        stampPrivateKey,
        stampCertificate);

    // Oldest signer KeyStore.
    Path oldestSignerKeystorePath = tmpDir.resolve("oldest-signer-keystore.jks");
    createKeyStore(oldestSignerKeystorePath, OLDEST_SIGNER_KEYSTORE_PASSWORD);
    addKeyToKeyStore(
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

    fakeAdbServer.init(Paths.get("path/to/adb"));
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

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
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
  public void keyStoreFlags_keyAliasNotSet() {
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
  public void keyStoreFlags_keyStoreNotSet() {
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
    createDebugKeystore(debugKeystorePath);

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

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
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

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
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

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
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
    SigningConfiguration signingConfiguration = createDebugKeystore(debugKeystorePath);
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

    BuildApksCommand.Builder commandViaBuilder =
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
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
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

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
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
  public void badTransparencyFile_throws() throws Exception {
    createAppBundle(
        bundlePath,
        Optional.of(
            CodeTransparency.newBuilder()
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(DEX)
                        .setApkPath("non/existent/path/dex.file")
                        .setSha256("sha-256"))
                .build()));

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
    SignerConfig signerConfig = new SignerConfig.Builder(privateKey, certificate).build();
    SignerConfig oldestSignerConfig =
        new SignerConfig.Builder(oldestSignerPrivateKey, oldestSignerCertificate).build();
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
    SignerConfig signerConfig = new SignerConfig.Builder(privateKey, certificate).build();
    SignerConfig oldestSignerConfig =
        new SignerConfig.Builder(oldestSignerPrivateKey, oldestSignerCertificate).build();
    SigningCertificateLineage lineage =
        new SigningCertificateLineage.Builder(oldestSignerConfig, signerConfig).build();

    com.android.tools.build.bundletool.model.SignerConfig oldestSigner =
        com.android.tools.build.bundletool.model.SignerConfig.builder()
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

  private void createAppBundle(Path path) throws Exception {
    createAppBundle(path, /* codeTransparency= */ Optional.empty());
  }

  private void createAppBundle(Path path, Optional<CodeTransparency> codeTransparency)
      throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")).build());
    if (codeTransparency.isPresent()) {
      appBundle.addMetadataFile(
          BundleMetadata.BUNDLETOOL_NAMESPACE,
          BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
          CharSource.wrap(createJwsToken(JsonFormat.printer().print(codeTransparency.get())))
              .asByteSource(Charset.defaultCharset()));
    }
    new AppBundleSerializer().writeToDisk(appBundle.build(), path);
  }

  private static void createKeyStore(Path keystorePath, String keystorePassword)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, keystorePassword.toCharArray());
    keystore.store(new FileOutputStream(keystorePath.toFile()), keystorePassword.toCharArray());
  }

  private static void addKeyToKeyStore(
      Path keystorePath,
      String keystorePassword,
      String keyAlias,
      String keyPassword,
      PrivateKey privateKey,
      X509Certificate certificate)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(new FileInputStream(keystorePath.toFile()), keystorePassword.toCharArray());
    keystore.setKeyEntry(
        keyAlias, privateKey, keyPassword.toCharArray(), new Certificate[] {certificate});
    keystore.store(new FileOutputStream(keystorePath.toFile()), keystorePassword.toCharArray());
  }

  private static SigningConfiguration createDebugKeystore(Path path) throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    X509Certificate certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=Android Debug,O=Android,C=US");
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, DEBUG_KEYSTORE_PASSWORD.toCharArray());
    keystore.setKeyEntry(
        DEBUG_KEY_ALIAS,
        privateKey,
        DEBUG_KEY_PASSWORD.toCharArray(),
        new Certificate[] {certificate});
    keystore.store(new FileOutputStream(path.toFile()), DEBUG_KEYSTORE_PASSWORD.toCharArray());
    return SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();
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
        standaloneApkSerializer != null,
        "The test must call TestComponent.useTestModule() to inject the required objects.");
    Path outPath = tmp.getRoot().toPath();
    ZipPath zipPath = ZipPath.create("minimalist-apk.apk");
    standaloneApkSerializer.writeToDisk(createMinimalistModuleSplit(), outPath, zipPath);
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
