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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.device.BadgingInfoParser.BadgingInfo;
import com.android.tools.build.bundletool.model.exceptions.AdbOutputParseException;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BadgingInfoParserTest {

  @Test
  public void parse_matchingCase() {
    // GIVEN an input with a package name...
    ImmutableList<String> input =
        ImmutableList.of(
            "package: name='com.google.android.media.swcodec' versionCode='292200000'"
                + " versionName='' platformBuildVersionName='' platformBuildVersionCode=''"
                + " compileSdkVersion='29' compileSdkVersionCodename='10'",
            "application: label='' icon=''",
            "sdkVersion:'29'",
            "maxSdkVersion:'29'",
            "");

    // WHEN parsed
    BadgingInfo packageName = BadgingInfoParser.parse(input);

    // THEN the correct package name is returned.
    assertThat(packageName)
        .isEqualTo(BadgingInfo.create("com.google.android.media.swcodec", 292200000));
  }

  @Test
  public void parse_missingPackageLine() {
    // GIVEN an input without a package line...
    ImmutableList<String> input =
        ImmutableList.of(
            "application: label='' icon=''", "sdkVersion:'29'", "maxSdkVersion:'29'", "");

    // WHEN parsed
    AdbOutputParseException e =
        assertThrows(AdbOutputParseException.class, () -> BadgingInfoParser.parse(input));

    // THEN an IllegalArgumentException is thrown.
    assertThat(e).hasMessageThat().contains("line not found in badging");
  }

  @Test
  public void parse_missingPackageName() {
    // GIVEN an input without a package name...
    ImmutableList<String> input =
        ImmutableList.of(
            "package: versionCode='292200000'",
            "application: label='' icon=''",
            "sdkVersion:'29'",
            "maxSdkVersion:'29'",
            "");

    // WHEN parsed
    AdbOutputParseException e =
        assertThrows(AdbOutputParseException.class, () -> BadgingInfoParser.parse(input));

    // THEN an IllegalArgumentException is thrown.
    assertThat(e).hasMessageThat().contains("'name=' and 'versionCode=' not found in package line");
  }
}
