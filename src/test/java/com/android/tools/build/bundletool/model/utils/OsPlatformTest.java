/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.build.bundletool.model.utils;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OsPlatformTest {

  @Test
  public void getPlatform_linuxArm64Detected() {
    assertThat(OsPlatform.getPlatform("Linux", "aarch64")).isEqualTo(OsPlatform.LINUX_ARM64);
    assertThat(OsPlatform.getPlatform("Linux", "arm64")).isEqualTo(OsPlatform.LINUX_ARM64);
  }

  @Test
  public void getPlatform_linuxX86Detected() {
    assertThat(OsPlatform.getPlatform("Linux", "amd64")).isEqualTo(OsPlatform.LINUX);
    assertThat(OsPlatform.getPlatform("Linux", "x86_64")).isEqualTo(OsPlatform.LINUX);
  }

  @Test
  public void getPlatform_macOsDetected() {
    assertThat(OsPlatform.getPlatform("Mac OS X", "x86_64")).isEqualTo(OsPlatform.MACOS);
    assertThat(OsPlatform.getPlatform("Mac OS X", "aarch64")).isEqualTo(OsPlatform.MACOS);
  }

  @Test
  public void getPlatform_windowsDetected() {
    assertThat(OsPlatform.getPlatform("Windows 10", "amd64")).isEqualTo(OsPlatform.WINDOWS);
    assertThat(OsPlatform.getPlatform("Windows 11", "aarch64")).isEqualTo(OsPlatform.WINDOWS);
  }

  @Test
  public void getPlatform_otherDetected() {
    assertThat(OsPlatform.getPlatform("FreeBSD", "x86_64")).isEqualTo(OsPlatform.OTHER);
  }
}
