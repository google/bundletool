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
package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.SplitDimension;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BundleConfigValidatorTest {

  private static final String CURRENT_VERSION = BundleToolVersion.getCurrentVersion().toString();

  @Test
  public void compression_validGlobs() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearCompression()
                .addUncompressedGlob("res/raw/**")
                .addUncompressedGlob("**/*.uncompressed"));

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void compression_invalidGlob_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create().clearCompression().addUncompressedGlob("res/raw\\"));

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(e).hasMessageThat().contains("Invalid uncompressed glob: 'res/raw\\'.");
  }

  @Test
  public void compression_forbiddenCharactersInGlob_backslash_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearCompression()
                .addUncompressedGlob("res\\\\raw\\\\**"));

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(e).hasMessageThat().contains("Invalid uncompressed glob: 'res\\\\raw\\\\**'.");
  }

  @Test
  public void compression_forbiddenCharactersInGlob_carriageReturn_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearCompression()
                .addUncompressedGlob("res/raw/**\nassets/raw/**"));

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(e)
        .hasMessageThat()
        .contains("Invalid uncompressed glob: 'res/raw/**\nassets/raw/**'.");
  }

  @Test
  public void optimizations_uniqueSplitDimensions_ok() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearOptimizations()
                .addSplitDimension(SplitDimension.Value.ABI)
                .addSplitDimension(SplitDimension.Value.SCREEN_DENSITY));

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void optimizations_duplicateSplitDimensions_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearOptimizations()
                .addSplitDimension(SplitDimension.Value.ABI)
                .addSplitDimension(SplitDimension.Value.ABI));

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(exception).hasMessageThat().contains("duplicate split dimensions:");
  }

  @Test
  public void optimizations_unrecognizedDimensionsNegated_ok() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearOptimizations()
                .addSplitDimension(
                    SplitDimension.newBuilder().setValueValue(1234).setNegate(true).build())
                .addSplitDimension(
                    SplitDimension.newBuilder().setValueValue(5678).setNegate(true).build()));

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void optimizations_unrecognizedDimensionsEnabled_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearOptimizations()
                .addSplitDimension(SplitDimension.newBuilder().setValueValue(1234).build()));

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(exception)
        .hasMessageThat()
        .contains("BundleConfig.pb contains an unrecognized split dimension.");
  }

  @Test
  public void optimizations_duplicateSplitDimensions_positiveAndNegative_throws() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            BundleConfigBuilder.create()
                .clearOptimizations()
                .addSplitDimension(SplitDimension.Value.ABI, /* negate= */ false)
                .addSplitDimension(SplitDimension.Value.ABI, /* negate= */ true));

    ValidationException exception =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(exception).hasMessageThat().contains("duplicate split dimensions");
  }

  @Test
  public void version_valid_ok() throws Exception {
    AppBundle appBundle = createAppBundle(BundleConfigBuilder.create().setVersion(CURRENT_VERSION));

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void version_missing_ok() throws Exception {
    AppBundle appBundle = createAppBundle(BundleConfigBuilder.create().clearVersion());

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void version_invalid_throws() throws Exception {
    AppBundle appBundle = createAppBundle(BundleConfigBuilder.create().setVersion("blah"));

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(e).hasMessageThat().contains("Invalid version");
  }

  @Test
  public void masterResources_valid_ok() throws Exception {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResource("label", "Hello World")
            .build();

    int resourceId =
        ResourcesUtils.entries(resourceTable).findFirst().get().getResourceId().getFullResourceId();

    AppBundle appBundle =
        createAppBundleBuilder(
                BundleConfigBuilder.create().addResourcePinnedToMasterSplit(resourceId))
            .addModule(
                "feature",
                module ->
                    module
                        .setResourceTable(resourceTable)
                        .setManifest(androidManifest("com.test.app")))
            .build();

    new BundleConfigValidator().validateBundle(appBundle);
  }

  @Test
  public void masterResources_undefinedResourceId_throws() throws Exception {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addStringResource("label", "Hello World")
            .build();

    int nonExistentResourceId =
        ResourcesUtils.entries(resourceTable).findFirst().get().getResourceId().getFullResourceId()
            + 1;

    AppBundle appBundle =
        createAppBundleBuilder(
                BundleConfigBuilder.create().addResourcePinnedToMasterSplit(nonExistentResourceId))
            .addModule(
                "feature",
                module ->
                    module
                        .setResourceTable(resourceTable)
                        .setManifest(androidManifest("com.test.app")))
            .build();

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new BundleConfigValidator().validateBundle(appBundle));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The Master Resources list contains resource IDs not defined in any module. "
                + "For example: 0x7f010001");
  }

  private static AppBundle createAppBundle(BundleConfigBuilder bundleConfig) throws IOException {
    return createAppBundle(bundleConfig.build());
  }

  private static AppBundle createAppBundle(BundleConfig bundleConfig) throws IOException {
    return createAppBundleBuilder(bundleConfig).build();
  }

  private static AppBundleBuilder createAppBundleBuilder(BundleConfigBuilder bundleConfig)
      throws IOException {
    return createAppBundleBuilder(bundleConfig.build());
  }

  private static AppBundleBuilder createAppBundleBuilder(BundleConfig bundleConfig)
      throws IOException {
    return new AppBundleBuilder()
        .addModule("base", module -> module.setManifest(androidManifest("com.app")))
        .setBundleConfig(bundleConfig);
  }
}
