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

import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithAbis;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AbiMatcherTest {

  @Test
  public void simpleMatch() {
    AbiMatcher abiMatcher = new AbiMatcher(lDeviceWithAbis("x86"));

    assertThat(abiMatcher.matchesTargeting(abiTargeting(AbiAlias.X86, ImmutableSet.of()))).isTrue();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.ARMEABI, ImmutableSet.of(AbiAlias.X86))))
        .isFalse();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64))))
        .isTrue();
  }

  @Test
  public void incompatibleDevice() {
    AbiMatcher abiMatcher = new AbiMatcher(lDeviceWithAbis("x86", "armeabi"));

    assertThrows(
        IncompatibleDeviceException.class,
        () ->
            abiMatcher.checkDeviceCompatible(
                abiTargeting(AbiAlias.ARM64_V8A, ImmutableSet.of(AbiAlias.X86_64))));
    assertThrows(
        IncompatibleDeviceException.class,
        () -> abiMatcher.checkDeviceCompatible(abiTargeting(AbiAlias.X86_64, ImmutableSet.of())));
  }

  /** Tests handling of fallback targeting, often used in assets (no values, some alternatives). */
  @Test
  public void matchesFallbackStyleTargeting() {
    AbiMatcher abiMatcher = new AbiMatcher(lDeviceWithAbis("x86", "armeabi"));

    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(ImmutableSet.of(), ImmutableSet.of(AbiAlias.X86_64))))
        .isTrue();
  }

  @Test
  public void worseAlternatives() {
    AbiMatcher abiMatcher = new AbiMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"));

    assertThat(
            abiMatcher.matchesTargeting(abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.MIPS))))
        .isTrue();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.ARM64_V8A))))
        .isTrue();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.ARM64_V8A, AbiAlias.ARMEABI))))
        .isTrue();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86_64, ImmutableSet.of(AbiAlias.ARM64_V8A, AbiAlias.X86))))
        .isTrue();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.ARM64_V8A, ImmutableSet.of(AbiAlias.ARMEABI))))
        .isTrue();
  }

  @Test
  public void betterAlternatives() {
    AbiMatcher abiMatcher = new AbiMatcher(lDeviceWithAbis("x86_64", "x86", "arm64-v8a"));

    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.ARM64_V8A, ImmutableSet.of(AbiAlias.X86_64))))
        .isFalse();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.ARM64_V8A, ImmutableSet.of(AbiAlias.X86, AbiAlias.X86_64))))
        .isFalse();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64))))
        .isFalse();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.ARMEABI, ImmutableSet.of(AbiAlias.X86))))
        .isFalse();
    assertThat(
            abiMatcher.matchesTargeting(
                abiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64, AbiAlias.ARM64_V8A))))
        .isFalse();
  }
}
