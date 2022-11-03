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
package com.android.tools.build.bundletool.validation;

import static com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile.Type.DEX;
import static com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile.Type.NATIVE_LIBRARY;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSharedUserId;
import static com.google.common.truth.Truth.assertThat;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.CertificateFactory;
import com.android.tools.build.bundletool.testing.CodeRelatedFileBuilderHelper;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.zip.ZipFile;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CodeTransparencyValidatorTest {

  private static final String DEX_PATH = "dex/classes.dex";
  private static final String NATIVE_LIB_PATH = "lib/arm64-v8a/libnative.so";
  private static final byte[] DEX_FILE_CONTENT = new byte[] {1, 2, 3};
  private static final byte[] NATIVE_LIB_FILE_CONTENT = new byte[] {2, 3, 4};

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path bundlePath;
  PrivateKey privateKey;
  KeyPairGenerator kpg;
  X509Certificate certificate;
  CodeTransparency validCodeTransparency;

  @Before
  public void setUp() throws Exception {
    bundlePath = tmp.getRoot().toPath().resolve("bundle.aab");
    kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(/* keysize= */ 3072);
    KeyPair keyPair = kpg.genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=CodeTransparencyValidatorTest");

    validCodeTransparency =
        CodeTransparency.newBuilder()
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(DEX)
                    .setPath("base/" + DEX_PATH)
                    .setSha256(ByteSource.wrap(DEX_FILE_CONTENT).hash(Hashing.sha256()).toString()))
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(NATIVE_LIBRARY)
                    .setPath("base/" + NATIVE_LIB_PATH)
                    .setSha256(
                        ByteSource.wrap(NATIVE_LIB_FILE_CONTENT).hash(Hashing.sha256()).toString())
                    .setApkPath(NATIVE_LIB_PATH))
            .addCodeRelatedFile(
                CodeRelatedFileBuilderHelper.archivedDexCodeRelatedFile(
                    BundleToolVersion.getCurrentVersion()))
            .build();
  }

  @Test
  public void transparencyFileNotPresent() {
    new CodeTransparencyValidator()
        .validateBundle(
            new AppBundleBuilder()
                .addModule(
                    "base", module -> module.setManifest(androidManifest("com.test.app")).build())
                .build());
  }

  @Test
  public void transparencyVerified() throws Exception {
    createBundle(bundlePath, validCodeTransparency);
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    new CodeTransparencyValidator().validateBundle(bundle);
  }

  @Test
  public void transparencyVerificationFailed_invalidSignature() throws Exception {
    // Update certificate value so that it does not match the private key that will used to sign the
    // transparency metadata.
    certificate =
        CertificateFactory.buildSelfSignedCertificate(
            kpg.generateKeyPair(), "CN=CodeTransparencyValidatorTest_WrongCertificate");
    createBundle(bundlePath, validCodeTransparency);
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    Exception e =
        assertThrows(
            InvalidBundleException.class,
            () -> new CodeTransparencyValidator().validateBundle(bundle));
    assertThat(e)
        .hasMessageThat()
        .contains("Verification failed because code transparency signature is invalid.");
  }

  @Test
  public void transparencyVerificationFailed_codeModified() throws Exception {
    createBundle(
        bundlePath,
        CodeTransparency.newBuilder()
            .addCodeRelatedFile(
                CodeRelatedFileBuilderHelper.archivedDexCodeRelatedFile(
                    BundleToolVersion.getCurrentVersion()))
            .build());
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    Exception e =
        assertThrows(
            InvalidBundleException.class,
            () -> new CodeTransparencyValidator().validateBundle(bundle));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Verification failed because code was modified after transparency metadata"
                + " generation.");
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Files added after transparency metadata generation: [base/dex/classes.dex,"
                + " base/lib/arm64-v8a/libnative.so]");
  }

  @Test
  public void transparencyVerified_bundleHasSharedUserId() throws Exception {
    createBundle(bundlePath, validCodeTransparency, withSharedUserId("sharedUserId"));
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    new CodeTransparencyValidator().validateBundle(bundle);
  }

  private void createBundle(
      Path path, CodeTransparency codeTransparency, ManifestMutator... manifestMutators)
      throws Exception {
    String transparencyPayload = JsonFormat.printer().print(codeTransparency);
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(androidManifest("com.test.app", manifestMutators))
                        .addFile(DEX_PATH, DEX_FILE_CONTENT)
                        .addFile(NATIVE_LIB_PATH, NATIVE_LIB_FILE_CONTENT))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                CharSource.wrap(createJwsToken(transparencyPayload))
                    .asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), path);
  }

  private String createJwsToken(String payload) throws JoseException {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
    jws.setCertificateChainHeaderValue(certificate);
    jws.setPayload(payload);
    jws.setKey(privateKey);
    return jws.getCompactSerialization();
  }
}
