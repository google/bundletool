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

import com.android.bundle.Config.SplitDimension.Value;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TextureCompressionFormatParityValidatorTest {

  @Test
  public void noTCFs_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a").setManifest(androidManifest("com.test.app")).build();
    BundleModule moduleB =
        new BundleModuleBuilder("b").setManifest(androidManifest("com.test.app")).build();
    AppBundle appBundle = new AppBundleBuilder().addModule(moduleA).addModule(moduleB).build();

    new TextureCompressionFormatParityValidator().validateBundle(appBundle);
    new TextureCompressionFormatParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameTCFsWithProperDefaultSuffix_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(moduleA)
            .addModule(moduleB)
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "astc")
                    .build())
            .build();

    new TextureCompressionFormatParityValidator().validateBundle(appBundle);
    new TextureCompressionFormatParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void tcfsWithNoFallbackAndNoConfig_throws() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(moduleA)
            .addModule(moduleB)
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(Value.TEXTURE_COMPRESSION_FORMAT, /* negate= */ false)
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new TextureCompressionFormatParityValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "When a standalone or universal APK is built, the fallback texture folders (folders"
                + " without #tcf suffixes) will be used, but module 'a' has no such folders."
                + " Instead, it has folder(s) targeted for formats [ASTC, ETC2] (without fallback"
                + " directories). Either add missing folders or change the configuration for the"
                + " TEXTURE_COMPRESSION_FORMAT optimization to specify a default suffix"
                + " corresponding to the format to use in the standalone and universal APKs.");
  }

  @Test
  public void tcfsWithNoMatchingDefaultSuffix_throws() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(moduleA)
            .addModule(moduleB)
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ true,
                        /* defaultSuffix= */ "etc1")
                    .build())
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new TextureCompressionFormatParityValidator().validateBundle(appBundle));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "When a standalone or universal APK is built, the texture folders for format"
                + " 'ETC1_RGB8' will be used, but module 'a' has no such folders. Instead, it has"
                + " folder(s) targeted for formats [ASTC, ETC2] (without fallback directories)."
                + " Either add missing folders or change the configuration for the"
                + " TEXTURE_COMPRESSION_FORMAT optimization to specify a default suffix"
                + " corresponding to the format to use in the standalone and universal APKs.");
  }

  @Test
  public void sameTCFsIncludingFallback_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .addFile("assets/textures/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .addFile("assets/other_textures/fallback_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(moduleA).addModule(moduleB).build();

    new TextureCompressionFormatParityValidator().validateBundle(appBundle);
    new TextureCompressionFormatParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void sameTCFsAndNoTCF_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c")
            .addFile("assets/untargeted_textures/level3.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(moduleA)
            .addModule(moduleB)
            .addModule(moduleC)
            .setBundleConfig(
                BundleConfigBuilder.create()
                    .addSplitDimension(
                        Value.TEXTURE_COMPRESSION_FORMAT,
                        /* negate= */ false,
                        /* stripSuffix= */ false,
                        /* defaultSuffix= */ "astc")
                    .build())
            .build();

    new TextureCompressionFormatParityValidator().validateBundle(appBundle);
    new TextureCompressionFormatParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
  }

  @Test
  public void sameTCFsIncludingFallbackAndNoTCF_ok() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .addFile("assets/textures/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_etc2/etc2_file.assets")
            .addFile("assets/other_textures/fallback_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleC =
        new BundleModuleBuilder("c")
            .addFile("assets/untargeted_textures/level3.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(moduleA).addModule(moduleB).build();

    new TextureCompressionFormatParityValidator()
        .validateAllModules(ImmutableList.of(moduleA, moduleB, moduleC));
    new TextureCompressionFormatParityValidator().validateBundle(appBundle);
  }

  @Test
  public void differentTCFs_throws() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures#tcf_etc2/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .addFile("assets/other_textures#tcf_pvrtc/etc2_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new TextureCompressionFormatParityValidator()
                    .validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with targeted textures must have the same set of texture formats, but"
                + " module 'a' has formats [ASTC, ETC2] (without fallback directories) and module"
                + " 'b' has formats [ASTC, PVRTC] (without fallback directories).");
  }

  @Test
  public void differentFallbacks_throws() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/textures#tcf_astc/level1.assets")
            .addFile("assets/textures/level1.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile("assets/other_textures#tcf_astc/astc_file.assets")
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new TextureCompressionFormatParityValidator()
                    .validateAllModules(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "All modules with targeted textures must have the same set of texture formats, but"
                + " module 'a' has formats [ASTC] (with fallback directories) and module 'b' has"
                + " formats [ASTC] (without fallback directories).");
  }

  @Test
  public void validateAllModules_withNestedTargeting_succeeds() throws Exception {
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/img1#countries_latam#tcf_pvrtc/image.jpg")
            .addFile("assets/img1#countries_latam/image.jpg")
            .addFile("assets/img1#tcf_astc/image.jpg")
            .addFile("assets/img1#tcf_pvrtc/image.jpg")
            .addFile("assets/img1/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .build();

    new TextureCompressionFormatParityValidator().validateAllModules(ImmutableList.of(moduleA));
  }
}
