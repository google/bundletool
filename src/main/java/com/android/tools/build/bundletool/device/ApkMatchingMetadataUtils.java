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

package com.android.tools.build.bundletool.device;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.ApkMatchingMetadata;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides collections to iterate over for selecting which APKs to serve to a given device.
 *
 * <p>The following common format of entries is used:
 *
 * <ul>
 *   <li>key: path to the APK
 *   <li>value: {@link ApkMatchingMetadata} message containing targeting information for selecting
 *       which APKs to serve.
 * </ul>
 */
public class ApkMatchingMetadataUtils {

  /** Returns the map for selecting APKs from the table of contents output of build-apks command. */
  public static ImmutableMap<Path, ApkMatchingMetadata> toApkMatchingMap(BuildApksResult result) {
    ImmutableMap.Builder<Path, ApkMatchingMetadata> mapBuilder = new Builder<>();
    for (Variant variant : result.getVariantList()) {
      VariantTargeting variantTargeting = variant.getTargeting();
      for (ApkSet apkSet : variant.getApkSetList()) {
        ModuleMetadata moduleMetadata = apkSet.getModuleMetadata();
        for (ApkDescription apkDescription : apkSet.getApkDescriptionList()) {
          mapBuilder.put(
              Paths.get(apkDescription.getPath()),
              ApkMatchingMetadata.newBuilder()
                  .setVariantTargeting(variantTargeting)
                  .setModuleMetadata(moduleMetadata)
                  .setApkTargeting(apkDescription.getTargeting())
                  .build());
        }
      }
    }
    return mapBuilder.build();
  }
}
