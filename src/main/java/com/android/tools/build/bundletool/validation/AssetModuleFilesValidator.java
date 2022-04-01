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

package com.android.tools.build.bundletool.validation;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.function.Predicate;

/** Asset modules specific validations. */
public class AssetModuleFilesValidator extends SubValidator {

  private static final Predicate<ZipPath> IS_INVALID_ASSET_MODULE_ENTRY =
      path ->
          !(path.startsWith(BundleModule.ASSETS_DIRECTORY)
              || path.startsWith(BundleModule.MANIFEST_DIRECTORY));

  @Override
  public void validateModule(BundleModule module) {
    if (!module.getModuleType().equals(ModuleType.ASSET_MODULE)) {
      return;
    }
    validateNoResourceTable(module);
    validateOnlyAssetsAndManifest(module);
    validateNoNativeOrApexConfig(module);
    validateNoRuntimeEnabledSdkConfig(module);
  }

  private void validateNoResourceTable(BundleModule module) {
    validate(
        !module.getResourceTable().isPresent(),
        "Unexpected resource table found in asset pack '%s'.",
        module.getName());
  }

  private void validateOnlyAssetsAndManifest(BundleModule module) {
    ImmutableList<String> illegalPaths =
        module
            .findEntries(IS_INVALID_ASSET_MODULE_ENTRY)
            .map(ModuleEntry::getPath)
            .map(ZipPath::toString)
            .collect(toImmutableList());
    validate(
        illegalPaths.isEmpty(),
        "Invalid %s found in asset pack '%s': '%s'.",
        illegalPaths.size() == 1 ? "entry" : "entries",
        module.getName(),
        String.join("', '", illegalPaths));
  }

  private void validateNoNativeOrApexConfig(BundleModule module) {
    validate(
        !module.getNativeConfig().isPresent(),
        "Native libraries config not allowed in asset packs, but found in '%s'.",
        module.getName());
    validate(
        !module.getApexConfig().isPresent(),
        "Apex config not allowed in asset packs, but found in '%s'.",
        module.getName());
  }

  private void validateNoRuntimeEnabledSdkConfig(BundleModule module) {
    validate(
        !module.getRuntimeEnabledSdkConfig().isPresent(),
        "Runtime-enabled SDK config not allowed in asset packs, but found in '%s'.",
        module.getName());
  }

  @FormatMethod
  private void validate(boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw InvalidBundleException.builder().withUserMessage(message, args).build();
    }
  }
}
