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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.CodeTransparencyTestUtils.createJwsToken;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA384;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.CheckTransparencyCommand.Mode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.ApkSerializerHelper;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.TestModule;
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils;
import com.android.tools.build.bundletool.transparency.CodeTransparencyVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.ByteString;
import dagger.Component;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.inject.Inject;
import org.jose4j.jws.JsonWebSignature;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckTransparencyCommandTest {

  private static final Path ADB_PATH =
      Paths.get("third_party/java/android/android_sdk_linux/platform-tools/adb.static");
  private static final String DEVICE_ID = "id1";
  private static final String PACKAGE_NAME = "com.test.app";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Inject ApkSerializerHelper apkSerializerHelper;

  private Path tmpDir;
  private Path bundlePath;
  private Path apkZipPath;
  private KeyPairGenerator kpg;
  private PrivateKey transparencyPrivateKey;
  private X509Certificate transparencyKeyCertificate;
  private X509Certificate apkSigningKeyCertificate;
  private Path transparencyKeyCertificatePath;
  private Path apkSigningKeyCertificatePath;

  private final AdbServer fakeAdbServer = mock(AdbServer.class);
  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(
          ImmutableMap.of(ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID));

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keySize= */ 3072);

    KeyPair keyPair = kpg.genKeyPair();
    transparencyPrivateKey = keyPair.getPrivate();
    transparencyKeyCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            keyPair, "CN=CheckTransparencyCommandTest_TransparencyKey");
    transparencyKeyCertificatePath = tmpDir.resolve("transparency-public.cert");
    Files.write(transparencyKeyCertificatePath, transparencyKeyCertificate.getEncoded());

    KeyPair apkSigningKeyPair = kpg.genKeyPair();
    apkSigningKeyCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            apkSigningKeyPair, "CN=CheckTransparencyCommandTest_ApkSigningKey");
    apkSigningKeyCertificatePath = tmpDir.resolve("apk-signing-key-public.cert");
    Files.write(apkSigningKeyCertificatePath, apkSigningKeyCertificate.getEncoded());
    SigningConfiguration apkSigningConfig =
        SigningConfiguration.builder()
            .setSignerConfig(
                SignerConfig.builder()
                    .setPrivateKey(apkSigningKeyPair.getPrivate())
                    .setCertificates(ImmutableList.of(apkSigningKeyCertificate))
                    .build())
            .build();

    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withCustomBuildApksCommandSetter(command -> command.setEnableNewApkSerializer(true))
            .withSigningConfig(apkSigningConfig)
            .build());

    bundlePath = tmpDir.resolve("bundle.aab");
    apkZipPath = tmpDir.resolve("apks.zip");
  }

  @Test
  public void buildingCommandViaFlags_modeNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser().parse(), systemEnvironmentProvider, fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Missing the required --mode flag");
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_bundlePathNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser().parse("--mode=BUNDLE"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Missing the required --bundle flag");
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_adbPathSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse("--mode=BUNDLE", "--bundle=" + bundlePath, "--adb=path/to/adb"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_unknownFlagSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse("--mode=BUNDLE", "--bundle=" + bundlePath, "--unknownFlag=hello"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlagsAndBuilderHasSameResult_bundleMode() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser().parse("--mode=BUNDLE", "--bundle=" + bundlePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder().setMode(Mode.BUNDLE).setBundlePath(bundlePath).build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_withTransparencyCertificateFlag() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=BUNDLE",
                    "--bundle=" + bundlePath,
                    "--transparency-key-certificate=" + transparencyKeyCertificatePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.BUNDLE)
            .setBundlePath(bundlePath)
            .setTransparencyKeyCertificate(transparencyKeyCertificate)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_withTransparencyCertificateFlag_invalidFormat() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--mode=BUNDLE",
                            "--bundle=" + bundlePath,
                            "--transparency-key-certificate=" + apkZipPath),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e)
        .hasMessageThat()
        .contains("Unable to read public key certificate from the provided path.");
  }

  @Test
  public void buildingCommandViaFlags_bundleMode_withApkSigningKeyCertificateFlag() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--mode=BUNDLE",
                            "--bundle=" + bundlePath,
                            "--apk-signing-key-certificate=" + apkSigningKeyCertificatePath),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags: --apk-signing-key-certificate");
  }

  @Test
  public void buildingCommandViaFlags_apkMode_apkZipPathNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser().parse("--mode=APK"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Missing the required --apk-zip flag");
  }

  @Test
  public void buildingCommandViaFlags_apkMode_adbPathSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse("--mode=APK", "--apk-zip=" + apkZipPath, "--adb=path/to/adb"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlags_apkMode_unknownFlagSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse("--mode=APK", "--apk-zip=" + bundlePath, "--unknownFlag=hello"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlagsAndBuilderHasSameResult_apkMode() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser().parse("--mode=APK", "--apk-zip=" + apkZipPath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(apkZipPath).build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_apkMode_withTransparencyCertificateFlag() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=APK",
                    "--apk-zip=" + apkZipPath,
                    "--transparency-key-certificate=" + transparencyKeyCertificatePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.APK)
            .setApkZipPath(apkZipPath)
            .setTransparencyKeyCertificate(transparencyKeyCertificate)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_apkMode_withApkSigningKeyCertificateFlag() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=APK",
                    "--apk-zip=" + apkZipPath,
                    "--apk-signing-key-certificate=" + apkSigningKeyCertificatePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.APK)
            .setApkZipPath(apkZipPath)
            .setApkSigningKeyCertificate(apkSigningKeyCertificate)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_bundleFlagSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--mode=CONNECTED_DEVICE",
                            "--adb=" + ADB_PATH,
                            "--package-name=" + PACKAGE_NAME,
                            "--bundle=" + bundlePath),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_unknownFlagSet() {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                CheckTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--mode=CONNECTED_DEVICE",
                            "--connected-device=true",
                            "--adb=" + ADB_PATH,
                            "--package-name=" + PACKAGE_NAME,
                            "--unknownFlag=hello"),
                    systemEnvironmentProvider,
                    fakeAdbServer));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlagsAndBuilderHasSameResult_connectedDeviceMode() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=CONNECTED_DEVICE",
                    "--adb=" + ADB_PATH,
                    "--device-id=" + DEVICE_ID,
                    "--package-name=" + PACKAGE_NAME),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
            .setPackageName(PACKAGE_NAME)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_withTransparencyCertificateFlag() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=CONNECTED_DEVICE",
                    "--adb=" + ADB_PATH,
                    "--device-id=" + DEVICE_ID,
                    "--package-name=" + PACKAGE_NAME,
                    "--transparency-key-certificate=" + transparencyKeyCertificatePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
            .setPackageName(PACKAGE_NAME)
            .setTransparencyKeyCertificate(transparencyKeyCertificate)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_withApkSigningKeyCertificateFlag() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=CONNECTED_DEVICE",
                    "--adb=" + ADB_PATH,
                    "--device-id=" + DEVICE_ID,
                    "--package-name=" + PACKAGE_NAME,
                    "--apk-signing-key-certificate=" + apkSigningKeyCertificatePath),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
            .setPackageName(PACKAGE_NAME)
            .setApkSigningKeyCertificate(apkSigningKeyCertificate)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_deviceIdRetrievedFromEnvironment() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--mode=CONNECTED_DEVICE",
                    "--adb=" + ADB_PATH,
                    "--package-name=" + PACKAGE_NAME),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
            .setPackageName(PACKAGE_NAME)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void execute_bundleMode_bundleNotFound() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setMode(Mode.BUNDLE).setBundlePath(bundlePath).build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void execute_bundleMode_wrongInputFileFormat() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.BUNDLE)
            .setBundlePath(tmpDir.resolve("bundle.txt"))
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("expected to have '.aab' extension.");
  }

  @Test
  public void execute_apkMode_fileNotFound() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(apkZipPath).build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void execute_apkMode_wrongInputFileFormat() {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.APK)
            .setApkZipPath(tmpDir.resolve("apks.txt"))
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, checkTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("expected to have '.zip' extension.");
  }

  @Test
  public void bundleMode_unsupportedCodeTransparencyVersion() throws Exception {
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion() + 1)
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Throwable e =
        assertThrows(
            IllegalStateException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setMode(Mode.BUNDLE)
                    .setBundlePath(bundlePath)
                    .setTransparencyKeyCertificate(transparencyKeyCertificate)
                    .build()
                    .checkTransparency(new PrintStream(outputStream)));
    assertThat(e).hasMessageThat().contains("Code transparency file has unsupported version.");
  }

  @Test
  public void bundleMode_unsupportedSignatureAlgorithm() throws Exception {
    String serializedJws =
        createJwsToken(
            CodeTransparency.getDefaultInstance(),
            transparencyKeyCertificate,
            transparencyPrivateKey,
            RSA_USING_SHA384);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);

    Throwable e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setMode(Mode.BUNDLE)
                    .setBundlePath(bundlePath)
                    .build()
                    .checkTransparency(new PrintStream(new ByteArrayOutputStream())));
    assertThat(e)
        .hasMessageThat()
        .contains("Exception while verifying code transparency signature.");
  }

  @Test
  public void bundleMode_transparencyVerified_transparencyKeyCertificateProvidedByUser()
      throws Exception {
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .setTransparencyKeyCertificate(transparencyKeyCertificate)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output).contains("No APK present. APK signature was not checked.");
    assertThat(output)
        .contains(
            "Code transparency signature verified for the provided code transparency key"
                + " certificate.");
    assertThat(output)
        .contains(
            "Code transparency verified: code related file contents match the code transparency"
                + " file.");
  }

  @Test
  public void bundleMode_transparencyVerified_codeTransparencyVersionNotSet() throws Exception {
    String serializedJws =
        createJwsToken(
            CodeTransparency.getDefaultInstance(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .setTransparencyKeyCertificate(transparencyKeyCertificate)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output).contains("No APK present. APK signature was not checked.");
    assertThat(output)
        .contains(
            "Code transparency signature verified for the provided code transparency key"
                + " certificate.");
    assertThat(output)
        .contains(
            "Code transparency verified: code related file contents match the code transparency"
                + " file.");
  }

  @Test
  public void bundleMode_verificationFailed_badCertificateProvidedByUser() throws Exception {
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    X509Certificate badCertificate =
        CertificateFactory.buildSelfSignedCertificate(
            kpg.generateKeyPair(), "CN=CheckTransparencyCommandTest_BadCertificate");

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .setTransparencyKeyCertificate(badCertificate)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output).contains("No APK present. APK signature was not checked.");
    assertThat(output)
        .contains(
            "Code transparency verification failed because the provided public key certificate does"
                + " not match the code transparency file.");
    assertThat(output)
        .contains(
            "SHA-256 fingerprint of the certificate that was used to sign code"
                + " transparency file: "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(
                    transparencyKeyCertificate));
    assertThat(output)
        .contains(
            "SHA-256 fingerprint of the certificate that was provided: "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(badCertificate));
  }

  @Test
  public void bundleMode_verificationFailed_transparencyKeyCertificateNotProvidedByUser()
      throws Exception {
    // The public key transparencyKeyCertificate used to create JWS does not match the private key,
    // and will result
    // in signature verification failure later.
    String serializedJws =
        createJwsToken(
            CodeTransparency.getDefaultInstance(),
            CertificateFactory.buildSelfSignedCertificate(
                kpg.generateKeyPair(), "CN=CheckTransparencyCommandTest_BadCertificate"),
            transparencyPrivateKey);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.test.app")))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), bundlePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output).contains("No APK present. APK signature was not checked.");
    assertThat(output)
        .contains("Verification failed because code transparency signature is invalid.");
  }

  @Test
  public void apkMode_transparencyVerified_unsupportedCodeTransparencyVersion() throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    Path zipOfApksPath = tmpDir.resolve("apks.zip");
    String dexFileName = "classes.dex";
    byte[] dexFileContents = new byte[] {1, 2, 3};
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion() + 1)
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(CodeRelatedFile.Type.DEX)
                        .setPath("base/dex/" + dexFileName)
                        .setSha256(
                            ByteSource.wrap(dexFileContents).hash(Hashing.sha256()).toString())
                        .build())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    ModuleSplit baseModuleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("").resolve(dexFileName))
                    .setContent(ByteSource.wrap(dexFileContents))
                    .build())
            .addEntry(
                ModuleEntry.builder()
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .setContent(
                        CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()))
                    .build())
            .build();
    apkSerializerHelper.writeToZipFile(baseModuleSplit, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Throwable e =
        assertThrows(
            IllegalStateException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setMode(Mode.APK)
                    .setApkZipPath(zipOfApksPath)
                    .build()
                    .checkTransparency(new PrintStream(outputStream)));
    assertThat(e).hasMessageThat().contains("Code transparency file has unsupported version.");
  }

  @Test
  public void apkMode_transparencyVerified_transparencyKeyCertificateNotProvidedByUser()
      throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    Path zipOfApksPath = tmpDir.resolve("apks.zip");
    String dexFileName = "classes.dex";
    byte[] dexFileContents = new byte[] {1, 2, 3};
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(CodeRelatedFile.Type.DEX)
                        .setPath("base/dex/" + dexFileName)
                        .setSha256(
                            ByteSource.wrap(dexFileContents).hash(Hashing.sha256()).toString())
                        .build())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    ModuleSplit baseModuleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("").resolve(dexFileName))
                    .setContent(ByteSource.wrap(dexFileContents))
                    .build())
            .addEntry(
                ModuleEntry.builder()
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .setContent(
                        CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()))
                    .build())
            .build();
    apkSerializerHelper.writeToZipFile(baseModuleSplit, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.APK)
        .setApkZipPath(zipOfApksPath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .contains(
            "APK signature is valid. SHA-256 fingerprint of the apk signing key certificate (must"
                + " be compared with the developer's public key manually): "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(apkSigningKeyCertificate));
    assertThat(output)
        .contains(
            "Code transparency signature is valid. SHA-256 fingerprint of the code transparency key"
                + " certificate (must be compared with the developer's public key manually): "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(
                    (JsonWebSignature) JsonWebSignature.fromCompactSerialization(serializedJws)));
    assertThat(output)
        .contains(
            "Code transparency verified: code related file contents match the code transparency"
                + " file.");
  }

  @Test
  public void apkMode_transparencyVerified_apkSigningKeyCertificateProvidedByUser()
      throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    Path zipOfApksPath = tmpDir.resolve("apks.zip");
    String dexFileName = "classes.dex";
    byte[] dexFileContents = new byte[] {1, 2, 3};
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(CodeRelatedFile.Type.DEX)
                        .setPath("base/dex/" + dexFileName)
                        .setSha256(
                            ByteSource.wrap(dexFileContents).hash(Hashing.sha256()).toString())
                        .build())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    ModuleSplit baseModuleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("").resolve(dexFileName))
                    .setContent(ByteSource.wrap(dexFileContents))
                    .build())
            .addEntry(
                ModuleEntry.builder()
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .setContent(
                        CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()))
                    .build())
            .build();
    apkSerializerHelper.writeToZipFile(baseModuleSplit, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.APK)
        .setApkZipPath(zipOfApksPath)
        .setApkSigningKeyCertificate(apkSigningKeyCertificate)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .contains("APK signature verified for the provided apk signing key certificate.");
    assertThat(output)
        .contains(
            "Code transparency signature is valid. SHA-256 fingerprint of the code transparency key"
                + " certificate (must be compared with the developer's public key manually): "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(
                    (JsonWebSignature) JsonWebSignature.fromCompactSerialization(serializedJws)));
    assertThat(output)
        .contains(
            "Code transparency verified: code related file contents match the code transparency"
                + " file.");
  }

  @Test
  public void apkMode_transparencyVerified_unspecifiedTypeForDexFiles() throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    Path zipOfApksPath = tmpDir.resolve("apks.zip");
    String dexFileName = "classes.dex";
    byte[] dexFileContents = new byte[] {1, 2, 3};
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(CodeRelatedFile.Type.TYPE_UNSPECIFIED)
                        .setPath("base/dex/" + dexFileName)
                        .setSha256(
                            ByteSource.wrap(dexFileContents).hash(Hashing.sha256()).toString())
                        .build())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    ModuleSplit baseModuleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("").resolve(dexFileName))
                    .setContent(ByteSource.wrap(dexFileContents))
                    .build())
            .addEntry(
                ModuleEntry.builder()
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .setContent(
                        CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()))
                    .build())
            .build();
    apkSerializerHelper.writeToZipFile(baseModuleSplit, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.APK)
        .setApkZipPath(zipOfApksPath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .contains(
            "APK signature is valid. SHA-256 fingerprint of the apk signing key certificate (must"
                + " be compared with the developer's public key manually): "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(apkSigningKeyCertificate));
    assertThat(output)
        .contains(
            "Code transparency signature is valid. SHA-256 fingerprint of the code transparency key"
                + " certificate (must be compared with the developer's public key manually): "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(
                    (JsonWebSignature) JsonWebSignature.fromCompactSerialization(serializedJws)));
    assertThat(output)
        .contains(
            "Code transparency verified: code related file contents match the code transparency"
                + " file.");
  }

  @Test
  public void apkMode_verificationFailed_apkSigningKeyCertificateMismatch() throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    Path zipOfApksPath = tmpDir.resolve("apks.zip");
    String dexFileName = "classes.dex";
    byte[] dexFileContents = new byte[] {1, 2, 3};
    String serializedJws =
        createJwsToken(
            CodeTransparency.newBuilder()
                .setVersion(CodeTransparencyVersion.getCurrentVersion())
                .addCodeRelatedFile(
                    CodeRelatedFile.newBuilder()
                        .setType(CodeRelatedFile.Type.DEX)
                        .setPath("base/dex/" + dexFileName)
                        .setSha256(
                            ByteSource.wrap(dexFileContents).hash(Hashing.sha256()).toString())
                        .build())
                .build(),
            transparencyKeyCertificate,
            transparencyPrivateKey);
    ModuleSplit baseModuleSplit =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true)
            .addEntry(
                ModuleEntry.builder()
                    .setPath(ZipPath.create("").resolve(dexFileName))
                    .setContent(ByteSource.wrap(dexFileContents))
                    .build())
            .addEntry(
                ModuleEntry.builder()
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .setContent(
                        CharSource.wrap(serializedJws).asByteSource(Charset.defaultCharset()))
                    .build())
            .build();
    apkSerializerHelper.writeToZipFile(baseModuleSplit, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    X509Certificate providedApkSigningKeyCert =
        CertificateFactory.buildSelfSignedCertificate(kpg.genKeyPair(), "CN=BadApkSigningCert");

    CheckTransparencyCommand.builder()
        .setMode(Mode.APK)
        .setApkZipPath(zipOfApksPath)
        .setApkSigningKeyCertificate(providedApkSigningKeyCert)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .contains(
            "APK signature verification failed because the provided public key certificate does"
                + " not match the APK signature."
                + "\nSHA-256 fingerprint of the certificate that was used to sign the APKs: "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(apkSigningKeyCertificate)
                + "\nSHA-256 fingerprint of the certificate that was provided: "
                + CodeTransparencyCryptoUtils.getCertificateFingerprint(providedApkSigningKeyCert));
  }

  @Test
  public void printHelpDoesNotCrash() {
    CheckTransparencyCommand.help();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {

    void inject(CheckTransparencyCommandTest test);

    static void useTestModule(CheckTransparencyCommandTest testInstance, TestModule testModule) {
      DaggerCheckTransparencyCommandTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
