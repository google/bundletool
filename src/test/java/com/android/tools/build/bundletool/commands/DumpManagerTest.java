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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDebuggableAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.Compression;
import com.android.tools.build.bundletool.commands.DumpCommand.DumpTarget;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableSortedMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DumpManagerTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() {
    bundlePath = temporaryFolder.getRoot().toPath().resolve("bundle.aab");
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

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(String.format("<manifest/>%n"));
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
        .isEqualTo(String.format("<manifest package=\"module\" split=\"module\"/>%n"));
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

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo(String.format("com.app%n"));
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
        .isEqualTo(String.format("value1%n" + "value2%n"));
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

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo(String.format("value2%n"));
  }

  @Test
  public void dumpManifest_withXPath_noMatch() throws Exception {
    createBundle(bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression("/manifest/@nothing-that-exists")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8)).isEqualTo(String.format("%n"));
  }

  @Test
  public void dumpManifest_withXPath_noNamespaceDeclaration() throws Exception {
    XmlNode manifestWithoutNamespaceDeclaration =
        androidManifest(
            "com.app",
            withDebuggableAttribute(true),
            manifestElement -> manifestElement.getProto().clearNamespaceDeclaration());

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(manifestWithoutNamespaceDeclaration))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setXPathExpression("/manifest/application/@android:debuggable")
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8).trim()).isEqualTo("true");
  }

  @Test
  public void dumpResources_allTable() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResourceForMultipleLocales(
                "title", ImmutableSortedMap.of("en", "Title", "pt", "Título"))
            .addDrawableResourceForMultipleDensities(
                "icon",
                ImmutableSortedMap.of(
                    160, "res/drawable/icon.png", 240, "res/drawable-hdpi/icon.png"))
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n"
                    + "0x7f010000 - string/title%n"
                    + "\tlocale: \"en\"%n"
                    + "\tlocale: \"pt\"%n"
                    + "0x7f020000 - drawable/icon%n"
                    + "\tdensity: 160%n"
                    + "\tdensity: 240%n%n"));
  }

  @Test
  public void dumpResources_resourceId() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResourceForMultipleLocales(
                "title", ImmutableSortedMap.of("en", "Title", "pt", "Título"))
            .addDrawableResourceForMultipleDensities(
                "icon",
                ImmutableSortedMap.of(
                    160, "res/drawable/icon.png", 240, "res/drawable-hdpi/icon.png"))
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .setResourceId(0x7f010000)
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n"
                    + "0x7f010000 - string/title%n"
                    + "\tlocale: \"en\"%n"
                    + "\tlocale: \"pt\"%n%n"));
  }

  @Test
  public void dumpResources_resourceName() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResource("icon", "Icon")
            .addMipmapResource("icon", "res/mipmap-hdpi/icon.png")
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .setResourceName("string/icon")
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n" + "0x7f010000 - string/icon%n" + "\t(default)%n%n"));
  }

  @Test
  public void dumpResources_sameResourceNamePresentInMultipleModules() throws Exception {
    ResourceTable baseResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResource("module", "base")
            .build();
    ResourceTable fooResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.app.foo", 0x80)
            .addStringResource("module", "foo")
            .build();

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(androidManifest("com.app"))
                        .setResourceTable(baseResourceTable))
            .addModule(
                "foo",
                module ->
                    module
                        .setManifest(androidManifest("com.app.foo"))
                        .setResourceTable(fooResourceTable))
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .setResourceName("string/module")
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n"
                    + "0x7f010000 - string/module%n"
                    + "\t(default)%n"
                    + "%n"
                    + "Package 'com.app.foo':%n"
                    + "0x80010000 - string/module%n"
                    + "\t(default)%n"
                    + "%n"));
  }

  @Test
  public void printResources_withValues() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResource("title", "Title")
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .setPrintValues(true)
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n"
                    + "0x7f010000 - string/title%n"
                    + "\t(default) - [STR] \"Title\"%n"
                    + "0x7f020000 - drawable/icon%n"
                    + "\t(default) - [FILE] res/drawable/icon.png%n"
                    + "%n"));
  }

  @Test
  public void printResources_valuesEscaped() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResource("text", "First line\nSecond line\nThird line")
            .addStringResource("text2", "First line\r\nSecond line\r\nThird line")
            .addStringResource("text3", "First line\u2028Second line\u2028Third line")
            .addStringResource("text4", "First line\\nSame line!")
            .addStringResource("text5", "Text \"with\" quotes!")
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.RESOURCES)
        .setOutputStream(new PrintStream(outputStream))
        .setPrintValues(true)
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "Package 'com.app':%n"
                    + "0x7f010000 - string/text%n"
                    + "\t(default) - [STR] \"First line\\nSecond line\\nThird line\"%n"
                    + "0x7f010001 - string/text2%n"
                    + "\t(default) - [STR] \"First line\\r\\nSecond line\\r\\nThird line\"%n"
                    + "0x7f010002 - string/text3%n"
                    + "\t(default) - [STR] \"First line\\u2028Second line\\u2028Third line\"%n"
                    + "0x7f010003 - string/text4%n"
                    + "\t(default) - [STR] \"First line\\\\nSame line!\"%n"
                    + "0x7f010004 - string/text5%n"
                    + "\t(default) - [STR] \"Text \\\"with\\\" quotes!\"%n"
                    + "%n"));
  }

  @Test
  public void printBundleConfig() throws Exception {
    createBundle(
        bundlePath,
        BundleConfig.newBuilder()
            .setBundletool(Bundletool.newBuilder().setVersion("1.2.3"))
            .setCompression(Compression.newBuilder().addUncompressedGlob("**.raw"))
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.CONFIG)
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    String output = new String(outputStream.toByteArray(), UTF_8);
    assertThat(output)
        .isEqualTo(
            String.format(
                "{\n"
                    + "  \"bundletool\": {\n"
                    + "    \"version\": \"1.2.3\"\n"
                    + "  },\n"
                    + "  \"compression\": {\n"
                    + "    \"uncompressedGlob\": [\"**.raw\"]\n"
                    + "  }\n"
                    + "}%n"));
  }

  private static void createBundle(Path bundlePath) throws IOException {
    createBundle(bundlePath, ResourceTable.getDefaultInstance());
  }

  private static void createBundle(Path bundlePath, BundleConfig bundleConfig) throws IOException {
    createBundle(bundlePath, ResourceTable.getDefaultInstance(), bundleConfig);
  }

  private static void createBundle(Path bundlePath, ResourceTable resourceTable)
      throws IOException {
    createBundle(bundlePath, resourceTable, BundleConfig.getDefaultInstance());
  }

  private static void createBundle(
      Path bundlePath, ResourceTable resourceTable, BundleConfig bundleConfig) throws IOException {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(androidManifest("com.app")).setResourceTable(resourceTable))
            .setBundleConfig(bundleConfig)
            .build();
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);
  }
}
