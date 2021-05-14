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

import static com.android.tools.build.bundletool.commands.AddTransparencyCommand.MIN_RSA_KEY_LENGTH;
import static com.android.tools.build.bundletool.model.BundleMetadata.BUNDLETOOL_NAMESPACE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.protobuf.util.JsonFormat;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.zip.ZipFile;
import org.jose4j.jws.JsonWebSignature;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddTransparencyCommandTest {

  private static final String BASE_MODULE = "base";
  private static final String FEATURE_MODULE1 = "feature1";
  private static final String FEATURE_MODULE2 = "feature2";
  private static final String DEX1 = "dex/classes.dex";
  private static final String DEX2 = "dex/classes2.dex";
  private static final String NATIVE_LIB_PATH1 = "lib/arm64-v8a/libnative.so";
  private static final String NATIVE_LIB_PATH2 = "lib/armeabi-v7a/libnative.so";
  private static final byte[] FILE_CONTENT_DEX1 = new byte[] {1, 2, 3};
  private static final byte[] FILE_CONTENT_DEX2 = new byte[] {2, 3, 4};
  private static final byte[] FILE_CONTENT_NATIVE_LIB1 = new byte[] {3, 4, 5};
  private static final byte[] FILE_CONTENT_NATIVE_LIB2 = new byte[] {4, 5, 6};
  private static final String RES_FILE = "res/image.png";
  private static final String NON_CODE_FILE_IN_LIB_DIRECTORY = "lib/wrap.sh";
  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private Path bundlePath;
  private Path outputPath;
  private Path keystorePath;
  private SignerConfig signerConfig;
  private String fileHashDex1;
  private String fileHashDex2;
  private String fileHashNativeLib1;
  private String fileHashNativeLib2;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle.aab");
    outputPath = tmpDir.resolve("bundle_with_transparency.aab");
    keystorePath = tmpDir.resolve("keystore.jks");
    createRsaKeySignerConfig(keystorePath, MIN_RSA_KEY_LENGTH);
    fileHashDex1 = ByteSource.wrap(FILE_CONTENT_DEX1).hash(Hashing.sha256()).toString();
    fileHashDex2 = ByteSource.wrap(FILE_CONTENT_DEX2).hash(Hashing.sha256()).toString();
    fileHashNativeLib1 =
        ByteSource.wrap(FILE_CONTENT_NATIVE_LIB1).hash(Hashing.sha256()).toString();
    fileHashNativeLib2 =
        ByteSource.wrap(FILE_CONTENT_NATIVE_LIB2).hash(Hashing.sha256()).toString();
  }

  @Test
  public void buildingCommandViaFlags_bundlePathNotSet() throws Exception {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                AddTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--output=" + outputPath,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD)));
    assertThat(e).hasMessageThat().contains("Missing the required --bundle flag");
  }

  @Test
  public void buildingCommandViaFlags_outputPathNotSet() throws Exception {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                AddTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD)));
    assertThat(e).hasMessageThat().contains("Missing the required --output flag");
  }

  @Test
  public void buildingCommandViaFlags_keystoreNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                AddTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputPath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD)));
    assertThat(e).hasMessageThat().contains("Missing the required --ks flag");
  }

  @Test
  public void buildingCommandViaFlags_keyAliasNotSet() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                AddTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputPath,
                            "--ks=" + keystorePath,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD)));
    assertThat(e).hasMessageThat().contains("Missing the required --ks-key-alias flag");
  }

  @Test
  public void buildingCommandViaFlags_unknownFlag() throws Exception {
    Throwable e =
        assertThrows(
            UnknownFlagsException.class,
            () ->
                AddTransparencyCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--bundle=" + bundlePath,
                            "--output=" + outputPath,
                            "--ks=" + keystorePath,
                            "--ks-key-alias=" + KEY_ALIAS,
                            "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                            "--key-pass=pass:" + KEY_PASSWORD,
                            "--unknownFlag=hello")));
    assertThat(e).hasMessageThat().contains("Unrecognized flags");
  }

  @Test
  public void buildingCommandViaFlagsAndBuilderHasSameResult() throws Exception {
    AddTransparencyCommand commandViaFlags =
        AddTransparencyCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputPath,
                    "--ks=" + keystorePath,
                    "--ks-key-alias=" + KEY_ALIAS,
                    "--ks-pass=pass:" + KEYSTORE_PASSWORD,
                    "--key-pass=pass:" + KEY_PASSWORD));
    AddTransparencyCommand commandViaBuilder =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void execute_bundleNotFound() throws Exception {
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("not found");
  }

  @Test
  public void execute_wrongInputFileFormat() throws Exception {
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(tmpDir.resolve("bundle.txt"))
            .setSignerConfig(signerConfig)
            .setOutputPath(outputPath)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("expected to have '.aab' extension.");
  }

  @Test
  public void execute_wrongOutputFileFormat() throws Exception {
    createBundle(bundlePath);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(tmpDir.resolve("bundle.txt"))
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("expected to have '.aab' extension.");
  }

  @Test
  public void execute_outputFileAlreadyExists() throws Exception {
    createBundle(bundlePath);
    createBundle(outputPath);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e).hasMessageThat().contains("already exists");
  }

  @Test
  public void execute_sharedUserIdSpecifiedInManifest() throws Exception {
    createBundle(bundlePath, /* hasSharedUserId= */ true);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(InvalidBundleException.class, addTransparencyCommand::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "Transparency can not be added because `sharedUserId` attribute is specified in one of"
                + " the manifests.");
  }

  @Test
  public void execute_unsupportedKeyLength() throws Exception {
    createBundle(bundlePath);
    createRsaKeySignerConfig(keystorePath, /* keySize= */ 1024);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Minimum required key length is 3072 bits, but 1024 bit key was provided.");
  }

  @Test
  public void execute_unsupportedAlgorithm() throws Exception {
    createBundle(bundlePath);
    createSignerConfigWithUnsupportedAlgorithm(keystorePath, /* keySize= */ 1024);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    Throwable e = assertThrows(IllegalArgumentException.class, addTransparencyCommand::execute);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Transparency signing key must be an RSA key, but DSA key was provided.");
  }

  @Test
  public void execute_success() throws Exception {
    createBundle(bundlePath);
    AddTransparencyCommand addTransparencyCommand =
        AddTransparencyCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputPath(outputPath)
            .setSignerConfig(signerConfig)
            .build();

    addTransparencyCommand.execute();

    AppBundle outputBundle = AppBundle.buildFromZip(new ZipFile(outputPath.toFile()));
    Optional<ByteSource> signedTransparencyFile =
        outputBundle
            .getBundleMetadata()
            .getFileAsByteSource(
                BUNDLETOOL_NAMESPACE, BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME);
    assertThat(signedTransparencyFile).isPresent();
    JsonWebSignature jws =
        (JsonWebSignature)
            JsonWebSignature.fromCompactSerialization(
                signedTransparencyFile.get().asCharSource(Charset.defaultCharset()).read());
    assertThat(jws.getAlgorithmHeaderValue()).isEqualTo(RSA_USING_SHA256);
    assertThat(jws.getCertificateChainHeaderValue()).isEqualTo(signerConfig.getCertificates());
    // jws.getPayload method will do signature verification using the public key set below.
    jws.setKey(signerConfig.getCertificates().get(0).getPublicKey());
    CodeTransparency transparencyProto = getTransparencyProto(jws.getPayload());
    assertThat(transparencyProto)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedTransparencyProto());
  }

  @Test
  public void printHelpDoesNotCrash() {
    AddTransparencyCommand.help();
  }

  private static void createBundle(Path path) throws Exception {
    createBundle(path, /* hasSharedUserId= */ false);
  }

  private static void createBundle(Path path, boolean hasSharedUserId) throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(BASE_MODULE, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE1, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .addModule(
                FEATURE_MODULE2, module -> addCodeFilesToBundleModule(module, hasSharedUserId))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, path);
  }

  private void createSignerConfigWithUnsupportedAlgorithm(Path keystorePath, int keySize)
      throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
    kpg.initialize(keySize);
    KeyPair keyPair = kpg.genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    Certificate certificate =
        CertificateFactory.buildSelfSignedDSACertificate(keyPair, "CN=AddTransparencyCommandTest");
    createSignerConfig(keystorePath, privateKey, certificate);
  }

  private void createRsaKeySignerConfig(Path keystorePath, int keySize) throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(keySize);
    KeyPair keyPair = kpg.genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    Certificate certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=AddTransparencyCommandTest");
    createSignerConfig(keystorePath, privateKey, certificate);
  }

  private void createSignerConfig(Path keystorePath, PrivateKey privateKey, Certificate certificate)
      throws Exception {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, KEYSTORE_PASSWORD.toCharArray());
    keystore.setKeyEntry(
        KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), new Certificate[] {certificate});
    keystore.store(new FileOutputStream(keystorePath.toFile()), KEYSTORE_PASSWORD.toCharArray());
    signerConfig =
        SignerConfig.extractFromKeystore(
            keystorePath,
            KEY_ALIAS,
            Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
            Optional.of(Password.createForTest(KEY_PASSWORD)));
  }

  private static BundleModule addCodeFilesToBundleModule(
      BundleModuleBuilder module, boolean hasSharedUserId) {
    XmlNode manifest =
        hasSharedUserId
            ? androidManifest("com.test.app", withSharedUserId("sharedUserId"))
            : androidManifest("com.test.app");
    return module
        .setManifest(manifest)
        .addFile(DEX1, FILE_CONTENT_DEX1)
        .addFile(DEX2, FILE_CONTENT_DEX2)
        .addFile(NATIVE_LIB_PATH1, FILE_CONTENT_NATIVE_LIB1)
        .addFile(NATIVE_LIB_PATH2, FILE_CONTENT_NATIVE_LIB2)
        // 2 files below are not code related and should not be included in the transparency file.
        .addFile(RES_FILE)
        .addFile(NON_CODE_FILE_IN_LIB_DIRECTORY)
        .build();
  }

  private CodeTransparency expectedTransparencyProto() {
    CodeTransparency.Builder transparencyBuilder = CodeTransparency.newBuilder();
    addCodeFilesToTransparencyProto(transparencyBuilder, BASE_MODULE);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE1);
    addCodeFilesToTransparencyProto(transparencyBuilder, FEATURE_MODULE2);
    return transparencyBuilder.build();
  }

  private void addCodeFilesToTransparencyProto(
      CodeTransparency.Builder transparencyBuilder, String moduleName) {
    transparencyBuilder
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX1)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileHashDex1)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + DEX2)
                .setType(CodeRelatedFile.Type.DEX)
                .setApkPath("")
                .setSha256(fileHashDex2)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH1)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH1)
                .setSha256(fileHashNativeLib1)
                .build())
        .addCodeRelatedFile(
            CodeRelatedFile.newBuilder()
                .setPath(moduleName + "/" + NATIVE_LIB_PATH2)
                .setType(CodeRelatedFile.Type.NATIVE_LIBRARY)
                .setApkPath(NATIVE_LIB_PATH2)
                .setSha256(fileHashNativeLib2)
                .build());
  }

  private static CodeTransparency getTransparencyProto(String transparencyPayload)
      throws IOException {
    CodeTransparency.Builder transparencyProto = CodeTransparency.newBuilder();
    JsonFormat.parser().merge(transparencyPayload, transparencyProto);
    return transparencyProto.build();
  }
}

