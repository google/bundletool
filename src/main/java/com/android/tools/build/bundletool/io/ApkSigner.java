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
package com.android.tools.build.bundletool.io;

import com.android.apksig.ApkSigner.SignerConfig;
import com.android.apksig.apk.ApkFormatException;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import javax.inject.Inject;

/** Signs APKs. */
class ApkSigner {

  /** Name identifying uniquely the {@link SignerConfig} passed to the engine. */
  private static final String SIGNER_CONFIG_NAME = "BNDLTOOL";

  @Inject
  ApkSigner() {}

  /** Signs an embedded APK. */
  void signEmbeddedApk(
      ModuleEntry apkEntry,
      SigningConfiguration signingConfig,
      Path outputApkPath,
      boolean signWithV3) {
    ZipPath targetPath = apkEntry.getPath();
    try (TempDirectory unsignedDir = new TempDirectory()) {
      // Input
      Path unsignedApk = unsignedDir.getPath().resolve("unsigned.apk");
      try (InputStream entryContent = apkEntry.getContent().openStream()) {
        Files.copy(entryContent, unsignedApk);
      }

      // Output
      com.android.apksig.ApkSigner.Builder apkSigner =
          new com.android.apksig.ApkSigner.Builder(extractSignerConfigs(signingConfig, signWithV3))
              .setInputApk(unsignedApk.toFile())
              .setOutputApk(outputApkPath.toFile())
              .setV3SigningEnabled(signWithV3);
      apkSigner.build().sign();
    } catch (ApkFormatException
        | NoSuchAlgorithmException
        | InvalidKeyException
        | SignatureException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Unable to sign the embedded APK '%s'.", targetPath)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Unable to sign the embedded APK '%s'.", targetPath), e);
    }
  }

  void signApk(
      Path inputApkPath,
      Path outputApkPath,
      SigningConfiguration signingConfiguration,
      Optional<SigningConfiguration> stampSigningConfiguration,
      boolean signWithV1,
      boolean signWithV3,
      int minSdkVersion) {
    try {
      com.android.apksig.ApkSigner.Builder apkSigner =
          new com.android.apksig.ApkSigner.Builder(
                  extractSignerConfigs(signingConfiguration, signWithV3))
              .setInputApk(inputApkPath.toFile())
              .setV1SigningEnabled(signWithV1)
              .setV2SigningEnabled(true)
              .setV3SigningEnabled(signWithV3)
              .setOtherSignersSignaturesPreserved(false)
              .setMinSdkVersion(minSdkVersion)
              .setOutputApk(outputApkPath.toFile());
      apkSigner.build().sign();
    } catch (IOException
        | ApkFormatException
        | NoSuchAlgorithmException
        | InvalidKeyException
        | SignatureException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Unable to sign APK.")
          .build();
    }
  }

  private static ImmutableList<SignerConfig> extractSignerConfigs(
      SigningConfiguration signingConfiguration, boolean signWithV3) {
    if (!signWithV3) {
      return ImmutableList.of(
          convertToApksigSignerConfig(signingConfiguration.getSignerConfigForV1AndV2()));
    }

    ImmutableList.Builder<SignerConfig> signerConfigs = ImmutableList.builder();
    signerConfigs.add(convertToApksigSignerConfig(signingConfiguration.getSignerConfig()));
    return signerConfigs.build();
  }

  private static SignerConfig convertToApksigSignerConfig(
      com.android.tools.build.bundletool.model.SignerConfig signerConfig) {
    return new SignerConfig.Builder(
            SIGNER_CONFIG_NAME, signerConfig.getPrivateKey(), signerConfig.getCertificates())
        .build();
  }
}
