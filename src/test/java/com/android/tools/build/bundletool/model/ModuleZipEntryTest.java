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
package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class ModuleZipEntryTest {

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Mock ZipFile zipFile;

  @Test
  public void fromBundleZipEntry_getPathRemovesModuleDirectory() {
    ModuleZipEntry moduleEntry =
        ModuleZipEntry.fromBundleZipEntry(new ZipEntry("/module1/resource1"), zipFile);
    assertThat(moduleEntry.getPath().toString()).isEqualTo("resource1");
  }

  @Test
  public void fromModuleZipEntry_getPathPreservesAllDirectories() {
    ModuleZipEntry moduleEntry =
        ModuleZipEntry.fromModuleZipEntry(new ZipEntry("/resource1"), zipFile);
    assertThat(moduleEntry.getPath().toString()).isEqualTo("resource1");
  }

  @Test
  public void fromBundleZipEntry_pathTooShort_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ModuleZipEntry.fromBundleZipEntry(new ZipEntry("AndroidManifest.xml"), zipFile));
  }

  @Test
  public void fromModuleZipEntry_pathTooShort_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ModuleZipEntry.fromBundleZipEntry(new ZipEntry(""), zipFile));
  }

  @Test
  public void getPath_deepResourcePath() {
    ModuleZipEntry moduleEntry =
        ModuleZipEntry.fromBundleZipEntry(new ZipEntry("/module2/assets/en-gb/text.txt"), zipFile);
    assertThat(moduleEntry.getPath().toString()).isEqualTo("assets/en-gb/text.txt");
  }
}
