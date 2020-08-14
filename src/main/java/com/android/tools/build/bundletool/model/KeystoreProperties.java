/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.auto.value.AutoValue;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Properties for a key in a Java KeyStore */
@SuppressWarnings("Immutable") // Password is considered immutable.
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class KeystoreProperties {

  static final String KEYSTORE_PATH_PROPERTY_NAME = "ks";
  static final String KEY_ALIAS_PROPERTY_NAME = "ks-key-alias";
  static final String KEYSTORE_PASSWORD_PROPERTY_NAME = "ks-pass";
  static final String KEY_PASSWORD_PROPERTY_NAME = "key-pass";

  KeystoreProperties() {}

  public abstract Path getKeystorePath();

  public abstract String getKeyAlias();

  public abstract Optional<Password> getKeystorePassword();

  public abstract Optional<Password> getKeyPassword();

  public static KeystoreProperties.Builder builder() {
    return new AutoValue_KeystoreProperties.Builder();
  }

  /** Builder of {@link KeystoreProperties} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the keystore. */
    public abstract Builder setKeystorePath(Path keystorePath);

    /** Sets the alias of the key to use in the keystore. */
    public abstract Builder setKeyAlias(String keyAlias);

    /** Sets the password of the keystore. */
    public abstract Builder setKeystorePassword(Password keystorePassword);

    /** Sets the password of the key in the keystore. */
    public abstract Builder setKeyPassword(Password keyPassword);

    public abstract KeystoreProperties build();
  }

  /**
   * Extract keystore properties from a .properties file.
   *
   * <p>The file is required to include values for the following keys:
   *
   * <ul>
   *   <li>ks - Path to the keystore.
   *   <li>ks-key-alias - Alias of the key to use in the keystore.
   * </ul>
   *
   * <p>The file may also optionally include values for the following keys:
   *
   * <ul>
   *   <li>ks-pass - Password of the keystore. If provided, must be prefixed with either 'pass:' (if
   *       the password is passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the password is
   *       the first line of a file, e.g. 'file:/tmp/myPassword.txt').
   *   <li>key-pass - Password of the key in the keystore. If provided, must be prefixed with either
   *       'pass:' (if the password is passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the
   *       password is the first line of a file, e.g. 'file:/tmp/myPassword.txt').
   * </ul>
   *
   * <p>Example keystore.properties file:
   *
   * <pre>
   * ks=/path/to/keystore.jks
   * ks-key-alias=keyAlias
   * ks-pass=pass:myPassword
   * key-pass=file:/path/to/myPassword.txt
   * </pre>
   *
   * @param path Path to the keystore properties file on disk.
   */
  public static KeystoreProperties readFromFile(Path path) {
    checkFileExistsAndReadable(path);

    Properties properties = new Properties();
    try (InputStream inputStream = MoreFiles.asByteSource(path).openBufferedStream()) {
      properties.load(inputStream);
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Error while loading keystore properties from file %s.", path)
          .withCause(e)
          .build();
    }

    String keystorePath = properties.getProperty(KEYSTORE_PATH_PROPERTY_NAME);
    String keyAlias = properties.getProperty(KEY_ALIAS_PROPERTY_NAME);
    String keystorePassword = properties.getProperty(KEYSTORE_PASSWORD_PROPERTY_NAME);
    String keyPassword = properties.getProperty(KEY_PASSWORD_PROPERTY_NAME);

    if (keystorePath == null) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "No value was given for property '%s' in %s.", KEYSTORE_PATH_PROPERTY_NAME, path)
          .build();
    }
    if (keyAlias == null) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "No value was given for property '%s' in %s.", KEY_ALIAS_PROPERTY_NAME, path)
          .build();
    }

    Builder keystoreProperties =
        KeystoreProperties.builder()
            .setKeystorePath(FileUtils.getPath(keystorePath))
            .setKeyAlias(keyAlias);

    if (keystorePassword != null) {
      keystoreProperties.setKeystorePassword(Password.createFromStringValue(keystorePassword));
    }
    if (keyPassword != null) {
      keystoreProperties.setKeyPassword(Password.createFromStringValue(keyPassword));
    }

    return keystoreProperties.build();
  }
}
