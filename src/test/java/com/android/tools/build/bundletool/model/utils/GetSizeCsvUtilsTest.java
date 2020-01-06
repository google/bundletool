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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ABI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.model.utils.CsvFormatter.CRLF;
import static com.android.tools.build.bundletool.model.utils.GetSizeCsvUtils.getSizeTotalOutputInCsv;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GetSizeCsvUtilsTest {

  @Test
  public void getSizeTotalOutputInCsv_emptyDimensions() {
    assertThat(
            getSizeTotalOutputInCsv(
                ConfigurationSizes.create(
                    ImmutableMap.of(SizeConfiguration.getDefaultInstance(), 10L),
                    ImmutableMap.of(SizeConfiguration.getDefaultInstance(), 15L)),
                ImmutableSet.of()))
        .isEqualTo("MIN,MAX" + CRLF + "10,15" + CRLF);
  }

  @Test
  public void getSizeTotalOutputInCsv_withDimensions() {
    assertThat(
            getSizeTotalOutputInCsv(
                ConfigurationSizes.create(
                    ImmutableMap.of(
                        SizeConfiguration.builder().setSdkVersion("21-").setAbi("x86").build(),
                        5L,
                        SizeConfiguration.builder()
                            .setSdkVersion("21-")
                            .setAbi("armeabi-v7a")
                            .build(),
                        2L),
                    ImmutableMap.of(
                        SizeConfiguration.builder().setSdkVersion("21-").setAbi("x86").build(),
                        10L,
                        SizeConfiguration.builder()
                            .setSdkVersion("21-")
                            .setAbi("armeabi-v7a")
                            .build(),
                        15L)),
                ImmutableSet.of(ABI, SDK)))
        .isEqualTo(
            "SDK,ABI,MIN,MAX" + CRLF + "21-,x86,5,10" + CRLF + "21-,armeabi-v7a,2,15" + CRLF);
  }

  @Test
  public void getSizeTotalOutputInCsv_withDimensionsAndCommasInConfiguration() {
    assertThat(
            getSizeTotalOutputInCsv(
                ConfigurationSizes.create(
                    ImmutableMap.of(
                        SizeConfiguration.builder()
                            .setSdkVersion("22")
                            .setAbi("x86,armeabi-v7a")
                            .setScreenDensity("480")
                            .setLocale("en,fr")
                            .build(),
                        1L),
                    ImmutableMap.of(
                        SizeConfiguration.builder()
                            .setSdkVersion("22")
                            .setAbi("x86,armeabi-v7a")
                            .setScreenDensity("480")
                            .setLocale("en,fr")
                            .build(),
                        6L)),
                ImmutableSet.of(SCREEN_DENSITY, ABI, LANGUAGE, SDK)))
        .isEqualTo(
            "SDK,ABI,SCREEN_DENSITY,LANGUAGE,MIN,MAX"
                + CRLF
                + "22,\"x86,armeabi-v7a\",480,\"en,fr\",1,6"
                + CRLF);
  }
}
