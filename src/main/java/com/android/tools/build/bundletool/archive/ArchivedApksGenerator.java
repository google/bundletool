/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.build.bundletool.archive;

import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.ARCHIVED_APK_GENERATION;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.STORE_ARCHIVE_ENABLED_BY_DEFAULT;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.commands.BuildApksModule.UpdateIconInArchiveMode;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceInjector;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.splitters.ResourceAnalyzer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Generates archived apk based on provided app bundle. Leaves only minimal manifest, only required
 * resources and two custom actions to clear app cache and to wake up an app.
 */
public final class ArchivedApksGenerator {
  private final boolean updateIconInArchiveMode;

  @Inject
  ArchivedApksGenerator(@UpdateIconInArchiveMode boolean updateIconInArchiveMode) {
    this.updateIconInArchiveMode = updateIconInArchiveMode;
  }

  public ModuleSplit generateArchivedApk(
      AppBundle appBundle, Optional<String> customAppStorePackageName) throws IOException {
    validateRequest(appBundle);

    BundleModule baseModule = appBundle.getBaseModule();

    AndroidManifest archivedManifest =
        ArchivedAndroidManifestUtils.createArchivedManifest(baseModule.getAndroidManifest());
    ResourceTable archivedResourceTable =
        getArchivedResourceTable(appBundle, baseModule, archivedManifest);

    Optional<XmlProtoAttribute> iconAttribute = archivedManifest.getIconAttribute();
    Optional<XmlProtoAttribute> roundIconAttribute = archivedManifest.getRoundIconAttribute();
    ResourceInjector resourceInjector =
        new ResourceInjector(archivedResourceTable.toBuilder(), appBundle.getPackageName());

    ImmutableMap<ZipPath, ByteSource> additionalResourcesByByteSource = ImmutableMap.of();
    if (updateIconInArchiveMode) {
      ImmutableMap<String, Integer> extraResourceNameToIdMap =
          ArchivedResourcesUtils.injectExtraResources(
              resourceInjector, customAppStorePackageName, iconAttribute, roundIconAttribute);

      additionalResourcesByByteSource =
          ArchivedResourcesUtils.buildAdditionalResourcesByByteSourceMap(
              extraResourceNameToIdMap.get(ArchivedResourcesUtils.CLOUD_SYMBOL_DRAWABLE_NAME),
              extraResourceNameToIdMap.get(ArchivedResourcesUtils.OPACITY_LAYER_DRAWABLE_NAME),
              iconAttribute,
              roundIconAttribute);

      archivedManifest =
          ArchivedAndroidManifestUtils.updateArchivedIcons(
              archivedManifest, extraResourceNameToIdMap);
    } else {
      resourceInjector.addStringResource(
          ArchivedResourcesUtils.APP_STORE_PACKAGE_NAME_RESOURCE_NAME,
          ArchivedResourcesUtils.getAppStorePackageName(customAppStorePackageName));
      additionalResourcesByByteSource =
          ImmutableMap.of(
              BundleModule.DEX_DIRECTORY.resolve("classes.dex"),
              ArchivedResourcesUtils.getResourceByteSource(
                  ArchivedResourcesUtils.ARCHIVED_CLASSES_DEX_PATH));
    }

    return ModuleSplit.forArchive(
        baseModule, archivedManifest, resourceInjector.build(), additionalResourcesByByteSource);
  }

  private void validateRequest(AppBundle appBundle) {
    checkNotNull(appBundle);
    Version bundletoolVersion =
        BundleToolVersion.getVersionFromBundleConfig(appBundle.getBundleConfig());
    if (!ARCHIVED_APK_GENERATION.enabledForVersion(bundletoolVersion)) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              String.format(
                  "Archived APK can only be generated for bundles built with version %s or higher.",
                  ARCHIVED_APK_GENERATION.getEnabledSinceVersion()))
          .build();
    }

    Optional<Boolean> storeArchiveConfig = appBundle.getStoreArchive();
    boolean isStoreArchiveEnabledByDefault =
        STORE_ARCHIVE_ENABLED_BY_DEFAULT.enabledForVersion(bundletoolVersion);
    if (!storeArchiveConfig.orElse(isStoreArchiveEnabledByDefault)) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Archived APK cannot be generated when Store Archive configuration is disabled.")
          .build();
    }

    if (appBundle.getBaseModule().getAndroidManifest().isHeadless()) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Archived APK can not be generated for applications without a launcher activity.")
          .build();
    }
  }

  private ResourceTable getArchivedResourceTable(
      AppBundle appBundle, BundleModule bundleModule, AndroidManifest archivedManifest)
      throws IOException {
    ResourceTable.Builder archivedResourceTable = ResourceTable.newBuilder();
    if (bundleModule.getResourceTable().isPresent()) {
      ImmutableSet<ResourceId> referredResources =
          new ResourceAnalyzer(appBundle)
              .findAllAppResourcesReachableFromManifest(archivedManifest);
      archivedResourceTable =
          ResourcesUtils.filterResourceTable(
              bundleModule.getResourceTable().get(),
              /* removeEntryPredicate= */ entry ->
                  !referredResources.contains(entry.getResourceId()),
              /* configValuesFilterFn= */ ResourceTableEntry::getEntry)
              .toBuilder();
    }
    return archivedResourceTable.build();
  }
}
