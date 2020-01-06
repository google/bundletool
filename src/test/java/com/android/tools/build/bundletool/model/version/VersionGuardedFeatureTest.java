/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.build.bundletool.model.version;

import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_V1_SIGNING_WHEN_POSSIBLE;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VersionGuardedFeatureTest {

  @Test
  public void testShouldNotBeEnabled() {
    assertThat(NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(Version.of("0.10.0"))).isFalse();
    assertThat(NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(Version.of("0.11.0-dev"))).isFalse();
  }

  @Test
  public void testShouldBeEnabled() {
    assertThat(NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(Version.of("0.11.0"))).isTrue();
    assertThat(NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(Version.of("1.0.0"))).isTrue();
  }
}
