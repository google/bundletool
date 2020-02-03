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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClassDexNameSanitizerTest {

  private static final byte[] DUMMY_CONTENT = new byte[] {0x42};
  private static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  @Test
  public void singleDexFile_doesNotModifyAnything() throws Exception {
    BundleModule module =
        createBasicModule()
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", DUMMY_CONTENT))
            .build();

    assertThat(new ClassesDexNameSanitizer().sanitize(module)).isEqualTo(module);
  }

  @Test
  public void multipleDexFilesNamedProperly_doesNotModifyAnything() throws Exception {
    BundleModule module =
        createBasicModule()
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes3.dex", new byte[] {0x3}))
            .build();

    assertThat(new ClassesDexNameSanitizer().sanitize(module)).isEqualTo(module);
  }

  @Test
  public void multipleDexNamedWithWrongDexFile_renamesCorrectly() throws Exception {
    BundleModule beforeRename =
        createBasicModule()
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes1.dex", new byte[] {0x2}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes2.dex", new byte[] {0x3}))
            .build();

    BundleModule afterRename =
        createBasicModule()
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes.dex", new byte[] {0x1}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes2.dex", new byte[] {0x2}))
            .addEntry(InMemoryModuleEntry.ofFile("dex/classes3.dex", new byte[] {0x3}))
            .build();

    assertThat(new ClassesDexNameSanitizer().sanitize(beforeRename)).isEqualTo(afterRename);
  }

  private static BundleModule.Builder createBasicModule() throws IOException {
    return BundleModule.builder()
        .setName(BundleModuleName.create("module"))
        .setBundleConfig(DEFAULT_BUNDLE_CONFIG)
        .addEntry(InMemoryModuleEntry.ofFile("assets/hello", new byte[] {0xD, 0xE, 0xA, 0xD}))
        .addEntry(InMemoryModuleEntry.ofFile("assets/world", new byte[] {0xB, 0xE, 0xE, 0xF}))
        .setAndroidManifestProto(androidManifest("com.test.app", withSplitId("module")));
  }
}
