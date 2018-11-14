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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.commands.DumpCommand.DumpTarget;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

  private static final String LINE_BREAK = System.lineSeparator();

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
  public void dumpManifest() throws Exception {
    XmlNode manifest =
        XmlNode.newBuilder()
            .setElement(XmlElement.newBuilder().setName("manifest").build())
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule("base", module -> module.setManifest(manifest)).build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setModuleName("base")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo("<manifest/>" + LINE_BREAK);
  }

  @Test
  public void dumpManifest_moduleNotBase() throws Exception {
    XmlNode manifestBase =
        XmlNode.newBuilder()
            .setElement(
                XmlElement.newBuilder()
                    .setName("manifest")
                    .addAttribute(XmlAttribute.newBuilder().setName("package").setValue("base")))
            .build();
    XmlNode manifestModule =
        XmlNode.newBuilder()
            .setElement(
                XmlElement.newBuilder()
                    .setName("manifest")
                    .addAttribute(XmlAttribute.newBuilder().setName("package").setValue("module")))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(manifestBase))
            .addModule("module", module -> module.setManifest(manifestModule))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setModuleName("module")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo("<manifest package=\"module\"/>" + LINE_BREAK);
  }

  @Test
  public void dumpManifest_withXPath_singleValue() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression("/manifest/@package")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo("com.app" + LINE_BREAK);
  }

  @Test
  public void dumpManifest_withXPath_multipleValues() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app",
                            withMetadataValue("key1", "value1"),
                            withMetadataValue("key2", "value2"))))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression("/manifest/application/meta-data/@android:value")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo("value1" + LINE_BREAK + "value2" + LINE_BREAK);
  }

  @Test
  public void dumpManifest_withXPath_nodeResult() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app",
                            withMetadataValue("key1", "value1"),
                            withMetadataValue("key2", "value2"))))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/application/meta-data")
            .setOutputStream(new PrintStream(outputStream))
            .build();

    assertThrows(UnsupportedOperationException.class, () -> dumpCommand.execute());
  }

  @Test
  public void dumpManifest_withXPath_predicate() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app",
                            withMetadataValue("key1", "value1"),
                            withMetadataValue("key2", "value2"))))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression(
            "/manifest/application/meta-data[@android:name = \"key2\"]/@android:value")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo("value2" + LINE_BREAK);
  }

  @Test
  public void dumpManifest_withXPath_noMatch() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression("/manifest/@nothing-that-exists")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo(LINE_BREAK);
  }
}
