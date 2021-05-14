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

import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA384;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.commands.CheckTransparencyCommand.Mode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckTransparencyCommandTest {

  private static final String BASE_MODULE = "base";
  private static final String FEATURE_MODULE1 = "feature1";
  private static final String FEATURE_MODULE2 = "feature2";
  private static final String DEX_PATH1 = "dex/classes.dex";
  private static final String DEX_PATH2 = "dex/classes2.dex";
  private static final String NATIVE_LIB_PATH1 = "lib/arm64-v8a/libnative.so";
  private static final String NATIVE_LIB_PATH2 = "lib/armeabi-v7a/libnative.so";
  private static final byte[] FILE_CONTENT = new byte[] {1, 2, 3};
  private static final Path ADB_PATH =
      Paths.get("third_party/java/android/android_sdk_linux/platform-tools/adb.static");
  private static final String DEVICE_ID = "id1";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path bundlePath;
  private Path apkZipPath;
  private KeyPairGenerator kpg;
  private PrivateKey privateKey;
  private X509Certificate certificate;

  private final AdbServer fakeAdbServer = mock(AdbServer.class);
  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(
          ImmutableMap.of(ANDROID_HOME, "/android/home", ANDROID_SERIAL, DEVICE_ID));

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
    apkZipPath = tmpDir.resolve("apks.zip");
    kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keySize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=CheckTransparencyCommandTest");
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
                .parse("--mode=CONNECTED_DEVICE", "--adb=" + ADB_PATH, "--device-id=" + DEVICE_ID),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_connectedDeviceMode_deviceIdRetrievedFromEnvironmend() {
    CheckTransparencyCommand commandViaFlags =
        CheckTransparencyCommand.fromFlags(
            new FlagParser().parse("--mode=CONNECTED_DEVICE", "--adb=" + ADB_PATH),
            systemEnvironmentProvider,
            fakeAdbServer);
    CheckTransparencyCommand commandViaBuilder =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setAdbPath(ADB_PATH)
            .setAdbServer(fakeAdbServer)
            .setDeviceId(DEVICE_ID)
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
  public void execute_bundletMode_transparencyFileMissing() throws Exception {
    createBundle(bundlePath, /* transparencyMetadata= */ Optional.empty());
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder().setMode(Mode.BUNDLE).setBundlePath(bundlePath).build();

    Throwable e = assertThrows(InvalidBundleException.class, checkTransparencyCommand::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Bundle does not include code transparency metadata. Run `add-transparency` command to"
                + " add code transparency metadata to the bundle.");
  }

  @Test
  public void execute_bundleMode_codeModified() throws Exception {
    CodeTransparency.Builder codeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString())
            .toBuilder();
    Map<String, CodeRelatedFile> codeRelatedFileMap =
        codeTransparency.getCodeRelatedFileList().stream()
            .collect(toMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
    codeRelatedFileMap.put(
        "dex/deleted.dex",
        CodeRelatedFile.newBuilder()
            .setType(CodeRelatedFile.Type.DEX)
            .setPath("dex/deleted.dex")
            .build());
    codeRelatedFileMap.remove(BASE_MODULE + "/" + DEX_PATH1);
    codeRelatedFileMap.put(
        BASE_MODULE + "/" + DEX_PATH2,
        CodeRelatedFile.newBuilder()
            .setType(CodeRelatedFile.Type.DEX)
            .setPath(BASE_MODULE + "/" + DEX_PATH2)
            .setSha256("modifiedSHa256")
            .build());
    codeTransparency.clearCodeRelatedFile().addAllCodeRelatedFile(codeRelatedFileMap.values());
    createBundle(bundlePath, Optional.of(codeTransparency.build()));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .contains(
            "Code transparency verification failed because code was modified after transparency"
                + " metadata generation.");
    assertThat(output)
        .contains("Files deleted after transparency metadata generation: [dex/deleted.dex]");
    assertThat(output)
        .contains("Files added after transparency metadata generation: [base/dex/classes.dex]");
    assertThat(output)
        .contains("Files modified after transparency metadata generation: [base/dex/classes2.dex]");
  }

  @Test
  public void execute_transparencyVerified_invalidSignature() throws Exception {
    // Update certificate value so that it does not match the private key that will used to sign the
    // transparency metadata.
    certificate =
        CertificateFactory.buildSelfSignedCertificate(
            kpg.generateKeyPair(), "CN=CodeTransparencyValidatorTest_WrongCertificate");
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(bundlePath, Optional.of(validCodeTransparency));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setBundlePath(bundlePath)
        .setMode(Mode.BUNDLE)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .contains("Code transparency verification failed because signature is invalid.");
  }

  @Test
  public void execute_bundleMode_transparencyVerified() throws Exception {
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(bundlePath, Optional.of(validCodeTransparency));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CheckTransparencyCommand.builder()
        .setMode(Mode.BUNDLE)
        .setBundlePath(bundlePath)
        .build()
        .checkTransparency(new PrintStream(outputStream));

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .contains("Code transparency verified. Public key certificate fingerprint: ");
  }

  @Test
  public void execute_bundleMode_unsupportedSignatureAlgorithm() throws Exception {
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(
        bundlePath,
        Optional.of(validCodeTransparency),
        /* hasSharedUserId= */ false,
        RSA_USING_SHA384);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Throwable e =
        assertThrows(
            InvalidBundleException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setMode(Mode.BUNDLE)
                    .setBundlePath(bundlePath)
                    .build()
                    .checkTransparency(new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains("Exception while verifying code transparency signature.");
  }

  @Test
  public void execute_bundleMode_transparencyFilePresent_sharedUserIdSpecifiedInManifest()
      throws Exception {
    CodeTransparency validCodeTransparency =
        createValidTransparencyProto(
            ByteSource.wrap(FILE_CONTENT).hash(Hashing.sha256()).toString());
    createBundle(
        bundlePath,
        Optional.of(validCodeTransparency),
        /* hasSharedUserId= */ true,
        RSA_USING_SHA256);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Throwable e =
        assertThrows(
            InvalidBundleException.class,
            () ->
                CheckTransparencyCommand.builder()
                    .setMode(Mode.BUNDLE)
                    .setBundlePath(bundlePath)
                    .build()
                    .checkTransparency(new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Transparency file is present in the bundle, but it can not be verified because"
                + " `sharedUserId` attribute is specified in one of the manifests.");
  }

  @Test
  public void printHelpDoesNotCrash() {
    CheckTransparencyCommand.help();
  }

  private void createBundle(Path path, Optional<CodeTransparency> transparencyMetadata)
      throws Exception {
    createBundle(path, transparencyMetadata, /* hasSharedUserId= */ false, RSA_USING_SHA256);
  }

  private void createBundle(
      Path path,
      Optional<CodeTransparency> transparencyMetadata,
      boolean hasSharedUserId,
      String algorithmIdentifier)
      throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(BASE_MODULE, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE1, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE2, module -> addCodeFilesToBundleModule(module, hasSharedUserId));
    if (transparencyMetadata.isPresent()) {
      appBundle.addMetadataFile(
          BundleMetadata.BUNDLETOOL_NAMESPACE,
          BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
          CharSource.wrap(
                  createJwsToken(
                      JsonFormat.printer().print(transparencyMetadata.get()), algorithmIdentifier))
              .asByteSource(Charset.defaultCharset()));
    }
    new AppBundleSerializer().writeToDisk(appBundle.build(), path);
  }

  private static BundleModule addCodeFilesToBundleModule(
      BundleModuleBuilder module, boolean hasSharedUserId) {
    XmlNode manifest =
        hasSharedUserId
            ? androidManifest("com.test.app", withSharedUserId("sharedUserId"))
            : androidManifest("com.test.app");
    return module
        .setManifest(manifest)
        .addFile(DEX_PATH1, FILE_CONTENT)
        .addFile(DEX_PATH2, FILE_CONTENT)
        .addFile(NATIVE_LIB_PATH1, FILE_CONTENT)
        .addFile(NATIVE_LIB_PATH2, FILE_CONTENT)
        .build();
  }

  private static CodeTransparency createValidTransparencyProto(String fileContentHash) {
    CodeTransparency.Builder transparencyBuilder = CodeTransparency.newBuilder();
    addCodeFilesToTransparencyProto(transparencyBuilder, BASE_MODULE, fileContentHash);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE1, fileContentHash);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE2, fileContentHash);
    return transparencyBuilder.build();
  }

  private static void addCodeFilesToTransparencyProto(
      CodeTransparency.Builder transparencyBuilder, String moduleName, String fileContentHash) {
    transparencyBuilder
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX_PATH1)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX_PATH2)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH1)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH1)
                .setSha256(fileContentHash)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH2)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH2)
                .setSha256(fileContentHash)
                .build());
  }

  private String createJwsToken(String payload, String algorithmIdentifier) throws JoseException {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(algorithmIdentifier);
    jws.setCertificateChainHeaderValue(certificate);
    jws.setPayload(payload);
    jws.setKey(privateKey);
    return jws.getCompactSerialization();
  }
}
