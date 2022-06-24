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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class ValidatorRunnerTest {

  private static final byte[] TEST_CONTENT = new byte[1];
  public static final BundleConfig BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;

  @Mock SubValidator validator;
  @Mock SubValidator validator2;

  @Before
  public void setUp() {
    tempFolder = tmp.getRoot().toPath();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void validateBundleZipFile_invokesRightSubValidatorMethods() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addDirectory(ZipPath.create("directory"))
            .addFileWithContent(ZipPath.create("file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      new ValidatorRunner(ImmutableList.of(validator)).validateBundleZipFile(bundleZip);

      ArgumentCaptor<ZipEntry> zipEntryArgs = ArgumentCaptor.forClass(ZipEntry.class);

      verify(validator).validateBundleZipFile(eq(bundleZip));
      verify(validator, atLeastOnce())
          .validateBundleZipEntry(eq(bundleZip), zipEntryArgs.capture());
      verifyNoMoreInteractions(validator);

      assertThat(zipEntryArgs.getAllValues().stream().map(ZipEntry::getName))
          .containsExactly("directory/", "file.txt");
    }
  }

  @Test
  public void validateSdkBundleZipFile_invokesRightSubValidatorMethods() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addDirectory(ZipPath.create("directory"))
            .addFileWithContent(ZipPath.create("file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.asb"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      new ValidatorRunner(ImmutableList.of(validator)).validateBundleZipFile(bundleZip);

      ArgumentCaptor<ZipEntry> zipEntryArgs = ArgumentCaptor.forClass(ZipEntry.class);

      verify(validator).validateBundleZipFile(eq(bundleZip));
      verify(validator, atLeastOnce())
          .validateBundleZipEntry(eq(bundleZip), zipEntryArgs.capture());
      verifyNoMoreInteractions(validator);

      assertThat(zipEntryArgs.getAllValues().stream().map(ZipEntry::getName))
          .containsExactly("directory/", "file.txt");
    }
  }

  @Test
  public void validateBundle_invokesRightSubValidatorMethods() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("BundleConfig.pb"), BUNDLE_CONFIG.toByteArray())
            .addFileWithProtoContent(
                ZipPath.create("moduleX/manifest/AndroidManifest.xml"),
                androidManifest("com.test.app", withSplitId("moduleX")))
            .addFileWithProtoContent(
                ZipPath.create("moduleX/assets.pb"), Assets.getDefaultInstance())
            .addFileWithProtoContent(
                ZipPath.create("moduleX/native.pb"), NativeLibraries.getDefaultInstance())
            .addFileWithProtoContent(
                ZipPath.create("moduleX/resources.pb"), ResourceTable.getDefaultInstance())
            .addFileWithContent(ZipPath.create("moduleX/res/drawable/icon.png"), TEST_CONTENT)
            .addFileWithContent(ZipPath.create("moduleX/lib/x86/libX.so"), TEST_CONTENT)
            .addFileWithProtoContent(
                ZipPath.create("moduleY/manifest/AndroidManifest.xml"),
                androidManifest("com.test.app", withSplitId("moduleY")))
            .addFileWithContent(ZipPath.create("moduleY/assets/file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      AppBundle bundle = AppBundle.buildFromZip(bundleZip);
      ImmutableList<BundleModule> bundleFeatureModules =
          ImmutableList.copyOf(bundle.getFeatureModules().values());

      new ValidatorRunner(ImmutableList.of(validator)).validateBundle(bundle);

      ArgumentCaptor<BundleModule> moduleArgs = ArgumentCaptor.forClass(BundleModule.class);
      ArgumentCaptor<ZipPath> fileArgs = ArgumentCaptor.forClass(ZipPath.class);

      verify(validator).validateBundle(eq(bundle));
      verify(validator).validateAllModules(eq(bundleFeatureModules));
      verify(validator, times(2)).validateModule(moduleArgs.capture());
      verify(validator, atLeastOnce()).validateModuleFile(fileArgs.capture());
      verifyNoMoreInteractions(validator);

      assertThat(moduleArgs.getAllValues())
          .containsExactlyElementsIn(bundle.getFeatureModules().values());
      assertThat(fileArgs.getAllValues().stream().map(ZipPath::toString))
          // Note that special module files (AndroidManifest.xml, assets.pb, native.pb,
          // resources.pb) are NOT passed to the validators.
          .containsExactly("assets/file.txt", "lib/x86/libX.so", "res/drawable/icon.png");
    }
  }

  @Test
  public void validateBundle_invokesSubValidatorsInSequence() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("BundleConfig.pb"), BUNDLE_CONFIG.toByteArray())
            .addFileWithProtoContent(
                ZipPath.create("moduleX/manifest/AndroidManifest.xml"),
                androidManifest("com.test.app", withSplitId("moduleX")))
            .addFileWithContent(ZipPath.create("moduleX/assets/file.txt"), TEST_CONTENT)
            .addFileWithProtoContent(
                ZipPath.create("moduleY/manifest/AndroidManifest.xml"),
                androidManifest("com.test.app", withSplitId("moduleY")))
            .addFileWithContent(ZipPath.create("moduleY/assets/file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      AppBundle bundle = AppBundle.buildFromZip(bundleZip);
      ImmutableList<BundleModule> bundleFeatureModules =
          ImmutableList.copyOf(bundle.getFeatureModules().values());

      new ValidatorRunner(ImmutableList.of(validator, validator2)).validateBundle(bundle);

      InOrder order = Mockito.inOrder(validator, validator2);

      order.verify(validator).validateBundle(eq(bundle));
      order.verify(validator).validateAllModules(eq(bundleFeatureModules));
      order.verify(validator).validateModule(any());
      order.verify(validator, atLeastOnce()).validateModuleFile(any());

      order.verify(validator2).validateBundle(eq(bundle));
      order.verify(validator2).validateAllModules(eq(bundleFeatureModules));
      order.verify(validator2).validateModule(any());
      order.verify(validator2, atLeastOnce()).validateModuleFile(any());

      order.verifyNoMoreInteractions();
    }
  }

  @Test
  public void validateModuleZipFile_invokesRightSubValidatorMethods() throws Exception {
    Path modulePath =
        new ZipBuilder()
            .addDirectory(ZipPath.create("module"))
            .addFileWithContent(ZipPath.create("module/file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("module.zip"));

    try (ZipFile moduleZip = new ZipFile(modulePath.toFile())) {
      new ValidatorRunner(ImmutableList.of(validator)).validateModuleZipFile(moduleZip);

      verify(validator).validateModuleZipFile(eq(moduleZip));
      verifyNoMoreInteractions(validator);
    }
  }
}
