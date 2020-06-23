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

package com.android.tools.build.bundletool.device;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.exceptions.AdbOutputParseException;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SessionIdParserTest {

  static final ImmutableList<String> SESSION_CREATED_SUCCESSFULLY_OUTPUT =
      ImmutableList.of("", "Success: created install session [1715550569]", "");

  static final ImmutableList<String> SESSION_NOT_CREATED_OUTPUT =
      ImmutableList.of(
          "Exception occurred while executing: Unknown option --multi-package",
          "java.lang.IllegalArgumentException: Unknown option --multi-package",
          "\tat com.android.server.pm.PackageManagerShellCommand.makeInstallParams(PackageManagerShellCommand.java:2757)",
          "\tat com.android.server.pm.PackageManagerShellCommand.runInstallCreate(PackageManagerShellCommand.java:1292)",
          "\tat com.android.server.pm.PackageManagerShellCommand.onCommand(PackageManagerShellCommand.java:189)");

  static final ImmutableList<String> NO_SESSION_ID_OUTPUT =
      ImmutableList.of("Success: created install session []");

  private final SessionIdParser sessionIdParser = new SessionIdParser();

  @Test
  public void successfulOutput() {
    assertThat(sessionIdParser.parse(SESSION_CREATED_SUCCESSFULLY_OUTPUT)).isEqualTo(1715550569);
  }

  @Test
  public void sessionNotCreatedOutput() {
    AdbOutputParseException exception =
        assertThrows(
            AdbOutputParseException.class, () -> sessionIdParser.parse(SESSION_NOT_CREATED_OUTPUT));
    assertThat(exception).hasMessageThat().contains("failed to parse session id from output");
  }

  @Test
  public void noSessionIdOutput() {
    AdbOutputParseException exception =
        assertThrows(
            AdbOutputParseException.class, () -> sessionIdParser.parse(NO_SESSION_ID_OUTPUT));
    assertThat(exception).hasMessageThat().contains("session id should be integer");
  }
}
