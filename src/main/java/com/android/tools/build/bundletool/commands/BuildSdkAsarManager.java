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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils.getCertificateFingerprint;

import com.android.bundle.SdkMetadataOuterClass.SdkMetadata;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.io.SdkAsarSerializer;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.SdkAsar;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import javax.inject.Inject;
import org.w3c.dom.Document;

/** Executes the "build-sdk-asar" command. */
public class BuildSdkAsarManager {
  private final BuildSdkAsarCommand command;
  private final SdkBundle sdkBundle;

  @Inject
  BuildSdkAsarManager(BuildSdkAsarCommand command, SdkBundle sdkBundle) {
    this.command = command;
    this.sdkBundle = sdkBundle;
  }

  void execute(Path extractedModulesFilePath) throws IOException {
    if (command.getOverwriteOutput() && Files.exists(command.getOutputFile())) {
      MoreFiles.deleteRecursively(command.getOutputFile(), RecursiveDeleteOption.ALLOW_INSECURE);
    }

    SdkAsarSerializer.writeToDisk(
        generateSdkAsar(extractedModulesFilePath), command.getOutputFile());
  }

  private SdkAsar generateSdkAsar(Path extractedModulesFilePath) {
    SdkModulesConfig sdkModulesConfig = sdkBundle.getSdkModulesConfig();
    AndroidManifest manifest = sdkBundle.getModule().getAndroidManifest();
    Document manifestDoc = XmlProtoToXmlConverter.convert(manifest.getManifestRoot());

    SdkMetadata.Builder metadata =
        SdkMetadata.newBuilder()
            .setPackageName(sdkModulesConfig.getSdkPackageName())
            .setSdkVersion(sdkModulesConfig.getSdkVersion());

    command
        .getApkSigningCertificate()
        .map(BuildSdkAsarManager::getFormattedCertificateDigest)
        .ifPresent(metadata::setCertificateDigest);

    SdkAsar.Builder asar =
        SdkAsar.builder()
            .setManifest(manifestDoc)
            .setModule(sdkBundle.getModule())
            .setSdkModulesConfig(sdkModulesConfig)
            .setModulesFile(extractedModulesFilePath.toFile())
            .setSdkMetadata(metadata.build());
    sdkBundle.getSdkInterfaceDescriptors().ifPresent(asar::setSdkInterfaceDescriptors);
    return asar.build();
  }

  private static String getFormattedCertificateDigest(X509Certificate certificate) {
    return getCertificateFingerprint(certificate).replace(' ', ':');
  }
}
