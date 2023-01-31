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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.model.SdkAsar.SDK_METADATA_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_BUNDLE_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_INTERFACE_DESCRIPTORS_FILE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallLocation;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.DEFAULT_SDK_MODULES_CONFIG;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.protobuf.ExtensionRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.function.Executable;

/** Some misc utility methods for tests. */
public final class TestUtils {

  private static final byte[] TEST_CONTENT = new byte[1];
  private static final String PACKAGE_NAME = "com.test.sdk.detail";
  private static final XmlNode MANIFEST = createSdkAndroidManifest();
  public static final SdkMetadata DEFAULT_SDK_METADATA = getSdkMetadata();

  /** Tests that missing mandatory property is detected by an AutoValue.Builder. */
  public static void expectMissingRequiredBuilderPropertyException(
      String property, Executable runnable) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, runnable);

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Missing required properties: %s", property));
  }

  /** Tests that missing mandatory command line argument is detected. */
  public static void expectMissingRequiredFlagException(String flag, Executable runnable) {
    RequiredFlagNotSetException exception =
        assertThrows(RequiredFlagNotSetException.class, runnable);

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Missing the required --%s flag", flag));
  }

  /**
   * Returns paths of the given {@link com.android.tools.build.bundletool.model.ModuleEntry}
   * instances, preserving the order.
   */
  public static ImmutableList<String> extractPaths(ImmutableList<ModuleEntry> entries) {
    return extractPaths(entries.stream());
  }

  /**
   * Returns paths of the given {@link com.android.tools.build.bundletool.model.ModuleEntry}
   * instances, preserving the order.
   */
  public static ImmutableList<String> extractPaths(Stream<ModuleEntry> entries) {
    return entries.map(ModuleEntry::getPath).map(ZipPath::toString).collect(toImmutableList());
  }

  /** Extracts paths of all files having the given path prefix. */
  public static ImmutableList<String> filesUnderPath(ZipFile zipFile, ZipPath pathPrefix) {
    return zipFile.stream()
        .map(ZipEntry::getName)
        .filter(entryName -> ZipPath.create(entryName).startsWith(pathPrefix))
        .collect(toImmutableList());
  }

  public static ModuleEntry createModuleEntryForFile(String filePath, byte[] content) {
    return createModuleEntryForFile(filePath, content, /* uncompressed= */ false);
  }

  public static ModuleEntry createModuleEntryForFile(
      String filePath, byte[] content, boolean uncompressed) {
    return ModuleEntry.builder()
        .setPath(ZipPath.create(filePath))
        .setContent(ByteSource.wrap(content))
        .setForceUncompressed(uncompressed)
        .build();
  }

  /** Extracts AndroidManifest from binary file in APK. */
  public static AndroidManifest extractAndroidManifest(File apk, Path tmpDir) {
    Path protoApkPath = tmpDir.resolve("proto.apk");
    Aapt2Helper.convertBinaryApkToProtoApk(apk.toPath(), protoApkPath);
    try {
      try (ZipFile protoApk = new ZipFile(protoApkPath.toFile())) {
        return AndroidManifest.create(
            XmlNode.parseFrom(
                protoApk.getInputStream(protoApk.getEntry("AndroidManifest.xml")),
                ExtensionRegistry.getEmptyRegistry()));
      } finally {
        Files.deleteIfExists(protoApkPath);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Creates a Java Keystore. */
  public static void createKeystore(Path keystorePath, String keystorePassword)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, keystorePassword.toCharArray());
    try (FileOutputStream keystoreOutputStream = new FileOutputStream(keystorePath.toFile())) {
      keystore.store(keystoreOutputStream, keystorePassword.toCharArray());
    }
  }

  /** Adds a key to a Java Keystore. */
  public static void addKeyToKeystore(
      Path keystorePath,
      String keystorePassword,
      String keyAlias,
      String keyPassword,
      PrivateKey privateKey,
      X509Certificate certificate)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = KeyStore.getInstance("JKS");
    try (FileInputStream keystoreInputStream = new FileInputStream(keystorePath.toFile())) {
      keystore.load(keystoreInputStream, keystorePassword.toCharArray());
    }
    keystore.setKeyEntry(
        keyAlias, privateKey, keyPassword.toCharArray(), new Certificate[] {certificate});
    try (FileOutputStream keystoreOutputStream = new FileOutputStream(keystorePath.toFile())) {
      keystore.store(keystoreOutputStream, keystorePassword.toCharArray());
    }
  }

  /** Creates a Debug Keystore. */
  public static SigningConfiguration createDebugKeystore(
      Path path, String keystorePassword, String keyAlias, String keyPassword) throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    X509Certificate certificate =
        CertificateFactory.buildSelfSignedCertificate(keyPair, "CN=Android Debug,O=Android,C=US");
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(/* stream= */ null, keystorePassword.toCharArray());
    keystore.setKeyEntry(
        keyAlias, privateKey, keyPassword.toCharArray(), new Certificate[] {certificate});
    try (FileOutputStream keystoreOutputStream = new FileOutputStream(path.toFile())) {
      keystore.store(keystoreOutputStream, keystorePassword.toCharArray());
    }
    return SigningConfiguration.builder().setSignerConfig(privateKey, certificate).build();
  }

  public static ZipBuilder createZipBuilderForSdkBundleWithModules(
      ZipBuilder modules, Path modulesPath) throws IOException {
    modules.writeTo(modulesPath);
    return createZipBuilderForSdkBundle()
        .addFileFromDisk(ZipPath.create("modules.resm"), modulesPath.toFile());
  }

  public static ZipBuilder createZipBuilderForSdkAsarWithModules(
      ZipBuilder modules, Path modulesPath) throws IOException {
    return createZipBuilderForSdkAsarWithModules(modules, DEFAULT_SDK_METADATA, modulesPath);
  }

  public static ZipBuilder createZipBuilderForSdkAsarWithModules(
      ZipBuilder modules, SdkMetadata sdkMetadata, Path modulesPath) throws IOException {
    modules.writeTo(modulesPath);
    return createZipBuilderForSdkAsar(sdkMetadata)
        .addFileFromDisk(ZipPath.create("modules.resm"), modulesPath.toFile());
  }

  public static ZipBuilder createZipBuilderForSdkBundle() {
    return createZipBuilderForSdkBundle(SdkBundleConfig.getDefaultInstance());
  }

  public static ZipBuilder createZipBuilderForSdkBundle(SdkBundleConfig sdkBundleConfig) {
    return new ZipBuilder()
        .addFileWithContent(
            ZipPath.create("BUNDLE-METADATA/some.namespace/metadata1"), new byte[] {0x01})
        .addFileWithProtoContent(ZipPath.create(SDK_BUNDLE_CONFIG_FILE_NAME), sdkBundleConfig)
        .addFileWithContent(ZipPath.create(SDK_INTERFACE_DESCRIPTORS_FILE_NAME), TEST_CONTENT);
  }

  public static ZipBuilder createZipBuilderForSdkAsar() {
    return createZipBuilderForSdkAsar(DEFAULT_SDK_METADATA);
  }

  public static ZipBuilder createZipBuilderForSdkAsar(SdkMetadata sdkMetadata) {
    return new ZipBuilder()
        .addFileWithProtoContent(ZipPath.create(SDK_METADATA_FILE_NAME), sdkMetadata);
  }

  public static ZipBuilder createZipBuilderForModules() {
    return new ZipBuilder()
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(
            ZipPath.create("SdkModulesConfig.pb"), DEFAULT_SDK_MODULES_CONFIG.toByteArray());
  }

  public static ZipBuilder createZipBuilderForModulesWithoutManifest() {
    return new ZipBuilder()
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(
            ZipPath.create("SdkModulesConfig.pb"), DEFAULT_SDK_MODULES_CONFIG.toByteArray());
  }

  public static ZipBuilder createZipBuilderForModulesWithInvalidManifest() {
    return new ZipBuilder()
        .addFileWithProtoContent(
            ZipPath.create("base/manifest/AndroidManifest.xml"), createInvalidSdkAndroidManifest())
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), TEST_CONTENT)
        .addFileWithContent(
            ZipPath.create("SdkModulesConfig.pb"), DEFAULT_SDK_MODULES_CONFIG.toByteArray());
  }

  public static XmlNode createSdkAndroidManifest() {
    return androidManifest(PACKAGE_NAME, withMinSdkVersion(32));
  }

  public static XmlNode createInvalidSdkAndroidManifest() {
    return androidManifest(
        PACKAGE_NAME, withMinSdkVersion(32), withInstallLocation("preferExternal"));
  }

  private static SdkMetadata getSdkMetadata() {
    return SdkMetadata.newBuilder()
        .setPackageName(PACKAGE_NAME)
        .setSdkVersion(DEFAULT_SDK_MODULES_CONFIG.getSdkVersion())
        .build();
  }
}
