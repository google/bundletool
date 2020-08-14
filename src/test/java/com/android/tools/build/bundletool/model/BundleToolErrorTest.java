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

import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.BundleToolError.ErrorType;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleToolErrorTest {

  @Test
  public void testInvalidBundle() {
    InvalidBundleException exception =
        InvalidBundleException.builder()
            .withCause(new RuntimeException("runtime"))
            .withUserMessage("user %s %s", "a", "b")
            .build();

    assertError(exception.toProto(), ErrorType.INVALID_BUNDLE_ERROR, "user a b", "user a b");
  }

  @Test
  public void testIncompatibleDevice() {
    IncompatibleDeviceException exception =
        IncompatibleDeviceException.builder().withUserMessage("user msg").build();

    assertError(exception.toProto(), ErrorType.INCOMPATIBLE_DEVICE_ERROR, "user msg", "user msg");
  }

  @Test
  public void testInvalidDeviceSpec() {
    InvalidDeviceSpecException exception =
        InvalidDeviceSpecException.builder().withUserMessage("user").build();

    assertError(exception.toProto(), ErrorType.INVALID_DEVICE_SPEC_ERROR, "user", "user");
  }

  @Test
  public void testInvalidDeviceSpec_causeNoMessage() {
    InvalidDeviceSpecException exception =
        InvalidDeviceSpecException.builder()
            .withCause(new IllegalArgumentException("internal"))
            .build();

    assertError(
        exception.toProto(),
        ErrorType.INVALID_DEVICE_SPEC_ERROR,
        "",
        "java.lang.IllegalArgumentException: internal");
  }

  @Test
  public void testCommandExecution() {
    CommandExecutionException exception =
        CommandExecutionException.builder().withInternalMessage("%s id:%d", "internal", 12).build();

    assertError(exception.toProto(), ErrorType.COMMAND_EXECUTION_ERROR, "", "internal id:12");
  }

  @Test
  public void testCommandExecution_causeNoMessage() {
    CommandExecutionException exception =
        CommandExecutionException.builder()
            .withCause(new IllegalArgumentException("internal"))
            .build();

    assertError(
        exception.toProto(),
        ErrorType.COMMAND_EXECUTION_ERROR,
        "",
        "java.lang.IllegalArgumentException: internal");
  }

  @Test
  public void testInvalidCommand() {
    InvalidCommandException exception =
        InvalidCommandException.builder()
            .withInternalMessage("internal msg")
            .withCause(new IllegalArgumentException("illegal arg"))
            .build();

    assertError(exception.toProto(), ErrorType.INVALID_COMMAND_ERROR, "", "internal msg");
  }

  private static void assertError(
      BundleToolError error, ErrorType errorType, String userMessage, String internalMessage) {
    assertThat(error.getErrorType()).isEqualTo(errorType);
    assertThat(error.getUserMessage()).isEqualTo(userMessage);
    assertThat(error.getExceptionMessage()).isEqualTo(internalMessage);
  }
}
