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

import com.android.tools.build.bundletool.device.PackagesParser.InstalledPackageInfo;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PackagesParserTest {
  private static final ImmutableList<String> LIST_PACKAGES_OUTPUT =
      ImmutableList.of(
          "package:com.android.bluetooth versionCode:123",
          "",
          "package:com.android.providers.contacts versionCode:4",
          "package:com.android.theme.icon.roundedrect versionCode:56");

  @Test
  public void listPackagesOutput() {
    assertThat(new PackagesParser(/* isApex= */ false).parse(LIST_PACKAGES_OUTPUT))
        .containsExactly(
            InstalledPackageInfo.create("com.android.bluetooth", 123, false),
            InstalledPackageInfo.create("com.android.providers.contacts", 4, false),
            InstalledPackageInfo.create("com.android.theme.icon.roundedrect", 56, false));
  }

  @Test
  public void listApexPackagesOutput() {
    assertThat(new PackagesParser(/* isApex= */ true).parse(LIST_PACKAGES_OUTPUT))
        .containsExactly(
            InstalledPackageInfo.create("com.android.bluetooth", 123, true),
            InstalledPackageInfo.create("com.android.providers.contacts", 4, true),
            InstalledPackageInfo.create("com.android.theme.icon.roundedrect", 56, true));
  }
}
