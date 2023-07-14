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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDwfProperty;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesFeatureElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeclarativeWatchFaceBundleValidatorTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private static final String DWF_BUNDLE_PACKAGE = "com.sample.dwf";
  private static final int MIN_DWF_SDK_VERSION = 33;
  private static final int EMBEDDED_RUNTIME_SDK_VERSION = 30;

  @Test
  public void validNonDwf() {
    AppBundle nonDwfBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.example.nondwf")))
            .build();
    new DeclarativeWatchFaceBundleValidator().validateBundle(nonDwfBundle);
  }

  @Test
  public void validNonDwfWithoutBaseModule() {
    AppBundle nonDwfBundle =
        new AppBundleBuilder()
            .addModule(
                "secondary_module",
                module -> module.setManifest(androidManifest("com.example.nondwf")))
            .build();
    new DeclarativeWatchFaceBundleValidator().validateBundle(nonDwfBundle);
  }

  @Test
  public void validSimpleDwf() {
    AppBundle appBundle = new AppBundleBuilder().addModule(createBaseModule()).build();

    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void validDwfWithEmbeddedRuntime() {
    AppBundle appBundle = createAppBundleWithRuntime();
    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void validDwfAabWithEmbeddedRuntime() throws Exception {
    Path wfAabPath = TestData.copyToTempDir(tmp, "testdata/bundle/watch-face-from-tool.aab");
    AppBundle appBundle = AppBundle.buildFromZip(new ZipFile(wfAabPath.toFile()));
    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void invalidDwf_notTargetingWatches() {
    AppBundle appBundle =
        new AppBundleBuilder()
            // create dwf manifest without the uses-feature definition
            .addModule(createBaseModule(createDwfManifest()))
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "AndroidManifest.xml must contain a <uses-feature"
                + " android:name=\"android.hardware.type.watch\" /> declaration.");
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_multipleModules() {
    AppBundle appBundle =
        createAppBundleWithRuntime().toBuilder()
            .addRawModule(
                new BundleModuleBuilder("extra_module")
                    .setManifest(androidManifest(DWF_BUNDLE_PACKAGE))
                    .build())
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e).hasMessageThat().contains("can have at most two modules");
  }

  private InvalidBundleException assertThrowsForBundle(AppBundle appBundle) {
    return assertThrows(
        InvalidBundleException.class,
        () -> new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle));
  }

  @Test
  public void invalidSimpleDwf_missingLayoutDefinitions() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                (module) ->
                    module.setManifest(
                        createDwfManifest(
                            withUsesFeatureElement(
                                AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME))))
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "must contain an xml watch_face_shapes resource or a default "
                + "/res/raw/watchface.xml layout");
  }

  @Test
  public void validSimpleDwf_defaultLayoutWithoutShapes() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                (module) ->
                    module
                        .setManifest(
                            createDwfManifest(
                                withUsesFeatureElement(
                                    AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                        .addFile("/res/raw/watchface.xml"))
            .build();
    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void validSimpleDwf_hasXmlShapesDefinition() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                (module) ->
                    module
                        .setManifest(
                            createDwfManifest(
                                withUsesFeatureElement(
                                    AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                        .addFile("/res/xml/watch_face_shapes.xml"))
            .build();

    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void validSimpleDwf_hasQualifiedShapesDefinitions() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                (module) ->
                    module
                        .setManifest(
                            createDwfManifest(
                                withUsesFeatureElement(
                                    AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                        .addFile("/res/xml-notround/watch_face_shapes.xml")
                        .addFile("/res/xml-round/watch_face_shapes.xml"))
            .build();
    new DeclarativeWatchFaceBundleValidator().validateBundle(appBundle);
  }

  @Test
  public void invalidSimpleDwf_exposesComponents() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                createBaseModule(
                    createDwfManifest(
                        withMainActivity("LauncherActivity"),
                        withUsesFeatureElement(AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME))))
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains("cannot have any components and can only have resources");
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_moduleIsAlwaysInstalled() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(createBaseModule())
            .addModule(createAlwaysInstalledEmbeddedRuntimeModule())
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains("Module embedded_runtime must be conditionally installed");
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_moduleIsConditionallyInstalledOnApi33() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(createBaseModule())
            .addModule(createEmbeddedRuntimeModuleBase(withMaxSdkCondition(MIN_DWF_SDK_VERSION)))
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains("embedded runtime must not install the runtime on devices with API level >= 33");
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_minSdkIsNot30() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(createBaseModule())
            .addModule(
                createEmbeddedRuntimeModuleBase(withMaxSdkCondition(MIN_DWF_SDK_VERSION - 1)))
            .build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e).hasMessageThat().contains("Watch face with embedded runtime must have minSdk 30");
  }

  @Test
  public void invalidSimpleDwf_minSdkIsBelow33() {
    AppBundle appBundle =
        new AppBundleBuilder().addModule(createBaseModule(MIN_DWF_SDK_VERSION - 1)).build();
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e).hasMessageThat().contains("without embedded runtime must have minSdk >= 33");
  }

  @Test
  public void invalidSimpleDwf_hasDexInBase() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        createDwfManifest(
                            withUsesFeatureElement(
                                AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                    .addFile("/res/raw/watchface.xml")
                    .addFile("/dex/classes.dex")
                    .build())
            .build();

    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e).hasMessageThat().contains("cannot have dex files");
  }

  @Test
  public void invalidSimpleDwf_hasLibsInBase() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(
                        createDwfManifest(
                            withUsesFeatureElement(
                                AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                    .addFile("/res/raw/watchface.xml")
                    .addFile("/lib/sample.so")
                    .build())
            .build();

    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e).hasMessageThat().contains("cannot have any external libraries");
  }

  @Test
  public void invalidSimpleDwf_hasCodeFilesInBase() {
    List<String> codeFileNames = Arrays.asList("classes.dex", "libs.so");

    codeFileNames.forEach(
        fileName -> {
          AppBundle appBundle =
              new AppBundleBuilder()
                  .addModule(
                      new BundleModuleBuilder("base")
                          .setManifest(
                              createDwfManifest(
                                  withUsesFeatureElement(
                                      AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
                          .addFile("/res/raw/watchface.xml")
                          .addFile("/root/" + fileName)
                          .build())
                  .build();

          InvalidBundleException e = assertThrowsForBundle(appBundle);
          assertThat(e)
              .hasMessageThat()
              .contains("cannot have any compiled code in its root folder");
        });
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_unexpectedDexFile() {
    AppBundle appBundle =
        createAppBundleWithRuntime(new SimpleEntry<>("/dex/classes.dex", new byte[1]));
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains("runtime must have a single, expected dex file in its base module");
  }

  @Test
  public void invalidDwfWithEmbeddedRuntime_multipleDexFiles() {
    AppBundle appBundle =
        createAppBundleWithRuntime(
            new SimpleEntry<>("/dex/classes.dex", new byte[1]),
            new SimpleEntry<>("/dex/classes2.dex", new byte[1]));
    InvalidBundleException e = assertThrowsForBundle(appBundle);
    assertThat(e)
        .hasMessageThat()
        .contains("runtime must have a single, expected dex file in its base module");
  }

  private static BundleModule createBaseModule() {
    return createBaseModule(MIN_DWF_SDK_VERSION);
  }

  private static BundleModule createBaseModule(int minSdk) {
    return createBaseModule(
        createDwfManifest(
            minSdk, withUsesFeatureElement(AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)));
  }

  private static BundleModule createBaseModule(Resources.XmlNode androidManifest) {
    return new BundleModuleBuilder("base")
        .setManifest(androidManifest)
        .addFile("/res/raw/watchface.xml")
        .build();
  }

  private static Resources.XmlNode createDwfManifest() {
    return createDwfManifest(MIN_DWF_SDK_VERSION, (manifest) -> {});
  }

  private static Resources.XmlNode createDwfManifest(ManifestMutator... extraManifestMutator) {
    return createDwfManifest(MIN_DWF_SDK_VERSION, extraManifestMutator);
  }

  private static Resources.XmlNode createDwfManifest(
      int minSdk, ManifestMutator... extraManifestMutators) {

    return androidManifest(
        DWF_BUNDLE_PACKAGE,
        Stream.concat(
                Stream.of(withMinSdkVersion(minSdk), withDwfProperty("0.0.1")),
                Arrays.stream(extraManifestMutators))
            .toArray(ManifestMutator[]::new));
  }

  private static BundleModule createAlwaysInstalledEmbeddedRuntimeModule() {
    return createEmbeddedRuntimeModuleBase((module) -> {});
  }

  private static BundleModule createEmbeddedRuntimeModuleBase(
      ManifestMutator extraManifestMutator) {
    return new BundleModuleBuilder("embedded_runtime")
        .setManifest(
            androidManifest(DWF_BUNDLE_PACKAGE, withInstallTimeDelivery(), extraManifestMutator))
        .addFile("/dex/classes.dex")
        .build();
  }

  private static AppBundle createAppBundleWithRuntime() {
    return createAppBundleWithRuntime(
        new SimpleEntry<>("/dex/classes.dex", TestData.readBytes("testdata/dex/classes-dwf.dex")));
  }

  private static AppBundle createAppBundleWithRuntime(
      Map.Entry<String, byte[]>... baseFilePathAndContent) {
    BundleModuleBuilder baseModuleBuilder =
        new BundleModuleBuilder("base")
            .setManifest(
                createDwfManifest(
                    EMBEDDED_RUNTIME_SDK_VERSION,
                    withUsesFeatureElement(AndroidManifest.USES_FEATURE_HARDWARE_WATCH_NAME)))
            .addFile("/res/raw/watchface.xml");
    for (Map.Entry<String, byte[]> fileAndTestData : baseFilePathAndContent) {
      baseModuleBuilder.addFile(fileAndTestData.getKey(), fileAndTestData.getValue());
    }
    return new AppBundleBuilder()
        .addModule(baseModuleBuilder.build())
        .addModule(createEmbeddedRuntimeModuleBase(withMaxSdkCondition(MIN_DWF_SDK_VERSION - 1)))
        .build();
  }
}
