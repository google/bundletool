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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.commands.DumpSdkBundleCommand.DumpTarget;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.version.Version;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DumpSdkBundleCommandTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() {
    bundlePath = temporaryFolder.getRoot().toPath().resolve("bundle.asb");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser().parse("dump", "manifest", "--bundle=" + bundlePath));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withPrintValues() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser().parse("dump", "manifest", "--bundle=" + bundlePath, "--values"));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setPrintValues(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_resourceId() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=0x12345678"));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceId(0x12345678)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_negativeResourceId() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=0x80200000"));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceId(0x80200000)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_resourceName() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=drawable/icon"));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceName("drawable/icon")
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withXPath() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser()
                .parse(
                    "dump",
                    "manifest",
                    "--bundle=" + bundlePath,
                    "--xpath=/manifest/@versionCode"));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setXPathExpression("/manifest/@versionCode")
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_bundleConfig() {
    DumpSdkBundleCommand commandViaFlags =
        DumpSdkBundleCommand.fromFlags(
            new FlagParser().parse("dump", "config", "--bundle=" + bundlePath));

    DumpSdkBundleCommand commandViaBuilder =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.CONFIG)
            .setBundlePath(bundlePath)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }
  
  @Test
  public void dumpFileThatDoesNotExist() {
    DumpSdkBundleCommand command =
        DumpSdkBundleCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(Paths.get("/tmp/random-file"))
            .build();

    assertThrows(IllegalArgumentException.class, command::execute);
  }

  @Test
  public void dumpInvalidTarget() {
    InvalidCommandException exception =
        assertThrows(
            InvalidCommandException.class,
            () ->
                DumpSdkBundleCommand.fromFlags(
                    new FlagParser().parse("dump", "blah", "--bundle=" + bundlePath)));

    assertThat(exception)
        .hasMessageThat()
        .matches("Unrecognized dump target: 'blah'. Accepted values are: .*");
  }

  @Test
  public void dumpResources_withXPath_throws() throws Exception {
    createBundle(bundlePath);

    DumpSdkBundleCommand dumpSdkBundleCommand =
        DumpSdkBundleCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.RESOURCES)
            .setXPathExpression("/manifest/@nothing-that-exists")
            .build();
    InvalidCommandException exception =
        assertThrows(InvalidCommandException.class, dumpSdkBundleCommand::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("Cannot pass an XPath expression when dumping resources.");
  }

  @Test
  public void dumpResources_resourceIdAndResourceNameSet_throws() throws Exception {
    createBundle(bundlePath);

    DumpSdkBundleCommand dumpSdkBundleCommand =
        DumpSdkBundleCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.RESOURCES)
            .setResourceId(0x12345678)
            .setResourceName("drawable/icon")
            .build();
    InvalidCommandException exception =
        assertThrows(InvalidCommandException.class, dumpSdkBundleCommand::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("Cannot pass both resource ID and resource name.");
  }

  @Test
  public void dumpManifest_resourceIdSet_throws() throws Exception {
    createBundle(bundlePath);

    DumpSdkBundleCommand dumpSdkBundleCommand =
        DumpSdkBundleCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setResourceId(0x12345678)
            .build();
    InvalidCommandException exception =
        assertThrows(InvalidCommandException.class, dumpSdkBundleCommand::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("The resource name/id can only be passed when dumping resources.");
  }

  @Test
  public void dumpManifest_printValues_throws() throws Exception {
    createBundle(bundlePath);

    DumpSdkBundleCommand dumpSdkBundleCommand =
        DumpSdkBundleCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setPrintValues(true)
            .build();
    InvalidCommandException exception =
        assertThrows(InvalidCommandException.class, dumpSdkBundleCommand::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("Printing resource values can only be requested when dumping resources.");
  }

  private static void createBundle(Path bundlePath) throws IOException {
    createBundleWithResourceTable(bundlePath, ResourceTable.getDefaultInstance());
  }

  private static void createBundleWithResourceTable(Path bundlePath, ResourceTable resourceTable)
      throws IOException {
    SdkBundle sdkBundle =
        SdkBundle.builder()
            .setModule(
                BundleModule.builder()
                    .setName(BundleModuleName.BASE_MODULE_NAME)
                    .setBundleType(BundleType.REGULAR)
                    .setBundletoolVersion(Version.of("1.1.1"))
                    .setAndroidManifestProto(androidManifest("com.app"))
                    .setResourceTable(resourceTable)
                    .build())
            .setSdkModulesConfig(SdkModulesConfig.getDefaultInstance())
            .setSdkBundleConfig(SdkBundleConfig.getDefaultInstance())
            .setBundleMetadata(BundleMetadata.builder().build())
            .build();

    new SdkBundleSerializer().writeToDisk(sdkBundle, bundlePath);
  }
}
