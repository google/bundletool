/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MAJOR_MAX_VALUE;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MINOR_MAX_VALUE;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RuntimeEnabledSdkVersionEncoderTest {

  @Test
  public void encodeSdkMajorAndMinorVersion_majorVersionNegative() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> encodeSdkMajorAndMinorVersion(/* versionMajor= */ -1, /* versionMinor= */ 0));
    assertThat(e)
        .hasMessageThat()
        .contains("SDK major version must be an integer between 0 and " + VERSION_MAJOR_MAX_VALUE);
  }

  @Test
  public void encodeSdkMajorAndMinorVersion_majorVersionTooBig() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                encodeSdkMajorAndMinorVersion(
                    /* versionMajor= */ RuntimeEnabledSdkVersionEncoder.VERSION_MAJOR_MAX_VALUE + 1,
                    /* versionMinor= */ 0));
    assertThat(e)
        .hasMessageThat()
        .contains("SDK major version must be an integer between 0 and " + VERSION_MAJOR_MAX_VALUE);
  }

  @Test
  public void encodeSdkMajorAndMinorVersion_minorVersionNegative() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> encodeSdkMajorAndMinorVersion(/* versionMajor= */ 0, /* versionMinor= */ -1));
    assertThat(e)
        .hasMessageThat()
        .contains("SDK minor version must be an integer between 0 and " + VERSION_MINOR_MAX_VALUE);
  }

  @Test
  public void encodeSdkMajorAndMinorVersion_minorVersionTooBig() {
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                encodeSdkMajorAndMinorVersion(
                    /* versionMajor= */ 0,
                    /* versionMinor= */ RuntimeEnabledSdkVersionEncoder.VERSION_MINOR_MAX_VALUE
                        + 1));
    assertThat(e)
        .hasMessageThat()
        .contains("SDK minor version must be an integer between 0 and " + VERSION_MINOR_MAX_VALUE);
  }

  @Test
  public void encodeSdkMajorAndMinorVersion_success() {
    assertThat(encodeSdkMajorAndMinorVersion(/* versionMajor= */ 1, /* versionMinor= */ 2))
        .isEqualTo(10002);
  }

  @Test
  public void encodeSdkMajorAndMinorVersion_maxValues_success() {
    assertThat(encodeSdkMajorAndMinorVersion(VERSION_MAJOR_MAX_VALUE, VERSION_MINOR_MAX_VALUE))
        .isEqualTo(1999999999);
  }
}
