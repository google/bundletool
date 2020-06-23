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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithAbis;
import static com.android.tools.build.bundletool.testing.TargetingUtils.multiAbiTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultiAbiMatcherTest {

  @Test
  public void checkDeviceCompatibleInternal_compatibleDevice() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86", "armeabi-v7a"));

    // The set (x86) is valid.
    matcher.checkDeviceCompatible(multiAbiTargeting(X86));
    matcher.checkDeviceCompatible(multiAbiTargeting(setOf(setOf(ARM64_V8A), setOf(X86))));
    matcher.checkDeviceCompatible(multiAbiTargeting(ARM64_V8A, setOf(X86)));
    // The set (x86, armeabi-v7a) is valid.
    matcher.checkDeviceCompatible(multiAbiTargeting(setOf(setOf(X86, ARMEABI_V7A))));
    matcher.checkDeviceCompatible(
        multiAbiTargeting(setOf(setOf(ARM64_V8A), setOf(X86, ARMEABI_V7A))));
    matcher.checkDeviceCompatible(
        multiAbiTargeting(setOf(setOf(ARM64_V8A)), setOf(setOf(X86, ARMEABI_V7A))));
  }

  @Test
  public void checkDeviceCompatibleInternal_compatibleDeviceInWrongOrder() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("armeabi-v7a", "x86"));

    matcher.checkDeviceCompatible(multiAbiTargeting(setOf(setOf(X86, ARMEABI_V7A))));
    matcher.checkDeviceCompatible(
        multiAbiTargeting(setOf(setOf(ARM64_V8A), setOf(X86, ARMEABI_V7A))));
    matcher.checkDeviceCompatible(
        multiAbiTargeting(setOf(setOf(ARM64_V8A)), setOf(setOf(X86, ARMEABI_V7A))));
  }

  @Test
  public void checkDeviceCompatibleInternal_incompatibleDevice() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86", "armeabi-v7a"));

    // Only 64-bit architectures.
    assertThrows(
        IncompatibleDeviceException.class,
        () -> matcher.checkDeviceCompatible(multiAbiTargeting(X86_64)));
    assertThrows(
        IncompatibleDeviceException.class,
        () -> matcher.checkDeviceCompatible(multiAbiTargeting(setOf(setOf(X86_64, ARM64_V8A)))));
    assertThrows(
        IncompatibleDeviceException.class,
        () ->
            matcher.checkDeviceCompatible(
                multiAbiTargeting(setOf(setOf(ARM64_V8A), setOf(X86_64)))));
    assertThrows(
        IncompatibleDeviceException.class,
        () -> matcher.checkDeviceCompatible(multiAbiTargeting(ARM64_V8A, setOf(X86_64))));
    // No set is fully contained in (x86, armeabi-v7a).
    assertThrows(
        IncompatibleDeviceException.class,
        () ->
            matcher.checkDeviceCompatible(
                multiAbiTargeting(setOf(setOf(X86_64, X86), setOf(ARM64_V8A)))));
    assertThrows(
        IncompatibleDeviceException.class,
        () ->
            matcher.checkDeviceCompatible(
                multiAbiTargeting(setOf(setOf(X86_64, X86, ARM64_V8A, ARMEABI_V7A)))));
  }

  @Test
  public void matchesTargeting_noMultiAbiTargeting() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86"));

    assertThat(matcher.matchesTargeting(MultiAbiTargeting.getDefaultInstance())).isTrue();
  }

  @Test
  public void matchesTargeting_singleAbiDevice() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86"));

    assertThat(matcher.matchesTargeting(multiAbiTargeting(X86))).isTrue();
    assertThat(matcher.matchesTargeting(multiAbiTargeting(setOf(setOf(X86), setOf(ARMEABI_V7A)))))
        .isTrue();
    assertThat(matcher.matchesTargeting(multiAbiTargeting(X86, setOf(X86_64)))).isTrue();
    assertThat(matcher.matchesTargeting(multiAbiTargeting(ARMEABI_V7A, setOf(X86)))).isFalse();
    assertThat(matcher.matchesTargeting(multiAbiTargeting(setOf(setOf(X86, ARMEABI_V7A)))))
        .isFalse();
  }

  @Test
  public void matchesTargeting_multipleAbiDevice_noMatch() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"));

    assertThat(matcher.matchesTargeting(multiAbiTargeting(ARMEABI_V7A, setOf(X86)))).isFalse();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86, ARMEABI_V7A)), setOf(setOf(X86)))))
        .isFalse();
  }

  @Test
  public void matchesTargeting_multipleAbiDevice_worseAlternatives() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"));

    assertThat(matcher.matchesTargeting(multiAbiTargeting(X86))).isTrue();
    assertThat(matcher.matchesTargeting(multiAbiTargeting(X86, setOf(ARM64_V8A)))).isTrue();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86_64, X86)), setOf(setOf(ARM64_V8A, ARMEABI_V7A)))))
        .isTrue();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86_64)), setOf(setOf(X86, ARM64_V8A)))))
        .isTrue();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86_64, X86)), setOf(setOf(X86_64, ARM64_V8A)))))
        .isTrue();
  }

  @Test
  public void matchesTargeting_multipleAbiDevice_betterAlternatives() {
    MultiAbiMatcher matcher = new MultiAbiMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"));

    assertThat(matcher.matchesTargeting(multiAbiTargeting(X86, setOf(X86_64)))).isFalse();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(ARM64_V8A, ARMEABI_V7A)), setOf(setOf(X86_64, X86)))))
        .isFalse();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86)), setOf(setOf(X86_64, ARM64_V8A)))))
        .isFalse();
    assertThat(
            matcher.matchesTargeting(
                multiAbiTargeting(setOf(setOf(X86_64, ARM64_V8A)), setOf(setOf(X86_64, X86)))))
        .isFalse();
  }

  @SafeVarargs
  private static <E> ImmutableSet<E> setOf(E... elements) {
    return ImmutableSet.copyOf(elements);
  }
}
