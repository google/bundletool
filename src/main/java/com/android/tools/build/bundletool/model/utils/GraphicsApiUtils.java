/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.bundle.Targeting.GraphicsApi;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.OpenGlVersion;
import com.android.bundle.Targeting.VulkanVersion;

/** Helpers related to the Graphics API. */
public final class GraphicsApiUtils {

  public static GraphicsApiTargeting openGlVersionFrom(int major, int minor) {
    return GraphicsApiTargeting.newBuilder()
        .addValue(
            GraphicsApi.newBuilder()
                .setMinOpenGlVersion(OpenGlVersion.newBuilder().setMajor(major).setMinor(minor)))
        .build();
  }

  public static GraphicsApiTargeting vulkanVersionFrom(int major, int minor) {
    return GraphicsApiTargeting.newBuilder()
        .addValue(
            GraphicsApi.newBuilder()
                .setMinVulkanVersion(VulkanVersion.newBuilder().setMajor(major).setMinor(minor)))
        .build();
  }
}
