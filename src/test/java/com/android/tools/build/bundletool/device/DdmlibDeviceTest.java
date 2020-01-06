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
package com.android.tools.build.bundletool.device;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.VersionCodes;
import com.android.tools.build.bundletool.device.DdmlibDevice.RemoteCommandExecutor;
import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class DdmlibDeviceTest {

  private static final Path APK_PATH = Paths.get("/tmp/app.apk");

  @Mock private IDevice mockDevice;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void doesNotAllowDowngrade() throws Exception {
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(VersionCodes.KITKAT));
    DdmlibDevice ddmlibDevice = new DdmlibDevice(mockDevice);

    ddmlibDevice.installApks(
        ImmutableList.of(APK_PATH), InstallOptions.builder().setAllowDowngrade(false).build());

    // "-d" should *not* be passed as extra arg.
    verify(mockDevice).installPackage(eq(APK_PATH.toString()), anyBoolean() /*, no extra args */);
  }

  @Test
  public void allowDowngrade_preL() throws Exception {
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(VersionCodes.KITKAT));
    DdmlibDevice ddmlibDevice = new DdmlibDevice(mockDevice);

    ddmlibDevice.installApks(
        ImmutableList.of(APK_PATH), InstallOptions.builder().setAllowDowngrade(true).build());

    verify(mockDevice).installPackage(eq(APK_PATH.toString()), anyBoolean(), eq("-d"));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void allowDowngrade_postL() throws Exception {
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(VersionCodes.LOLLIPOP));
    DdmlibDevice ddmlibDevice = new DdmlibDevice(mockDevice);

    ddmlibDevice.installApks(
        ImmutableList.of(APK_PATH), InstallOptions.builder().setAllowDowngrade(true).build());

    ArgumentCaptor<List<String>> extraArgsCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(mockDevice)
        .installPackages(
            eq(ImmutableList.of(APK_PATH.toFile())),
            anyBoolean(),
            extraArgsCaptor.capture(),
            anyLong(),
            any(TimeUnit.class));

    assertThat(extraArgsCaptor.getValue()).contains("-d");
  }

  @Test
  public void allowTestOnly() throws Exception {
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(VersionCodes.KITKAT));
    DdmlibDevice ddmlibDevice = new DdmlibDevice(mockDevice);

    ddmlibDevice.installApks(
        ImmutableList.of(APK_PATH), InstallOptions.builder().setAllowTestOnly(true).build());

    verify(mockDevice).installPackage(eq(APK_PATH.toString()), anyBoolean(), eq("-t"));
  }

  @Test
  public void joinUnixPathsTest() {
    assertThat(DdmlibDevice.joinUnixPaths("/", "splits", "mysplits")).isEqualTo("/splits/mysplits");
    assertThat(DdmlibDevice.joinUnixPaths("splits", "mysplits")).isEqualTo("splits/mysplits");
    assertThat(DdmlibDevice.joinUnixPaths("/", "splits/", "mysplits"))
        .isEqualTo("/splits/mysplits");
    assertThat(DdmlibDevice.joinUnixPaths("/", "splits", "mysplits/"))
        .isEqualTo("/splits/mysplits/");
  }

  @Test
  public void escapeAndSingleQuoteTest() {
    assertThat(RemoteCommandExecutor.escapeAndSingleQuote("abc")).isEqualTo("'abc'");
    assertThat(RemoteCommandExecutor.escapeAndSingleQuote("ab'c")).isEqualTo("'ab'\\''c'");
  }

  @Test
  public void formatCommandWithArgsTest() {
    assertThat(RemoteCommandExecutor.formatCommandWithArgs("cat %s %s", "abc", "def"))
        .isEqualTo("cat 'abc' 'def'");
    assertThat(RemoteCommandExecutor.formatCommandWithArgs("cat %s %s", "ab'c", "de'f"))
        .isEqualTo("cat 'ab'\\''c' 'de'\\''f'");
  }
}
