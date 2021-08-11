/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.CertificateFactory.buildSelfSignedCertificate;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SigningConfigurationTest {

  private static final String KEYSTORE_PASSWORD = "keystore-password";
  private static final String KEY_PASSWORD = "key-password";
  private static final String KEY_ALIAS = "key-alias";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private PrivateKey privateKey;
  private X509Certificate certificate;

  @Before
  public void setUp() throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    privateKey = keyPair.getPrivate();
    certificate = buildSelfSignedCertificate(keyPair, "CN=SigningConfigurationTest");
  }

  @Test
  public void extractFromKeystore_jks() throws Exception {
    Path keystorePath = createKeystoreOfType("JKS");

    SigningConfiguration signingConfiguration =
        SigningConfiguration.extractFromKeystore(
            keystorePath,
            KEY_ALIAS,
            Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
            Optional.of(Password.createForTest(KEY_PASSWORD)));

    assertThat(signingConfiguration.getSignerConfig().getPrivateKey()).isEqualTo(privateKey);
    assertThat(signingConfiguration.getSignerConfig().getCertificates())
        .containsExactly(certificate);
  }

  @Test
  public void extractFromKeystore_pkcs12() throws Exception {
    Path keystorePath = createKeystoreOfType("PKCS12");

    SigningConfiguration signingConfiguration =
        SigningConfiguration.extractFromKeystore(
            keystorePath,
            KEY_ALIAS,
            Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
            Optional.of(Password.createForTest(KEY_PASSWORD)));

    assertThat(signingConfiguration.getSignerConfig().getPrivateKey()).isEqualTo(privateKey);
    assertThat(signingConfiguration.getSignerConfig().getCertificates())
        .containsExactly(certificate);
  }

  @Test
  public void extractFromKeystore_wrongKeystorePassword() throws Exception {
    Path keystorePath = createKeystore();

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                SigningConfiguration.extractFromKeystore(
                    keystorePath,
                    KEY_ALIAS,
                    Optional.of(Password.createForTest("WrongPassword")),
                    Optional.of(Password.createForTest(KEY_PASSWORD))));
    assertThat(e).hasMessageThat().contains("Incorrect keystore password");
  }

  @Test
  public void extractFromKeystore_wrongKeyPassword() throws Exception {
    Path keystorePath = createKeystore();

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                SigningConfiguration.extractFromKeystore(
                    keystorePath,
                    KEY_ALIAS,
                    Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
                    Optional.of(Password.createForTest("WrongPassword"))));
    assertThat(e).hasMessageThat().contains("Incorrect key password");
  }

  @Test
  public void extractFromKeystore_keyPasswordNotProvidedImpliesSameAsKeystorePassword()
      throws Exception {
    Path keystorePath =
        createKeystoreWithPasswords(
            /* keystoreType= */ "JKS",
            /* keystorePassword= */ KEYSTORE_PASSWORD,
            /* keyPassword= */ KEYSTORE_PASSWORD);

    SigningConfiguration signingConfiguration =
        SigningConfiguration.extractFromKeystore(
            keystorePath,
            KEY_ALIAS,
            Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
            Optional.empty());

    assertThat(signingConfiguration.getSignerConfig().getPrivateKey()).isEqualTo(privateKey);
    assertThat(signingConfiguration.getSignerConfig().getCertificates())
        .containsExactly(certificate);
  }

  @Test
  public void extractFromKeystore_keyAliasDoesNotExist() throws Exception {
    Path keystorePath = createKeystore();

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                SigningConfiguration.extractFromKeystore(
                    keystorePath,
                    "BadKeyAlias",
                    Optional.of(Password.createForTest(KEYSTORE_PASSWORD)),
                    Optional.of(Password.createForTest(KEY_PASSWORD))));
    assertThat(e).hasMessageThat().contains("No key found with alias 'BadKeyAlias'");
  }

  @Test
  public void buildSigningConfiguration_missingSigningCertificateLineage() throws Exception {
    SignerConfig signerConfig = createSignerConfig("CN=SigningConfigurationTest");
    SignerConfig oldSignerConfig = createSignerConfig("CN=SigningConfigurationTest Old");

    Exception exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SigningConfiguration.builder()
                    .setSignerConfig(signerConfig)
                    .setOldestSigner(oldSignerConfig)
                    .build());

    assertThat(exception)
        .hasMessageThat()
        .contains("Oldest signer should not be provided without signing certificate lineage.");
  }

  private Path createKeystore() throws Exception {
    return createKeystoreOfType("JKS");
  }

  private Path createKeystoreOfType(String keystoreType) throws Exception {
    return createKeystoreWithPasswords(keystoreType, KEYSTORE_PASSWORD, KEY_PASSWORD);
  }

  private Path createKeystoreWithPasswords(
      String keystoreType, String keystorePassword, String keyPassword) throws Exception {
    KeyStore keystore = KeyStore.getInstance(keystoreType);
    keystore.load(/* stream= */ null, keystorePassword.toCharArray());
    keystore.setKeyEntry(
        KEY_ALIAS, privateKey, keyPassword.toCharArray(), new Certificate[] {certificate});
    Path keystorePath = tmp.getRoot().toPath().resolve("keystore.jks");
    keystore.store(new FileOutputStream(keystorePath.toFile()), keystorePassword.toCharArray());
    return keystorePath;
  }

  private static SignerConfig createSignerConfig(String distinguishedName) throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    X509Certificate oldCertificate = buildSelfSignedCertificate(keyPair, distinguishedName);
    return SignerConfig.builder()
        .setPrivateKey(privateKey)
        .setCertificates(ImmutableList.of(oldCertificate))
        .build();
  }
}
