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

import static com.android.tools.build.bundletool.model.ManifestMutator.withExtractNativeLibs;
import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ManifestMutator}. */
@RunWith(JUnit4.class)
public class ManifestMutatorTest {

  @Test
  public void setExtractNativeLibsValue() throws Exception {
    AndroidManifest manifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(manifest.getExtractNativeLibsValue()).isEmpty();

    AndroidManifest editedManifest =
        manifest.applyMutators(ImmutableList.of(withExtractNativeLibs(false)));
    assertThat(editedManifest.getExtractNativeLibsValue()).hasValue(false);

    editedManifest = editedManifest.applyMutators(ImmutableList.of(withExtractNativeLibs(true)));
    assertThat(editedManifest.getExtractNativeLibsValue()).hasValue(true);
  }

  @Test
  public void setSplitsRequiredValue() throws Exception {
    AndroidManifest manifest = AndroidManifest.create(androidManifest("com.test.app"));
    assertThat(manifest.getSplitsRequiredValue()).isEmpty();

    AndroidManifest editedManifest =
        manifest.applyMutators(ImmutableList.of(withSplitsRequired(false)));
    assertThat(editedManifest.getSplitsRequiredValue()).hasValue(false);

    editedManifest = editedManifest.applyMutators(ImmutableList.of(withSplitsRequired(true)));
    assertThat(editedManifest.getSplitsRequiredValue()).hasValue(true);
  }
}
