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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.TestUtils.createModuleEntryForFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClassesDexSanitizerTest {
  private static final byte[] TEST_CONTENT = new byte[] {0x42};
  private static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  @Test
  public void classesDexNameSanitizer_singleDexFile_doesNotModifyAnything() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", TEST_CONTENT))
            .build();

    assertThat(new ClassesDexSanitizer().applyMutation(module)).isEqualTo(module);
  }

  @Test
  public void classesDexNameSanitizer_multipleDexFilesNamedProperly_doesNotModifyAnything() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes3.dex", new byte[] {0x3}))
            .build();

    assertThat(new ClassesDexSanitizer().applyMutation(module)).isEqualTo(module);
  }

  @Test
  public void classesDexNameSanitizer_multipleDexNamedWithWrongDexFile_renamesCorrectly() {
    BundleModule beforeRename =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes1.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x3}))
            .build();

    BundleModule afterRename =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes3.dex", new byte[] {0x3}))
            .build();

    assertThat(new ClassesDexSanitizer().applyMutation(beforeRename)).isEqualTo(afterRename);
  }

  private static BundleModule.Builder createBasicModule() {
    return BundleModule.builder()
        .setName(BundleModuleName.create("module"))
        .setBundleType(DEFAULT_BUNDLE_CONFIG.getType())
        .setBundletoolVersion(Version.of(DEFAULT_BUNDLE_CONFIG.getBundletool().getVersion()))
        .addEntry(createModuleEntryForFile("assets/hello", new byte[] {0xD, 0xE, 0xA, 0xD}))
        .addEntry(createModuleEntryForFile("assets/world", new byte[] {0xB, 0xE, 0xE, 0xF}))
        .setAndroidManifestProto(androidManifest("com.test.app", withSplitId("module")));
  }
}
