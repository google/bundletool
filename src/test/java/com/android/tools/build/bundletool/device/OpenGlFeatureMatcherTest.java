/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.device.OpenGlFeatureMatcher.CONDITIONAL_MODULES_OPEN_GL_NAME;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceFeatureTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OpenGlFeatureMatcherTest {

  @Test
  public void exactOpenGlVersion_match() {
    OpenGlFeatureMatcher openGlFeatureMatcher =
        new OpenGlFeatureMatcher(deviceFeatures("reqGlEsVersion=0x30001"));

    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(
            deviceFeatureTargeting("com.feature"),
            deviceFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30001));

    assertThat(openGlFeatureMatcher.matchesTargeting(deviceFeatureTargetings)).isTrue();
  }

  @Test
  public void greaterOpenGlVersion_match() {
    OpenGlFeatureMatcher openGlFeatureMatcher =
        new OpenGlFeatureMatcher(deviceFeatures("reqGlEsVersion=0x30002"));

    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(deviceFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30000));

    assertThat(openGlFeatureMatcher.matchesTargeting(deviceFeatureTargetings)).isTrue();
  }

  @Test
  public void lowerOpenGlVersion_noMatch() {
    OpenGlFeatureMatcher openGlFeatureMatcher =
        new OpenGlFeatureMatcher(deviceFeatures("reqGlEsVersion=0x30000"));

    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(deviceFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30002));

    assertThat(openGlFeatureMatcher.matchesTargeting(deviceFeatureTargetings)).isFalse();
  }

  @Test
  public void noOpenGlCondition_match() {
    OpenGlFeatureMatcher openGlFeatureMatcher =
        new OpenGlFeatureMatcher(deviceFeatures("reqGlEsVersion=0x30000"));

    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(deviceFeatureTargeting("com.feature"));

    assertThat(openGlFeatureMatcher.matchesTargeting(deviceFeatureTargetings)).isTrue();
  }

  @Test
  public void noOpenGlVersionInDeviceSpec_conditionOnOpenGl_noMatch() {
    OpenGlFeatureMatcher openGlFeatureMatcher =
        new OpenGlFeatureMatcher(deviceFeatures("com.some.feature"));

    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(deviceFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30000));

    assertThat(openGlFeatureMatcher.matchesTargeting(deviceFeatureTargetings)).isFalse();
  }
}
