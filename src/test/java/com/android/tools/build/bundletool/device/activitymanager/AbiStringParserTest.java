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

package com.android.tools.build.bundletool.device.activitymanager;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AbiStringParserTest {

  @Test
  public void differentAbiLineFormat() {
    Throwable exception =
        assertThrows(
            IllegalArgumentException.class, () -> AbiStringParser.parseAbiLine("arm64-v8a"));
    assertThat(exception).hasMessageThat().contains("Expected ABI output to start with 'abi:'.");
  }

  @Test
  public void recognizedSingleAbi() {
    ImmutableList<String> abis = AbiStringParser.parseAbiLine("abi: arm64-v8a");
    assertThat(abis).containsExactly("arm64-v8a");
  }

  @Test
  public void unrecognizedSingleAbi() {
    ImmutableList<String> abis = AbiStringParser.parseAbiLine("abi: arm64-v9a");
    assertThat(abis).isEmpty();
  }

  @Test
  public void recognizedMultipleAbis() {
    ImmutableList<String> abis = AbiStringParser.parseAbiLine("abi: x86_64,arm64-v8a");
    assertThat(abis).containsExactly("x86_64", "arm64-v8a").inOrder();
  }

  @Test
  public void unrecognizedMultipleAbis() {
    ImmutableList<String> abis = AbiStringParser.parseAbiLine("abi: x86_64,arm64-v10a,arm64-v8a");
    assertThat(abis).containsExactly("x86_64", "arm64-v8a").inOrder();
  }

  @Test
  public void pixelAbiLine() {
    ImmutableList<String> abis = AbiStringParser.parseAbiLine("abi: arm64-v8a,armeabi-v7a,armeabi");
    assertThat(abis).containsExactly("arm64-v8a", "armeabi-v7a", "armeabi").inOrder();
  }
}
