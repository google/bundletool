/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.build.bundletool.validation;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;

/**
 * Validates that Resource IDs in the SDK module are allocated in the {@value
 * #SDK_BUNDLE_PACKAGE_ID} space.
 */
public class SdkBundleModuleResourceIdValidator extends SubValidator {

  public static final int SDK_BUNDLE_PACKAGE_ID = 0x7f;

  @Override
  public void validateModule(BundleModule module) {
    ResourceTable resourceTable =
        module.getResourceTable().orElse(ResourceTable.getDefaultInstance());
    resourceTable
        .getPackageList()
        .forEach(
            aPackage -> {
              if (aPackage.getPackageId().getId() != SDK_BUNDLE_PACKAGE_ID) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "SDK Bundle Resource IDs must be in the %s space.",
                        Integer.toHexString(SDK_BUNDLE_PACKAGE_ID))
                    .build();
              }
            });
  }
}
