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

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;

/** Validator ensuring that the module inside an SDK bundle is named correctly. */
final class SdkBundleModuleNameValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    if (!module.getName().equals(BASE_MODULE_NAME)) {
      throw InvalidBundleException.builder()
          .withUserMessage("The SDK bundle module must be named '%s'.", BASE_MODULE_NAME)
          .build();
    }
  }
}
