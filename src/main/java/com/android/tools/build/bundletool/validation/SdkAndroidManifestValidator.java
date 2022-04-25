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

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_LOCATION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_GROUP_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_TREE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.RECEIVER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_ID_ATTRIBUTE_NAME;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;

/** Validates {@code AndroidManifest.xml} file for the SDK module. */
public class SdkAndroidManifestValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    validateNoSdkLibraryElement(manifest);
    validateNoSdkPatchVersionProperty(manifest);
    validateInternalOnlyIfInstallLocationSet(manifest);
    validateNoPermissions(manifest);
    validateNoSharedUserId(manifest);
    validateNoComponents(manifest);
    validateNoSplitId(manifest);
  }

  private void validateNoSdkLibraryElement(AndroidManifest manifest) {
    if (!manifest.getSdkLibraryElements().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<%s> cannot be declared in the manifest of an SDK bundle.", SDK_LIBRARY_ELEMENT_NAME)
          .build();
    }
  }

  private void validateNoSdkPatchVersionProperty(AndroidManifest manifest) {
    if (manifest.getSdkPatchVersionProperty().isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<%s> cannot be declared with name='%s' in the manifest of an SDK bundle.",
              PROPERTY_ELEMENT_NAME, SDK_PATCH_VERSION_ATTRIBUTE_NAME)
          .build();
    }
  }

  private void validateInternalOnlyIfInstallLocationSet(AndroidManifest manifest) {
    if (manifest.getInstallLocationValue().isPresent()
        && !manifest.getInstallLocationValue().get().equals("internalOnly")) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "'%s' in <manifest> must be 'internalOnly' for SDK bundles if it is set.",
              INSTALL_LOCATION_ATTRIBUTE_NAME)
          .build();
    }
  }

  private void validateNoPermissions(AndroidManifest manifest) {
    if (!manifest.getPermissions().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<%s> cannot be declared in the manifest of an SDK bundle.", PERMISSION_ELEMENT_NAME)
          .build();
    }

    if (!manifest.getPermissionGroups().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<%s> cannot be declared in the manifest of an SDK bundle.",
              PERMISSION_GROUP_ELEMENT_NAME)
          .build();
    }

    if (!manifest.getPermissionTrees().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<%s> cannot be declared in the manifest of an SDK bundle.",
              PERMISSION_TREE_ELEMENT_NAME)
          .build();
    }
  }

  private void validateNoSharedUserId(AndroidManifest manifest) {
    if (manifest.hasSharedUserId()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "'%s' attribute cannot be used in the manifest of an SDK bundle.",
              SHARED_USER_ID_ATTRIBUTE_NAME)
          .build();
    }
  }

  private void validateNoComponents(AndroidManifest manifest) {
    if (manifest.hasComponents()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "None of <%s>, <%s>, <%s>, or <%s> can be declared in the manifest of an SDK bundle.",
              ACTIVITY_ELEMENT_NAME,
              SERVICE_ELEMENT_NAME,
              PROVIDER_ELEMENT_NAME,
              RECEIVER_ELEMENT_NAME)
          .build();
    }
  }

  private void validateNoSplitId(AndroidManifest manifest) {
    if (manifest.getSplitId().isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage("'split' attribute cannot be used in the manifest of an SDK bundle.")
          .build();
    }
  }
}
