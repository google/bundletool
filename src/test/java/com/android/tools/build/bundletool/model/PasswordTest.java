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

package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PasswordTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void passwordFlag_noPrefix_throws() {
    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> Password.createFromStringValue("hello"));
    assertThat(exception)
        .hasMessageThat()
        .contains("Passwords must be prefixed with \"pass:\" or \"file:\"");
  }

  @Test
  public void password_inClear() {
    String password =
        new String(Password.createFromStringValue("pass:hello").getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void password_inFile() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, "hello".getBytes(UTF_8));

    String password =
        new String(Password.createFromStringValue("file:" + passwordFile).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void password_inFile_withLineFeedAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, "hello\n".getBytes(UTF_8));

    String password =
        new String(Password.createFromStringValue("file:" + passwordFile).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void password_inFile_withCarriageReturnAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, "hello\r".getBytes(UTF_8));

    String password =
        new String(Password.createFromStringValue("file:" + passwordFile).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void password_inFile_withCarriageReturnAndLineFeedAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, "hello\r\n".getBytes(UTF_8));

    String password =
        new String(Password.createFromStringValue("file:" + passwordFile).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }
}
