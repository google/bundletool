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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.android.apksig.apk.ApkFormatException;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
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
import javax.inject.Inject;

/** Signs APKs. */
class ApkSigner {

  private final SigningConfigurationHelper signingConfigHelper;
  private final TempDirectory tempDirectory;

  @Inject
  ApkSigner(SigningConfigurationHelper signingConfigHelper, TempDirectory tempDirectory) {
    this.signingConfigHelper = signingConfigHelper;
    this.tempDirectory = tempDirectory;
  }

  public void signApk(Path apkPath, ModuleSplit split) {
    if (!signingConfigHelper.shouldSignGeneratedApks()) {
      return;
    }

    try (TempDirectory tempDirectory = new TempDirectory(getClass().getSimpleName())) {
      Path signedApkPath = tempDirectory.getPath().resolve("signed.apk");
      com.android.apksig.ApkSigner.Builder apkSigner =
          new com.android.apksig.ApkSigner.Builder(
                  signingConfigHelper.getSignerConfigsForSplit(split))
              .setInputApk(apkPath.toFile())
              .setOutputApk(signedApkPath.toFile())
              .setV1SigningEnabled(signingConfigHelper.shouldSignWithV1(split))
              .setV2SigningEnabled(true)
              .setV3SigningEnabled(true)
              .setOtherSignersSignaturesPreserved(false)
              .setMinSdkVersion(split.getAndroidManifest().getEffectiveMinSdkVersion());
      signingConfigHelper
          .getSigningCertificateLineageForSplit(split)
          .ifPresent(apkSigner::setSigningCertificateLineage);
      signingConfigHelper
          .getSourceStampSignerConfig()
          .ifPresent(apkSigner::setSourceStampSignerConfig);
      apkSigner.build().sign();
      Files.move(signedApkPath, apkPath, REPLACE_EXISTING);
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
}
