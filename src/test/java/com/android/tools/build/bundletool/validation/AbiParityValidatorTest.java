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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AbiParityValidatorTest {

  @Test
  public void noAbis_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a").setManifest(androidManifest("com.test.app")).build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();

    new AbiParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameAbis_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/mips/lib2.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("lib/x86/lib3.so")
            .addFile("lib/mips/lib4.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new AbiParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameAbisAndNoAbi_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/mips/lib2.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("lib/x86/lib3.so")
            .addFile("lib/mips/lib4.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c").setManifest(androidManifest("com.test.app")).build();

    new AbiParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
  }

  @Test
  public void differentAbis_throws() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/mips/lib2.so")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("lib/x86/lib3.so")
            .addFile("lib/x86_64/lib4.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AbiParityValidator().validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception).hasMessageThat().contains("must support the same set of ABIs");
  }
}
