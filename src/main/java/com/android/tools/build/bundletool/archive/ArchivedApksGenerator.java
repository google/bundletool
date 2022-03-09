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
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.splitters.ResourceAnalyzer;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Generates archived apk based on provided app bundle. Leaves only minimal manifest, only required
 * resources and two custom actions to clear app cache and to wake up an app.
 */
public final class ArchivedApksGenerator {
  private static final String ARCHIVED_CLASSES_DEX_PATH = "dex/classes.dex";

  private final TempDirectory globalTempDir;

  @Inject
  ArchivedApksGenerator(TempDirectory globalTempDir) {
    this.globalTempDir = globalTempDir;
  }

  public ModuleSplit generateArchivedApk(AppBundle appBundle) throws IOException {
    validateRequest(appBundle);

    BundleModule baseModule = appBundle.getBaseModule();

    AndroidManifest archivedManifest =
        ArchivedAndroidManifestUtils.createArchivedManifest(baseModule.getAndroidManifest());
    Optional<ResourceTable> archivedResourceTable =
        getArchivedResourceTable(appBundle, baseModule, archivedManifest);
    Path archivedClassesDexFile = getArchivedClassesDexFile();

    return ModuleSplit.forArchive(
        baseModule, archivedManifest, archivedResourceTable, archivedClassesDexFile);
  }

  private void validateRequest(AppBundle appBundle) {
    checkNotNull(appBundle);

    if (!ARCHIVED_APK_GENERATION.enabledForVersion(
        BundleToolVersion.getVersionFromBundleConfig(appBundle.getBundleConfig()))) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              String.format(
                  "Archived APK can only be generated for bundles built with version %s or higher.",
                  ARCHIVED_APK_GENERATION.getEnabledSinceVersion()))
          .build();
    }

    if (!appBundle.storeArchiveEnabled()) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Archived APK cannot be generated when Store Archive configuration is disabled.")
          .build();
    }
  }

  private Optional<ResourceTable> getArchivedResourceTable(
      AppBundle appBundle, BundleModule bundleModule, AndroidManifest archivedManifest)
      throws IOException {
    if (!bundleModule.getResourceTable().isPresent()) {
      return Optional.empty();
    }

    ImmutableSet<ResourceId> referredResources =
        new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromManifest(archivedManifest);
    ResourceTable archivedResourceTable =
        ResourcesUtils.filterResourceTable(
            bundleModule.getResourceTable().get(),
            /* removeEntryPredicate= */ entry -> !referredResources.contains(entry.getResourceId()),
            /* configValuesFilterFn= */ ResourceTableEntry::getEntry);

    return Optional.of(archivedResourceTable);
  }

  private Path getArchivedClassesDexFile() throws IOException {
    Path archivedDexFilePath = Files.createTempFile(globalTempDir.getPath(), "classes", ".dex");
    try (InputStream inputStream = readArchivedClassesDexFile()) {
      Files.copy(inputStream, archivedDexFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
    return archivedDexFilePath;
  }

  private static InputStream readArchivedClassesDexFile() {
    return ArchivedApksGenerator.class.getResourceAsStream(ARCHIVED_CLASSES_DEX_PATH);
  }
}
