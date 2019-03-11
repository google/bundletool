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
import com.android.tools.build.bundletool.commands.DumpCommand.DumpTarget;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
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
public final class DumpCommandTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() {
    bundlePath = temporaryFolder.getRoot().toPath().resolve("bundle.aab");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_defaults() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(new FlagParser().parse("dump", "manifest", "--bundle=" + bundlePath));

    DumpCommand commandViaBuilder =
        DumpCommand.builder().setDumpTarget(DumpTarget.MANIFEST).setBundlePath(bundlePath).build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withModule() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--module=feature"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setModuleName("feature")
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withPrintValues() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser().parse("dump", "manifest", "--bundle=" + bundlePath, "--values"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setPrintValues(true)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_resourceId() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=0x12345678"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceId(0x12345678)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_negativeResourceId() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=0x80200000"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceId(0x80200000)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_resourceName() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser()
                .parse("dump", "manifest", "--bundle=" + bundlePath, "--resource=drawable/icon"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setResourceName("drawable/icon")
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withXPath() {
    DumpCommand commandViaFlags =
        DumpCommand.fromFlags(
            new FlagParser()
                .parse(
                    "dump",
                    "manifest",
                    "--bundle=" + bundlePath,
                    "--xpath=/manifest/@versionCode"));

    DumpCommand commandViaBuilder =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(bundlePath)
            .setXPathExpression("/manifest/@versionCode")
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void dumpFileThatDoesNotExist() {
    DumpCommand command =
        DumpCommand.builder()
            .setDumpTarget(DumpTarget.MANIFEST)
            .setBundlePath(Paths.get("/tmp/random-file"))
            .build();

    assertThrows(IllegalArgumentException.class, () -> command.execute());
  }

  @Test
  public void dumpInvalidTarget() {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () ->
                DumpCommand.fromFlags(
                    new FlagParser().parse("dump", "blah", "--bundle=" + bundlePath)));

    assertThat(exception)
        .hasMessageThat()
        .matches("Unrecognized dump target: 'blah'. Accepted values are: .*");
  }

  @Test
  public void dumpResources_withXPath_throws() throws Exception {
    createBundle(bundlePath);

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.RESOURCES)
            .setXPathExpression("/manifest/@nothing-that-exists")
            .build();
    ValidationException exception =
        assertThrows(ValidationException.class, () -> dumpCommand.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("Cannot pass an XPath expression when dumping resources.");
  }

  @Test
  public void dumpResources_resourceIdAndResourceNameSet_throws() throws Exception {
    createBundle(bundlePath);

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.RESOURCES)
            .setResourceId(0x12345678)
            .setResourceName("drawable/icon")
            .build();
    ValidationException exception =
        assertThrows(ValidationException.class, () -> dumpCommand.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("Cannot pass both resource ID and resource name.");
  }

  @Test
  public void dumpManifest_resourceIdSet_throws() throws Exception {
    createBundle(bundlePath);

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setResourceId(0x12345678)
            .build();
    ValidationException exception =
        assertThrows(ValidationException.class, () -> dumpCommand.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("The --resource flag can only be passed when dumping resources.");
  }

  @Test
  public void dumpResources_moduleSet_throws() throws Exception {
    createBundle(bundlePath);

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.RESOURCES)
            .setModuleName("base")
            .build();
    ValidationException exception =
        assertThrows(ValidationException.class, () -> dumpCommand.execute());
    assertThat(exception).hasMessageThat().contains("The --module flag is unnecessary");
  }

  @Test
  public void dumpManifest_printValues_throws() throws Exception {
    createBundle(bundlePath);

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setPrintValues(true)
            .build();
    ValidationException exception =
        assertThrows(ValidationException.class, () -> dumpCommand.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("The --values flag can only be passed when dumping resources.");
  }

  private static void createBundle(Path bundlePath) throws IOException {
    createBundleWithResourceTable(bundlePath, ResourceTable.getDefaultInstance());
  }

  private static void createBundleWithResourceTable(Path bundlePath, ResourceTable resourceTable)
      throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(androidManifest("com.app")).setResourceTable(resourceTable))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);
  }
}
