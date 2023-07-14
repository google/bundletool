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

package com.android.tools.build.bundletool.validation;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

/** Validates a declarative watch face bundle */
public final class DeclarativeWatchFaceBundleValidator extends SubValidator {

  /**
   * We do not allow Declarative Watch Faces to install executable code on Wear 4. However, if a
   * Declarative Watch Face is built in the Wear 3 compatibility mode, then we must allow a minimal
   * dex file in its base module. We have a pre-compiled dex file with a known sha256, and we are
   * expecting the DWF bundle to contain a dex file with this sha if it has an embedded runtime
   * module.
   */
  private static final HashCode WEAR_3_COMPAT_DEX_SHA =
      HashCode.fromString("2891be8a0b1e950bb91e9c35b4dc763dd425b106218e20cd0a4eaaf40c2a9ca4");

  @Override
  public void validateBundle(AppBundle bundle) {
    if (BundleValidationUtils.isDeclarativeWatchFaceBundle(bundle)) {
      validateDwfBundle(bundle);
    }
  }

  private void validateDwfBundle(AppBundle bundle) {
    validateBundleContainsAtMostTwoModules(bundle);

    BundleModule baseModule = bundle.getBaseModule();

    validateBaseIsWatchModule(baseModule);

    validateBaseContainsLayoutDefinitions(baseModule);
    validateBaseHasNoExecutableComponents(baseModule);

    validateBaseHasNoLibs(baseModule);
    validateBaseHasNoCodeInRoot(baseModule);

    Optional<Entry<BundleModuleName, BundleModule>> optionalRuntime = getOptionalRuntime(bundle);

    optionalRuntime.ifPresent(this::validateRuntimeIsConditionallyInstalled);

    validateMinSdkIsCorrect(baseModule, optionalRuntime.isPresent());
    validateNoUnexpectedDexFiles(baseModule, optionalRuntime.isPresent());
  }

  private void validateBundleContainsAtMostTwoModules(AppBundle bundle) {
    ImmutableMap<BundleModuleName, BundleModule> modules = bundle.getModules();
    assertWithUserMessage(modules.size() <= 2, "Watch face bundle can have at most two modules.");
  }

  private void validateBaseIsWatchModule(BundleModule baseModule) {
    ImmutableList<XmlProtoElement> usesFeatureWatch =
        baseModule
            .getAndroidManifest()
            .getUsesFeatureElement(AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME);
    assertWithUserMessage(
        !usesFeatureWatch.isEmpty(),
        "Watch face apps are working only on watches. The AndroidManifest.xml must contain"
            + " a <uses-feature android:name=\"android.hardware.type.watch\" /> declaration.");
  }

  private void validateBaseContainsLayoutDefinitions(BundleModule baseModule) {
    boolean hasShapeDefinitions =
        baseModule
            .findEntries(
                entry ->
                    Objects.equals(entry.getFileName().toString(), "watch_face_shapes.xml")
                        && entry.toString().startsWith("res/xml"))
            .findFirst()
            .isPresent();
    if (!hasShapeDefinitions) {
      ZipPath requiredFilePath = ZipPath.create("/res/raw/watchface.xml");
      boolean fileExists = baseModule.findEntriesUnderPath(requiredFilePath).count() == 1;
      assertWithUserMessage(
          fileExists,
          "Watch face must contain an xml watch_face_shapes resource or a"
              + " default /res/raw/watchface.xml layout.");
    }
  }

  private void validateBaseHasNoExecutableComponents(BundleModule baseModule) {
    assertWithUserMessage(
        !baseModule.getAndroidManifest().hasComponents(),
        "Watch face base module cannot have any components and can only have resources.");
  }

  private Optional<Entry<BundleModuleName, BundleModule>> getOptionalRuntime(AppBundle bundle) {
    ImmutableMap<BundleModuleName, BundleModule> modules = bundle.getModules();
    return modules.entrySet().stream()
        .filter((entry) -> !entry.getValue().isBaseModule())
        .findFirst();
  }

  private void validateRuntimeIsConditionallyInstalled(
      Entry<BundleModuleName, BundleModule> runtimeModule) {
    BundleModule runtimeModuleValue = runtimeModule.getValue();

    assertWithUserMessage(
        runtimeModuleValue.getModuleType().isFeatureModule(),
        String.format("Module %s must be a feature module.", runtimeModule.getKey()));

    assertWithUserMessage(
        runtimeModuleValue.getDeliveryType().equals(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL),
        String.format("Module %s must be conditionally installed.", runtimeModule.getKey()));

    Optional<Integer> runtimeDeliveryMaxSdk =
        runtimeModuleValue
            .getAndroidManifest()
            .getManifestDeliveryElement()
            .flatMap(it -> it.getModuleConditions().getMaxSdkVersion());

    int sdkLevelWithRuntime = 33;

    assertWithUserMessage(
        runtimeDeliveryMaxSdk.isPresent() && runtimeDeliveryMaxSdk.get() < sdkLevelWithRuntime,
        String.format(
            "Watch face with embedded runtime must not install the runtime "
                + "on devices with API level >= %s.",
            sdkLevelWithRuntime));
  }

  private void validateMinSdkIsCorrect(BundleModule baseModule, boolean hasRuntime) {
    int baseMinSdk = baseModule.getAndroidManifest().getEffectiveMinSdkVersion();
    if (hasRuntime) {
      assertWithUserMessage(
          baseMinSdk == 30, "Watch face with embedded runtime must have minSdk 30.");
    } else {
      assertWithUserMessage(
          baseMinSdk >= 33, "Watch face without embedded runtime must have minSdk >= 33.");
    }
  }

  private void validateNoUnexpectedDexFiles(BundleModule baseModule, boolean hasRuntime) {
    ImmutableList<ModuleEntry> dexFiles =
        baseModule.findEntriesUnderPath(BundleModule.DEX_DIRECTORY).collect(toImmutableList());
    if (!hasRuntime) {
      assertWithUserMessage(
          dexFiles.isEmpty(), "Watch face with minSdk >= 33 cannot have dex files.");
    } else {
      assertWithUserMessage(
          dexFiles.size() == 1
              && Objects.equals(dexFiles.get(0).getContentSha256Hash(), WEAR_3_COMPAT_DEX_SHA),
          "Watch face with embedded runtime must have a single, expected dex file in"
              + " its base module.");
    }
  }

  private void validateBaseHasNoLibs(BundleModule baseModule) {
    boolean hasLibs =
        baseModule.findEntriesUnderPath(BundleModule.LIB_DIRECTORY).findFirst().isPresent();
    assertWithUserMessage(!hasLibs, "Watch face cannot have any external libraries.");
  }

  private void validateBaseHasNoCodeInRoot(BundleModule baseModule) {
    boolean hasCodeInRoot =
        baseModule
            .findEntriesUnderPath(BundleModule.ROOT_DIRECTORY)
            .anyMatch(
                entry -> {
                  String fileName = entry.getPath().toString();
                  return fileName.endsWith(".so") || fileName.endsWith(".dex");
                });
    assertWithUserMessage(
        !hasCodeInRoot, "Watch face cannot have any compiled code in its root folder.");
  }

  private void assertWithUserMessage(boolean condition, String message) {
    if (!condition) {
      throw InvalidBundleException.builder().withUserMessage(message).build();
    }
  }
}
