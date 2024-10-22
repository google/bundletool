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
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.commands.DumpSdkBundleCommand.DumpTarget;
import com.android.tools.build.bundletool.io.SdkBundleSerializer;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.version.Version;
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
public final class DumpSdkBundleManagerTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() {
    bundlePath = temporaryFolder.getRoot().toPath().resolve("bundle.asb");
  }

  @Test
  public void dumpManifest() throws Exception {
    XmlNode manifest =
        XmlNode.newBuilder()
            .setElement(XmlElement.newBuilder().setName("manifest").build())
            .build();
    createBundle(bundlePath, manifest);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
        .setBundlePath(bundlePath)
        .setDumpTarget(DumpTarget.MANIFEST)
        .setOutputStream(new PrintStream(outputStream))
        .build()
        .execute();

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(String.format("<manifest/>%n"));
  }

  @Test
  public void dumpManifest_withXPath_singleValue() throws Exception {
    createBundle(bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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
    createBundle(
        bundlePath,
        androidManifest(
            "com.app", withMetadataValue("key1", "value1"), withMetadataValue("key2", "value2")));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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
    createBundle(
        bundlePath,
        androidManifest(
            "com.app", withMetadataValue("key1", "value1"), withMetadataValue("key2", "value2")));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand dumpCommand =
        DumpSdkBundleCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/application/meta-data")
            .setOutputStream(new PrintStream(outputStream))
            .build();

    assertThrows(UnsupportedOperationException.class, () -> dumpCommand.execute());
  }

  @Test
  public void dumpManifest_withXPath_predicate() throws Exception {
    createBundle(
        bundlePath,
        androidManifest(
            "com.app", withMetadataValue("key1", "value1"), withMetadataValue("key2", "value2")));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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

    DumpSdkBundleCommand.builder()
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

    createBundle(bundlePath, manifestWithoutNamespaceDeclaration);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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

    DumpSdkBundleCommand.builder()
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

    DumpSdkBundleCommand.builder()
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

    DumpSdkBundleCommand.builder()
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
  public void printResources_withValues() throws Exception {
    createBundle(
        bundlePath,
        new ResourceTableBuilder()
            .addPackage("com.app")
            .addStringResource("title", "Title")
            .addDrawableResource("icon", "res/drawable/icon.png")
            .build());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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

    DumpSdkBundleCommand.builder()
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
    createBundle(bundlePath);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    DumpSdkBundleCommand.builder()
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
                    + "  \"sdkPackageName\": \"com.sdk\",\n"
                    + "  \"sdkVersion\": {\n"
                    + "    \"major\": 1,\n"
                    + "    \"minor\": 2,\n"
                    + "    \"patch\": 3\n"
                    + "  },\n"
                    + "  \"sdkProviderClassName\": \"com.sdk.SandboxedSdkProviderAdapter\",\n"
                    + "  \"compatSdkProviderClassName\": \"com.sdk.SdkProvider\"\n"
                    + "}%n"));
  }

  private static void createBundle(Path bundlePath) throws IOException {
    createBundle(bundlePath, ResourceTable.getDefaultInstance());
  }

  private static void createBundle(Path bundlePath, ResourceTable resourceTable)
      throws IOException {
    createBundle(bundlePath, resourceTable, androidManifest("com.app"));
  }

  private static void createBundle(Path bundlePath, XmlNode manifest) throws IOException {
    createBundle(bundlePath, ResourceTable.getDefaultInstance(), manifest);
  }

  private static void createBundle(Path bundlePath, ResourceTable resourceTable, XmlNode manifest)
      throws IOException {
    SdkBundle sdkBundle =
        SdkBundle.builder()
            .setModule(
                BundleModule.builder()
                    .setName(BundleModuleName.BASE_MODULE_NAME)
                    .setBundleType(BundleType.REGULAR)
                    .setBundletoolVersion(Version.of("1.1.1"))
                    .setAndroidManifestProto(manifest)
                    .setResourceTable(resourceTable)
                    .build())
            .setSdkModulesConfig(
                SdkModulesConfig.newBuilder()
                    .setBundletool(Bundletool.newBuilder().setVersion("1.2.3"))
                    .setSdkPackageName("com.sdk")
                    .setSdkVersion(
                        RuntimeEnabledSdkVersion.newBuilder()
                            .setMajor(1)
                            .setMinor(2)
                            .setPatch(3)
                            .build())
                    .setSdkProviderClassName("com.sdk.SandboxedSdkProviderAdapter")
                    .setCompatSdkProviderClassName("com.sdk.SdkProvider")
                    .build())
            .setSdkBundleConfig(SdkBundleConfig.getDefaultInstance())
            .setBundleMetadata(BundleMetadata.builder().build())
            .build();

    new SdkBundleSerializer().writeToDisk(sdkBundle, bundlePath);
  }
}
