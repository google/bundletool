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

import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdkAndCodename;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.SdkVersion;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkVersionMatcherTest {

  @Test
  public void simpleMatching() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdk(21));

    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(SdkVersion.getDefaultInstance(), ImmutableSet.of())))
        .isTrue();
    assertThat(
            sdkMatcher.matchesTargeting(sdkVersionTargeting(sdkVersionFrom(21), ImmutableSet.of())))
        .isTrue();
  }

  @Test
  public void incompatibleDevice() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdk(21));

    Throwable exception;
    exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                sdkMatcher.checkDeviceCompatible(
                    sdkVersionTargeting(sdkVersionFrom(22), ImmutableSet.of())));
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (21) of the device is not supported.");

    exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                sdkMatcher.checkDeviceCompatible(
                    sdkVersionTargeting(
                        sdkVersionFrom(25),
                        ImmutableSet.of(sdkVersionFrom(22), sdkVersionFrom(27)))));
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (21) of the device is not supported.");
  }

  @Test
  public void worseAlternatives() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdk(23));

    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance()))))
        .isTrue();
    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21)))))
        .isTrue();
    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    sdkVersionFrom(23),
                    ImmutableSet.of(sdkVersionFrom(21), SdkVersion.getDefaultInstance()))))
        .isTrue();
  }

  @Test
  public void betterAlternative() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdk(25));

    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    SdkVersion.getDefaultInstance(), ImmutableSet.of(sdkVersionFrom(25)))))
        .isFalse();
    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    SdkVersion.getDefaultInstance(), ImmutableSet.of(sdkVersionFrom(21)))))
        .isFalse();
    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    sdkVersionFrom(21),
                    ImmutableSet.of(sdkVersionFrom(23), SdkVersion.getDefaultInstance()))))
        .isFalse();
    assertThat(
            sdkMatcher.matchesTargeting(
                sdkVersionTargeting(
                    sdkVersionFrom(27),
                    ImmutableSet.of(sdkVersionFrom(23), SdkVersion.getDefaultInstance()))))
        .isFalse();
  }

  @Test
  public void preReleaseAppReleaseDevice_fails() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdk(29));

    Throwable exception;
    exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                sdkMatcher.checkDeviceCompatible(
                    sdkVersionTargeting(sdkVersionFrom(10_000), ImmutableSet.of())));
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (29) of the device is not supported.");
  }

  @Test
  public void preReleaseAppPreReleaseDevice_succeeds() {
    SdkVersionMatcher sdkMatcher = new SdkVersionMatcher(deviceWithSdkAndCodename(29, "R"));

    assertThat(
        sdkMatcher.matchesTargeting(sdkVersionTargeting(sdkVersionFrom(10_000), ImmutableSet.of())))
        .isTrue();
  }
}
