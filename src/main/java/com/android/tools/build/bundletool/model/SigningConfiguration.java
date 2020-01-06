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

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import javax.security.auth.DestroyFailedException;

/** Information required to sign an APK. */
@SuppressWarnings("Immutable") // PrivateKey and X509Certificate are considered immutable.
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class SigningConfiguration {

  SigningConfiguration() {}

  public abstract PrivateKey getPrivateKey();

  public abstract ImmutableList<X509Certificate> getCertificates();

  public static Builder builder() {
    return new AutoValue_SigningConfiguration.Builder();
  }

  /** Builder of {@link SigningConfiguration} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the private key to use to sign the APK. */
    public abstract Builder setPrivateKey(PrivateKey privateKey);

    /** Sets the certificate corresponding to the private key. */
    public abstract Builder setCertificates(ImmutableList<X509Certificate> certificates);

    public abstract SigningConfiguration build();
  }

  /**
   * Extract the signing configuration (private key and certificates) associated with a key stored
   * in a keystore on disk.
   *
   * @param keystorePath Path to the keystore on disk (JKS and PKCS12 supported).
   * @param keyAlias Alias of the key in the keystore.
   * @param optionalKeystorePassword Password of the keystore. If not set, user will be prompted.
   * @param optionalKeyPassword Password of the key in the keystore. If not set, user will be
   *     prompted.
   */
  public static SigningConfiguration extractFromKeystore(
      Path keystorePath,
      String keyAlias,
      Optional<Password> optionalKeystorePassword,
      Optional<Password> optionalKeyPassword) {
    checkFileExistsAndReadable(keystorePath);

    KeyStore keystore;
    try {
      // With Java 8, this is able to read both JKS and PKCS12 keystores.
      keystore = KeyStore.getInstance("JKS");
    } catch (KeyStoreException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Unable to build a keystore instance: " + e.getMessage())
          .build();
    }

    PasswordProtection keystorePassword = null;
    PasswordProtection keyPassword = null;
    try (InputStream keystoreInputStream = new FileInputStream(keystorePath.toFile())) {
      // 1. Prompt for the keystore password if it wasn't provided.
      keystorePassword =
          optionalKeystorePassword
              .map(Password::getValue)
              .orElseGet(
                  () ->
                      new PasswordProtection(
                          System.console().readPassword("Enter keystore password: ")));

      // 2. Load the KeyStore (required to perform any operation).
      try {
        keystore.load(keystoreInputStream, keystorePassword.getPassword());
      } catch (IOException e) {
        if (e.getCause() instanceof UnrecoverableKeyException) {
          throw new CommandExecutionException("Incorrect keystore password.");
        }
        throw e;
      }

      // 3. If the key password was provided, use it.
      if (optionalKeyPassword.isPresent()) {
        try {
          return readSigningConfigFromLoadedKeyStore(
              keystore, keyAlias, optionalKeyPassword.get().getValue().getPassword());
        } catch (UnrecoverableKeyException e) {
          throw new CommandExecutionException("Incorrect key password.", e);
        }
      }

      // 4. Otherwise, we first try with the keystore password. If it doesn't work, we prompt the
      //    user for the password and try again.
      try {
        return readSigningConfigFromLoadedKeyStore(
            keystore, keyAlias, keystorePassword.getPassword());
      } catch (UnrecoverableKeyException expected) {
        try {
          keyPassword =
              new PasswordProtection(
                  System.console().readPassword("Enter password for key '%s': ", keyAlias));
          return readSigningConfigFromLoadedKeyStore(keystore, keyAlias, keyPassword.getPassword());
        } catch (UnrecoverableKeyException e) {
          throw new CommandExecutionException("Incorrect key password.", e);
        }
      }
    } catch (IOException | NoSuchAlgorithmException | KeyStoreException | CertificateException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Error while loading private key and certificates from the keystore.")
          .build();
    } finally {
      // Destroy the passwords.
      try {
        if (keyPassword != null) {
          keyPassword.destroy();
        }
        if (keystorePassword != null) {
          keystorePassword.destroy();
        }
      } catch (DestroyFailedException e) {
        // Ignore. Never thrown by PasswordProtection#destroy().
      }
    }
  }

  private static SigningConfiguration readSigningConfigFromLoadedKeyStore(
      KeyStore keystore, String keyAlias, char[] keyPassword)
      throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
    PrivateKey privateKey = (PrivateKey) keystore.getKey(keyAlias, keyPassword);
    Certificate[] certChain = keystore.getCertificateChain(keyAlias);
    if (certChain == null) {
      throw CommandExecutionException.builder()
          .withMessage("No key found with alias '%s' in keystore.", keyAlias)
          .build();
    }

    ImmutableList<X509Certificate> certificates =
        Arrays.stream(certChain).map(c -> (X509Certificate) c).collect(toImmutableList());

    return SigningConfiguration.builder()
        .setPrivateKey(privateKey)
        .setCertificates(certificates)
        .build();
  }
}
