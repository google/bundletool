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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.android.apksig.ApkSigner.SignerConfig;
import com.android.apksig.apk.ApkFormatException;
import com.android.bundle.Commands.SigningDescription;
import com.android.tools.build.bundletool.commands.BuildApksModule.ApkSigningConfigProvider;
import com.android.tools.build.bundletool.model.ApksigSigningConfiguration;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider.ApkDescription;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.WearApkLocator;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
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
  /** Name identifying uniquely the {@link SignerConfig}. */
  private static final String SIGNER_CONFIG_NAME = "BNDLTOOL";

  private final Optional<SigningConfigurationProvider> signingConfigProvider;
  private final Optional<SourceStamp> sourceStampSigningConfig;
  private final TempDirectory tempDirectory;

  @Inject
  ApkSigner(
      @ApkSigningConfigProvider Optional<SigningConfigurationProvider> signingConfigProvider,
      Optional<SourceStamp> sourceStampSigningConfig,
      TempDirectory tempDirectory) {
    this.signingConfigProvider = signingConfigProvider;
    this.sourceStampSigningConfig = sourceStampSigningConfig;
    this.tempDirectory = tempDirectory;
  }

  public Optional<SigningDescription> signApk(Path apkPath, ModuleSplit split) {
    if (!signingConfigProvider.isPresent()) {
      return Optional.empty();
    }

    ApksigSigningConfiguration signingConfig =
        signingConfigProvider.get().getSigningConfiguration(ApkDescription.fromModuleSplit(split));

    try (TempDirectory tempDirectory = new TempDirectory(getClass().getSimpleName())) {
      Path signedApkPath = tempDirectory.getPath().resolve("signed.apk");
      com.android.apksig.ApkSigner.Builder apkSigner =
          new com.android.apksig.ApkSigner.Builder(
                  signingConfig.getSignerConfigs().stream()
                      .map(ApkSigner::convertToApksigSignerConfig)
                      .collect(toImmutableList()))
              .setInputApk(apkPath.toFile())
              .setOutputApk(signedApkPath.toFile())
              .setV1SigningEnabled(signingConfig.getV1SigningEnabled())
              .setV2SigningEnabled(signingConfig.getV2SigningEnabled())
              .setV3SigningEnabled(signingConfig.getV3SigningEnabled())
              .setOtherSignersSignaturesPreserved(false)
              .setMinSdkVersion(split.getAndroidManifest().getEffectiveMinSdkVersion());
      signingConfig
          .getSigningCertificateLineage()
          .ifPresent(apkSigner::setSigningCertificateLineage);


      sourceStampSigningConfig.ifPresent(
          stampConfig -> {
            apkSigner.setSourceStampSignerConfig(
                convertToApksigSignerConfig(
                    stampConfig.getSigningConfiguration().getSignerConfig()));
          });
      apkSigner.build().sign();
      Files.move(signedApkPath, apkPath, REPLACE_EXISTING);
      return Optional.of(signingDescription(signingConfig));
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

  private SigningDescription signingDescription(ApksigSigningConfiguration signingConfig) {
    boolean usesKeyRotation =
        signingConfig
            .getSigningCertificateLineage()
            .map(lineage -> lineage.size() > 1)
            .orElse(false);
    return SigningDescription.newBuilder().setSignedWithRotatedKey(usesKeyRotation).build();
  }

  /**
   * Returns a new {@link ModuleSplit} with the same entries as the one given as parameter but with
   * embedded APKs signed.
   */
  @CheckReturnValue
  public ModuleSplit signEmbeddedApks(ModuleSplit split) {
    ImmutableSet<ZipPath> wear1ApkPaths =
        ImmutableSet.copyOf(WearApkLocator.findEmbeddedWearApkPaths(split));
    ImmutableList.Builder<ModuleEntry> newEntries = ImmutableList.builder();
    for (ModuleEntry entry : split.getEntries()) {
      ZipPath pathInApk = ApkSerializerHelper.toApkEntryPath(entry.getPath());
      if (entry.getShouldSign() || wear1ApkPaths.contains(pathInApk)) {
        newEntries.add(signModuleEntry(split, entry));
      } else {
        newEntries.add(entry);
      }
    }
    return split.toBuilder().setEntries(newEntries.build()).build();
  }

  /**
   * Extracts the given {@link ModuleEntry} to the filesystem then signs the file as an APK and
   * returns a new ModuleEntry with the signed APK as content.
   */
  private ModuleEntry signModuleEntry(ModuleSplit split, ModuleEntry entry) {
    try {
      // Creating a new temp directory to ensure unicity of APK name in the temp directory..
      Path tempDir = Files.createTempDirectory(tempDirectory.getPath(), getClass().getSimpleName());
      Path embeddedApk = tempDir.resolve("embedded.apk");
      try (InputStream entryContent = entry.getContent().openStream()) {
        Files.copy(entryContent, embeddedApk);
      }
      signApk(embeddedApk, split);
      return entry.toBuilder().setContent(embeddedApk).setShouldSign(false).build();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static SignerConfig convertToApksigSignerConfig(
      com.android.tools.build.bundletool.model.SignerConfig signerConfig) {
    return new SignerConfig.Builder(
            SIGNER_CONFIG_NAME, signerConfig.getPrivateKey(), signerConfig.getCertificates())
        .build();
  }
}
