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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigStringIteratorTest {

  @Test
  public void saveRestorePosition() {
    ConfigStringIterator iterator = new ConfigStringIterator("abc-def-ghi");
    iterator.next();
    assertThat(iterator.getValue()).isEqualTo("abc");
    iterator.savePosition();
    iterator.next();
    iterator.next();
    assertThat(iterator.getValue()).isEqualTo("ghi");
    iterator.restorePosition();
    assertThat(iterator.getValue()).isEqualTo("abc");
  }

  @Test
  public void savePosition_defaultsToBeginning() {
    ConfigStringIterator iterator = new ConfigStringIterator("abc-def-ghi");
    iterator.next();
    assertThat(iterator.getValue()).isEqualTo("abc");
    iterator.restorePosition();
    iterator.next();
    assertThat(iterator.getValue()).isEqualTo("abc");
  }
}
