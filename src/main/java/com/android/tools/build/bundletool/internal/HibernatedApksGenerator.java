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

package com.android.tools.build.bundletool.internal;

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
 * Generates hibernated apk based on provided app bundle. Leaves only minimal manifest, only
 * required resources and two custom actions to clear app cache and to wake up an app.
 */
public final class HibernatedApksGenerator {
  private static final String HIBERNATED_CLASSES_DEX_PATH = "dex/classes.dex";

  private final TempDirectory globalTempDir;

  @Inject
  HibernatedApksGenerator(TempDirectory globalTempDir) {
    this.globalTempDir = globalTempDir;
  }

  public ModuleSplit generateHibernatedApk(AppBundle appBundle) throws IOException {
    checkNotNull(appBundle);
    if (!appBundle.storeArchiveEnabled()) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Hibernated APK cannot be generated when Store Archive configuration is disabled.")
          .build();
    }

    BundleModule baseModule = appBundle.getBaseModule();

    AndroidManifest hibernatedManifest =
        HibernatedAndroidManifestUtils.createHibernatedManifest(baseModule.getAndroidManifest());
    Optional<ResourceTable> hibernatedResourceTable =
        getHibernatedResourceTable(appBundle, baseModule, hibernatedManifest);
    Path hibernatedClassesDexFile = getHibernatedClassesDexFile();

    return ModuleSplit.forHibernation(
        baseModule, hibernatedManifest, hibernatedResourceTable, hibernatedClassesDexFile);
  }

  private Optional<ResourceTable> getHibernatedResourceTable(
      AppBundle appBundle, BundleModule bundleModule, AndroidManifest hibernatedManifest)
      throws IOException {
    if (!bundleModule.getResourceTable().isPresent()) {
      return Optional.empty();
    }

    ImmutableSet<ResourceId> referredResources =
        new ResourceAnalyzer(appBundle)
            .findAllAppResourcesReachableFromManifest(hibernatedManifest);
    ResourceTable hibernatedResourceTable =
        ResourcesUtils.filterResourceTable(
            bundleModule.getResourceTable().get(),
            /* removeEntryPredicate= */ entry -> !referredResources.contains(entry.getResourceId()),
            /* configValuesFilterFn= */ ResourceTableEntry::getEntry);

    return Optional.of(hibernatedResourceTable);
  }

  private Path getHibernatedClassesDexFile() throws IOException {
    Path hibernatedDexFilePath = Files.createTempFile(globalTempDir.getPath(), "classes", ".dex");
    try (InputStream inputStream = readHibernatedClassesDexFile()) {
      Files.copy(inputStream, hibernatedDexFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
    return hibernatedDexFilePath;
  }

  private static InputStream readHibernatedClassesDexFile() {
    return HibernatedApksGenerator.class.getResourceAsStream(HIBERNATED_CLASSES_DEX_PATH);
  }
}
