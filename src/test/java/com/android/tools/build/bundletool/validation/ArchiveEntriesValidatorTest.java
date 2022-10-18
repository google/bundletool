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

import static com.android.tools.build.bundletool.archive.ArchivedResourcesHelper.ARCHIVED_ICON_DRAWABLE_NAME;
import static com.android.tools.build.bundletool.archive.ArchivedResourcesHelper.ARCHIVED_ROUND_ICON_DRAWABLE_NAME;
import static com.android.tools.build.bundletool.archive.ArchivedResourcesHelper.ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME;
import static com.android.tools.build.bundletool.archive.ArchivedResourcesHelper.CLOUD_SYMBOL_DRAWABLE_NAME;
import static com.android.tools.build.bundletool.archive.ArchivedResourcesHelper.OPACITY_LAYER_DRAWABLE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ArchiveEntriesValidatorTest {

  private static final byte[] FILE_CONTENT = new byte[1];

  @Test
  public void allEntriesValid() {
    String filePathDex = "dex/tmp.dex";
    String filePathDrawable = "res/drawable/tmp.xml";
    String confilictingFilePath = "res/drawable/" + CLOUD_SYMBOL_DRAWABLE_NAME + ".xml";
    BundleModule base =
        new BundleModuleBuilder("base")
            .addFile(filePathDex, FILE_CONTENT)
            .addFile(filePathDrawable, FILE_CONTENT)
            .setManifest(androidManifest("com.test.app"))
            .build();
    // only conflicting resources in base will be checke, because in archive mode we only deal with
    // base module.
    BundleModule feature1 =
        new BundleModuleBuilder("feature1")
            .addFile(confilictingFilePath, FILE_CONTENT)
            .setManifest(androidManifest("com.test.app"))
            .build();

    // No exception = pass.
    new ArchiveEntriesValidator().validateAllModules(ImmutableList.of(base, feature1));
  }

  @Test
  public void hasConflicitingEntries_throws() {
    String filePathDex = "dex/tmp.dex";
    String filePathDrawable = "res/drawable/tmp.xml";
    String conflictingFilePath1 = "res/drawable/" + CLOUD_SYMBOL_DRAWABLE_NAME + ".xml";
    String conflictingFilePath2 = "res/drawable/" + OPACITY_LAYER_DRAWABLE_NAME + ".xml";
    String conflictingFilePath3 = "res/drawable/" + ARCHIVED_ICON_DRAWABLE_NAME + ".xml";
    String conflictingFilePath4 = "res/drawable/" + ARCHIVED_ROUND_ICON_DRAWABLE_NAME + ".xml";
    String conflictingFilePath5 = "res/layout/" + ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME + ".xml";
    BundleModule base =
        new BundleModuleBuilder("base")
            .addFile(filePathDex, FILE_CONTENT)
            .addFile(conflictingFilePath1, FILE_CONTENT)
            .addFile(conflictingFilePath2, FILE_CONTENT)
            .addFile(conflictingFilePath3, FILE_CONTENT)
            .addFile(conflictingFilePath4, FILE_CONTENT)
            .addFile(conflictingFilePath5, FILE_CONTENT)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule feature1 =
        new BundleModuleBuilder("feature1")
            .addFile(filePathDrawable, FILE_CONTENT)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new ArchiveEntriesValidator().validateAllModules(ImmutableList.of(base, feature1)));
    ImmutableList<String> validPaths = ImmutableList.of(filePathDex, filePathDrawable);
    ImmutableList<String> invalidPaths =
        ImmutableList.of(
            conflictingFilePath1,
            conflictingFilePath2,
            conflictingFilePath3,
            conflictingFilePath4,
            conflictingFilePath5);
    for (String validPath : validPaths) {
      assertThat(exception).hasMessageThat().doesNotContain(validPath);
    }
    for (String invalidPath : invalidPaths) {
      assertThat(exception).hasMessageThat().contains(invalidPath);
    }
  }
}
