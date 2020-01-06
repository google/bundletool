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
package com.android.tools.build.bundletool;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class BundleToolMainTest {

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Mock private Runtime mockRuntime;

  private final ArgumentCaptor<Integer> exitCode = ArgumentCaptor.forClass(Integer.class);

  @Test
  public void testNoErrorReturnsExitCodeZero() {
    BundleToolMain.main(new String[] {"help", "build-apks"}, mockRuntime);

    // Exit code 0 is returned by explicitly invoking Runtime#exit.
    verify(mockRuntime).exit(exitCode.capture());
    assertThat(exitCode.getValue()).isEqualTo(0);
  }

  @Test
  public void testErrorReturnsExitCodeNotZero_badFlag() {
    BundleToolMain.main(new String[] {"build-apks", "not-a-flag"}, mockRuntime);

    verify(mockRuntime).exit(exitCode.capture());
    assertThat(exitCode.getValue()).isNotEqualTo(0);
  }

  @Test
  public void testErrorReturnsExitCodeNotZero_noCommand() {
    BundleToolMain.main(new String[] {""}, mockRuntime);

    verify(mockRuntime).exit(exitCode.capture());
    assertThat(exitCode.getValue()).isNotEqualTo(0);
  }

  @Test
  public void testErrorReturnsExitCodeNotZero_helpForUnrecognizedCommand() {
    BundleToolMain.main(new String[] {"help", "not-a-command"}, mockRuntime);

    verify(mockRuntime, atLeastOnce()).exit(exitCode.capture());
    assertThat(exitCode.getAllValues().get(0)).isNotEqualTo(0);
  }

  @Test
  public void testErrorReturnsExitCodeNotZero_commandDoesNotExist() {
    BundleToolMain.main(new String[] {"not-a-command"}, mockRuntime);

    verify(mockRuntime).exit(exitCode.capture());
    assertThat(exitCode.getValue()).isNotEqualTo(0);
  }
}
