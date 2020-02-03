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
package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InMemoryModuleEntryTest {
  private static final String PATH = "res/test";
  private static final byte[] CONTENT = {1, 34, 123};

  @Test
  public void ofFile() {
    InMemoryModuleEntry moduleEntry = InMemoryModuleEntry.ofFile(PATH, CONTENT);
    assertThat(moduleEntry.getPath().toString()).isEqualTo(PATH);
    assertThat(moduleEntry.getContentAsBytes().toByteArray()).isEqualTo(CONTENT);
    assertThat(moduleEntry.isDirectory()).isFalse();
    assertThat(moduleEntry.shouldCompress()).isTrue();
  }

  @Test
  public void ofFile_shouldCompressFalse() {
    InMemoryModuleEntry moduleEntry =
        InMemoryModuleEntry.ofFile(PATH, CONTENT, /* shouldCompress = */ false);
    assertThat(moduleEntry.getPath().toString()).isEqualTo(PATH);
    assertThat(moduleEntry.getContentAsBytes().toByteArray()).isEqualTo(CONTENT);
    assertThat(moduleEntry.shouldCompress()).isFalse();
  }

  @Test
  public void ofFile_shouldCompressTrue() {
    InMemoryModuleEntry moduleEntry =
        InMemoryModuleEntry.ofFile(PATH, CONTENT, /* shouldCompress = */ true);
    assertThat(moduleEntry.getPath().toString()).isEqualTo(PATH);
    assertThat(moduleEntry.getContentAsBytes().toByteArray()).isEqualTo(CONTENT);
    assertThat(moduleEntry.shouldCompress()).isTrue();
  }

  @Test
  public void ofDirectory() {
    InMemoryModuleEntry moduleEntry = InMemoryModuleEntry.ofDirectory(PATH);
    assertThat(moduleEntry.getPath().toString()).isEqualTo(PATH);
    assertThat(moduleEntry.getContentAsBytes()).isEmpty();
    assertThat(moduleEntry.isDirectory()).isTrue();
  }
}
