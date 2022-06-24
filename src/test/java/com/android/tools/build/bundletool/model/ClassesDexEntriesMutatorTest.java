/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.ClassesDexEntriesMutator.CLASSES_DEX_NAME_SANITIZER;
import static com.android.tools.build.bundletool.model.ClassesDexEntriesMutator.R_PACKAGE_DEX_ENTRY_REMOVER;
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
public final class ClassesDexEntriesMutatorTest {

  private static final byte[] TEST_CONTENT = new byte[] {0x42};
  private static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  @Test
  public void classesDexNameSanitizer_singleDexFile_doesNotModifyAnything() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", TEST_CONTENT))
            .build();

    assertThat(new ClassesDexEntriesMutator().applyMutation(module, CLASSES_DEX_NAME_SANITIZER))
        .isEqualTo(module);
  }

  @Test
  public void classesDexNameSanitizer_multipleDexFilesNamedProperly_doesNotModifyAnything() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes3.dex", new byte[] {0x3}))
            .build();

    assertThat(new ClassesDexEntriesMutator().applyMutation(module, CLASSES_DEX_NAME_SANITIZER))
        .isEqualTo(module);
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

    assertThat(
            new ClassesDexEntriesMutator().applyMutation(beforeRename, CLASSES_DEX_NAME_SANITIZER))
        .isEqualTo(afterRename);
  }

  @Test
  public void rPackageDexEntryRemover_removesLastDexFile() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes3.dex", new byte[] {0x3}))
            .addEntry(createModuleEntryForFile("dex/classes4.dex", new byte[] {0x4}))
            .addEntry(createModuleEntryForFile("dex/classes5.dex", new byte[] {0x5}))
            .addEntry(createModuleEntryForFile("dex/classes6.dex", new byte[] {0x6}))
            .addEntry(createModuleEntryForFile("dex/classes7.dex", new byte[] {0x7}))
            .addEntry(createModuleEntryForFile("dex/classes8.dex", new byte[] {0x8}))
            .addEntry(createModuleEntryForFile("dex/classes9.dex", new byte[] {0x9}))
            .addEntry(createModuleEntryForFile("dex/classes10.dex", new byte[] {0x10}))
            .build();

    BundleModule afterRemoval =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(createModuleEntryForFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(createModuleEntryForFile("dex/classes3.dex", new byte[] {0x3}))
            .addEntry(createModuleEntryForFile("dex/classes4.dex", new byte[] {0x4}))
            .addEntry(createModuleEntryForFile("dex/classes5.dex", new byte[] {0x5}))
            .addEntry(createModuleEntryForFile("dex/classes6.dex", new byte[] {0x6}))
            .addEntry(createModuleEntryForFile("dex/classes7.dex", new byte[] {0x7}))
            .addEntry(createModuleEntryForFile("dex/classes8.dex", new byte[] {0x8}))
            .addEntry(createModuleEntryForFile("dex/classes9.dex", new byte[] {0x9}))
            .build();

    assertThat(new ClassesDexEntriesMutator().applyMutation(module, R_PACKAGE_DEX_ENTRY_REMOVER))
        .isEqualTo(afterRemoval);
  }

  @Test
  public void rPackageDexEntryRemover_singleDexFile_removes() {
    BundleModule module =
        createBasicModule()
            .addEntry(createModuleEntryForFile("dex/classes.dex", new byte[] {0x1}))
            .build();

    BundleModule afterRemoval = createBasicModule().build();

    assertThat(new ClassesDexEntriesMutator().applyMutation(module, R_PACKAGE_DEX_ENTRY_REMOVER))
        .isEqualTo(afterRemoval);
  }

  @Test
  public void rPackageDexEntryRemover_noDexFiles_noChange() {
    BundleModule module = createBasicModule().build();

    assertThat(new ClassesDexEntriesMutator().applyMutation(module, R_PACKAGE_DEX_ENTRY_REMOVER))
        .isEqualTo(module);
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
