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

import static com.android.tools.build.bundletool.model.KeystoreProperties.KEYSTORE_PASSWORD_PROPERTY_NAME;
import static com.android.tools.build.bundletool.model.KeystoreProperties.KEYSTORE_PATH_PROPERTY_NAME;
import static com.android.tools.build.bundletool.model.KeystoreProperties.KEY_ALIAS_PROPERTY_NAME;
import static com.android.tools.build.bundletool.model.KeystoreProperties.KEY_PASSWORD_PROPERTY_NAME;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeystorePropertiesTest {

  private static final String KEYSTORE_PATH = "/path/to/keystore.jks";
  private static final String KEY_ALIAS = "key-alias";
  private static final String KEYSTORE_PASSWORD = "ks-password";
  private static final String KEY_PASSWORD = "key-password";

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void readFromFile() throws Exception {
    Path keystorePropertiesPath = createPropertiesFile(KEYSTORE_PATH, KEY_ALIAS, null, null);

    KeystoreProperties keystoreProperties = KeystoreProperties.readFromFile(keystorePropertiesPath);

    assertThat(keystoreProperties.getKeystorePath()).isEqualTo(Paths.get(KEYSTORE_PATH));
    assertThat(keystoreProperties.getKeyAlias()).isEqualTo(KEY_ALIAS);
    assertThat(keystoreProperties.getKeystorePassword()).isEmpty();
    assertThat(keystoreProperties.getKeyPassword()).isEmpty();
  }

  @Test
  public void readFromFileWithPassword() throws Exception {
    Path keystorePropertiesPath =
        createPropertiesFile(
            KEYSTORE_PATH, KEY_ALIAS, "pass:" + KEYSTORE_PASSWORD, "pass:" + KEY_PASSWORD);

    KeystoreProperties keystoreProperties = KeystoreProperties.readFromFile(keystorePropertiesPath);

    assertThat(keystoreProperties.getKeystorePath()).isEqualTo(Paths.get(KEYSTORE_PATH));
    assertThat(keystoreProperties.getKeyAlias()).isEqualTo(KEY_ALIAS);
    assertThat(keystoreProperties.getKeystorePassword().get().getValue().getPassword())
        .isEqualTo(KEYSTORE_PASSWORD.toCharArray());
    assertThat(keystoreProperties.getKeyPassword().get().getValue().getPassword())
        .isEqualTo(KEY_PASSWORD.toCharArray());
  }

  @Test
  public void readFromFileWithPasswordFromFile() throws Exception {
    Path keystorePropertiesPath =
        createPropertiesFile(
            KEYSTORE_PATH,
            KEY_ALIAS,
            "file:" + createPasswordFile(KEYSTORE_PASSWORD),
            "file:" + createPasswordFile(KEY_PASSWORD));

    KeystoreProperties keystoreProperties = KeystoreProperties.readFromFile(keystorePropertiesPath);

    assertThat(keystoreProperties.getKeystorePath()).isEqualTo(Paths.get(KEYSTORE_PATH));
    assertThat(keystoreProperties.getKeyAlias()).isEqualTo(KEY_ALIAS);
    assertThat(keystoreProperties.getKeystorePassword().get().getValue().getPassword())
        .isEqualTo(KEYSTORE_PASSWORD.toCharArray());
    assertThat(keystoreProperties.getKeyPassword().get().getValue().getPassword())
        .isEqualTo(KEY_PASSWORD.toCharArray());
  }

  @Test
  public void readFromFile_missingKeystorePath() throws Exception {
    Path keystorePropertiesPath = createPropertiesFile(null, KEY_ALIAS, null, null);

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () -> KeystoreProperties.readFromFile(keystorePropertiesPath));
    assertThat(e)
        .hasMessageThat()
        .contains(
            String.format("No value was given for property 'ks' in %s.", keystorePropertiesPath));
  }

  @Test
  public void readFromFile_missingKeyAlias() throws Exception {
    Path keystorePropertiesPath = createPropertiesFile(KEYSTORE_PATH, null, null, null);

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () -> KeystoreProperties.readFromFile(keystorePropertiesPath));
    assertThat(e)
        .hasMessageThat()
        .contains(
            String.format(
                "No value was given for property 'ks-key-alias' in %s.", keystorePropertiesPath));
  }

  private Path createPropertiesFile(
      @Nullable String keystorePath,
      @Nullable String keyAlias,
      @Nullable String keystorePassword,
      @Nullable String keyPassword)
      throws IOException {
    Properties properties = new Properties();

    if (keystorePath != null) {
      properties.setProperty(KEYSTORE_PATH_PROPERTY_NAME, keystorePath);
    }
    if (keyAlias != null) {
      properties.setProperty(KEY_ALIAS_PROPERTY_NAME, keyAlias);
    }
    if (keystorePassword != null) {
      properties.setProperty(KEYSTORE_PASSWORD_PROPERTY_NAME, keystorePassword);
    }
    if (keyPassword != null) {
      properties.setProperty(KEY_PASSWORD_PROPERTY_NAME, keyPassword);
    }

    File keystorePropertiesFile = tmp.newFile();
    try (OutputStream outputStream = new FileOutputStream(keystorePropertiesFile)) {
      properties.store(outputStream, null);
    }

    return keystorePropertiesFile.toPath();
  }

  private Path createPasswordFile(String password) throws IOException {
    Path passwordFile = tmp.newFile().toPath();
    Files.write(passwordFile, password.getBytes(UTF_8));
    return passwordFile;
  }
}
