/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.utils.BundleParser.extractModules;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readSdkModulesConfig;
import static com.android.tools.build.bundletool.model.utils.BundleParser.sanitize;

import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;

/** Represents an SDK ASAR. */
@AutoValue
public abstract class SdkAsar {

  public static final String SDK_METADATA_FILE_NAME = "SdkMetadata.pb";

  public static SdkAsar buildFromZip(ZipFile asar, ZipFile modulesFile, Path modulesFilePath) {
    SdkModulesConfig sdkModulesConfig = readSdkModulesConfig(modulesFile);
    BundleModule sdkModule =
        Iterables.getOnlyElement(
                sanitize(
                    extractModules(
                        modulesFile,
                        BundleType.REGULAR,
                        Version.of(sdkModulesConfig.getBundletool().getVersion()),
                        /* apexConfig= */ Optional.empty(),
                        /* nonModuleDirectories= */ ImmutableSet.of())))
            .toBuilder()
            .setSdkModulesConfig(sdkModulesConfig)
            .build();
    SdkAsar.Builder sdkAsarBuilder =
        builder()
            .setModule(sdkModule)
            .setSdkModulesConfig(sdkModulesConfig)
            .setModulesFile(modulesFilePath.toFile())
            .setSdkMetadata(readSdkMetadata(asar));
    Document document;
    try {
      document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
    // Setting an empty manifest, because we don't need the text manifest for generating APKs.
    // APK generation is the only use case for constructing an SdkAsar from a previously generated
    // ASAR zip.
    return sdkAsarBuilder.setManifest(document).build();
  }

  /** Returns manifest stored in the ASAR in the text format. */
  public abstract Document getManifest();

  public abstract BundleModule getModule();

  public abstract SdkModulesConfig getSdkModulesConfig();

  /** Path to the RESM archive extracted from the ASAR. */
  public abstract File getModulesFile();

  public abstract SdkMetadata getSdkMetadata();

  public abstract Optional<ByteSource> getSdkInterfaceDescriptors();

  /**
   * Gets the SDK package name.
   *
   * <p>Note that this is different from the package name used in the APK AndroidManifest, which is
   * a combination of the SDK package name and its Android version major.
   */
  public String getPackageName() {
    return getSdkMetadata().getPackageName();
  }

  /** Gets the major version of the SDK. */
  public int getMajorVersion() {
    return getSdkMetadata().getSdkVersion().getMajor();
  }

  /** Gets the minor version of the SDK. */
  public int getMinorVersion() {
    return getSdkMetadata().getSdkVersion().getMinor();
  }

  /**
   * Returns the SHA-256 hash of the runtime-enabled SDK's signing certificate, represented as a
   * string of bytes in hexadecimal form, with ':' separating the bytes.
   */
  public String getCertificateDigest() {
    return getSdkMetadata().getCertificateDigest();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_SdkAsar.Builder();
  }

  /** Builder for SDK ASARs. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setManifest(Document manifest);

    public abstract Builder setModule(BundleModule module);

    public abstract Builder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig);

    public abstract Builder setModulesFile(File modulesFile);

    public abstract Builder setSdkMetadata(SdkMetadata sdkMetadata);

    public abstract Builder setSdkInterfaceDescriptors(ByteSource source);

    public abstract SdkAsar build();
  }

  /** Loads {@link SDK_METADATA_FILE_NAME} from zip file into {@link SdkMetadata}. */
  @SuppressWarnings("ProtoParseWithRegistry")
  private static SdkMetadata readSdkMetadata(ZipFile asarFile) {
    ZipEntry sdkMetadataEntry = asarFile.getEntry(SDK_METADATA_FILE_NAME);
    if (sdkMetadataEntry == null) {
      throw InvalidBundleException.builder()
          .withUserMessage("ASAR is expected to contain '%s' file.", SDK_METADATA_FILE_NAME)
          .build();
    }
    try {
      return SdkMetadata.parseFrom(ZipUtils.asByteSource(asarFile, sdkMetadataEntry).read());
    } catch (InvalidProtocolBufferException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("SDK metadata file '%s' could not be parsed.", SDK_METADATA_FILE_NAME)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading file '%s'.", SDK_METADATA_FILE_NAME), e);
    }
  }
}
