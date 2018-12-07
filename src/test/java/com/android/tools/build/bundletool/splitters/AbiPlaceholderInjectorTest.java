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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AbiPlaceholderInjectorTest {

  @Test
  public void addPlaceholderNativeEntries_newEntriesAdded() throws Exception {
    ModuleSplit moduleSplit =
        ModuleSplit.forModule(
            new BundleModuleBuilder("base").setManifest(androidManifest("com.test")).build());

    AbiPlaceholderInjector abiPlaceholderInjector =
        new AbiPlaceholderInjector(ImmutableSet.of(toAbi(AbiAlias.ARMEABI_V7A)));

    ModuleSplit actualModuleSplit = abiPlaceholderInjector.addPlaceholderNativeEntries(moduleSplit);

    assertThat(extractPaths(actualModuleSplit.getEntries()))
        .contains("lib/armeabi-v7a/libplaceholder.so");
  }

  @Test
  public void addPlaceholderNativeEntries_noAppAbis_noEntriesAdded() throws Exception {
    ModuleSplit moduleSplit =
        ModuleSplit.forModule(
            new BundleModuleBuilder("base").setManifest(androidManifest("com.test")).build());

    AbiPlaceholderInjector abiPlaceholderInjector = new AbiPlaceholderInjector(ImmutableSet.of());

    ModuleSplit actualModuleSplit = abiPlaceholderInjector.addPlaceholderNativeEntries(moduleSplit);

    assertThat(actualModuleSplit).isEqualTo(moduleSplit);
  }
}
