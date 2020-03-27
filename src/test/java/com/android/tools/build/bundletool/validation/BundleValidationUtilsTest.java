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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleValidationUtilsTest {

  @Test
  public void directoryContainsNoFiles_doesNotEvenExist() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    boolean result = BundleValidationUtils.directoryContainsNoFiles(module, ZipPath.create("lib"));

    assertThat(result).isTrue();
  }

  @Test
  public void directoryContainsNoFiles_containsAFile() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/file.txt")
            .addFile("lib/x86/libX.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    boolean result = BundleValidationUtils.directoryContainsNoFiles(module, ZipPath.create("lib"));

    assertThat(result).isFalse();
  }
}
