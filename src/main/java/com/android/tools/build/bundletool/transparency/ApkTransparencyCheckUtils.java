/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.transparency;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jose4j.jws.JsonWebSignature;

/** Helper class for verifying code transparency for a given set of device-specific APKs. */
public final class ApkTransparencyCheckUtils {

  private static final String TRANSPARENCY_FILE_ZIP_ENTRY_NAME =
      "META-INF/" + BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME;

  public static TransparencyCheckResult checkTransparency(ImmutableList<Path> deviceSpecificApks) {
    Optional<Path> baseApkPath = getBaseApkPath(deviceSpecificApks);
    if (!baseApkPath.isPresent()) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "The provided list of device specific APKs must either contain a single APK, or, if"
                  + " multiple APK files are present, base.apk file.")
          .build();
    }

    TransparencyCheckResult.Builder result = TransparencyCheckResult.builder();
    ApkSignatureVerifier.Result apkSignatureVerificationResult =
        ApkSignatureVerifier.verify(deviceSpecificApks);
    if (!apkSignatureVerificationResult.verified()) {
      return result
          .errorMessage("Verification failed: " + apkSignatureVerificationResult.getErrorMessage())
          .build();
    }
    result.apkSigningKeyCertificateFingerprint(
        apkSignatureVerificationResult.getApkSigningKeyCertificateFingerprint());

    try (ZipFile baseApkFile = ZipUtils.openZipFile(baseApkPath.get())) {
      Optional<ZipEntry> transparencyFileEntry =
          Optional.ofNullable(baseApkFile.getEntry(TRANSPARENCY_FILE_ZIP_ENTRY_NAME));
      if (!transparencyFileEntry.isPresent()) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "Could not verify code transparency because transparency file is not present in the"
                    + " APK.")
            .build();
      }

      JsonWebSignature jws =
          CodeTransparencyCryptoUtils.parseJws(
              ZipUtils.asByteSource(baseApkFile, transparencyFileEntry.get()));
      boolean signatureVerified = CodeTransparencyCryptoUtils.verifySignature(jws);
      if (!signatureVerified) {
        return result
            .errorMessage("Verification failed because code transparency signature is invalid.")
            .build();
      }
      result
          .transparencySignatureVerified(true)
          .transparencyKeyCertificateFingerprint(
              CodeTransparencyCryptoUtils.getCertificateFingerprint(jws));

      CodeTransparency codeTransparencyMetadata =
          CodeTransparencyFactory.parseFrom(jws.getUnverifiedPayload());
      CodeTransparencyVersion.checkVersion(codeTransparencyMetadata);

      ImmutableSet<String> pathsToModifiedFiles =
          getModifiedFiles(codeTransparencyMetadata, deviceSpecificApks);
      result.fileContentsVerified(pathsToModifiedFiles.isEmpty());
      if (!pathsToModifiedFiles.isEmpty()) {
        result.errorMessage(
            "Verification failed because code was modified after code transparency metadata"
                + " generation. Modified files: "
                + pathsToModifiedFiles);
      }
      return result.build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the file.", e);
    }
  }

  private static Optional<Path> getBaseApkPath(ImmutableList<Path> apkPaths) {
    // If only 1 APK is present, it is assumed to be a universal or standalone APK.
    if (apkPaths.size() == 1) {
      return apkPaths.get(0).getFileName().toString().endsWith(".apk")
          ? Optional.of(apkPaths.get(0))
          : Optional.empty();
    }
    return apkPaths.stream()
        .filter(apkPath -> apkPath.getFileName().toString().equals("base.apk"))
        .findAny();
  }

  private static ImmutableSet<String> getModifiedFiles(
      CodeTransparency codeTransparencyMetadata, ImmutableList<Path> allApkPaths) {
    ImmutableSet.Builder<String> pathsToModifiedFilesBuilder = ImmutableSet.builder();
    for (Path apkPath : allApkPaths) {
      try (ZipFile apkFile = ZipUtils.openZipFile(apkPath)) {
        pathsToModifiedFilesBuilder.addAll(getModifiedDexFiles(apkFile, codeTransparencyMetadata));
        pathsToModifiedFilesBuilder.addAll(
            getModifiedNativeLibraries(apkFile, codeTransparencyMetadata));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return pathsToModifiedFilesBuilder.build();
  }

  private static ImmutableSet<String> getModifiedDexFiles(
      ZipFile apkFile, CodeTransparency codeTransparencyMetadata) {
    ImmutableSet<String> expectedDexFiles = getDexFiles(codeTransparencyMetadata);
    ImmutableSet.Builder<String> pathsToModifiedFilesBuilder = ImmutableSet.builder();
    apkFile.stream()
        .forEach(
            zipEntry -> {
              String fileHash = getFileHash(apkFile, zipEntry);
              if (isDexFile(zipEntry) && !expectedDexFiles.contains(fileHash)) {
                pathsToModifiedFilesBuilder.add(zipEntry.getName());
              }
            });
    return pathsToModifiedFilesBuilder.build();
  }

  private static ImmutableSet<String> getModifiedNativeLibraries(
      ZipFile apkFile, CodeTransparency codeTransparencyMetadata) {
    ImmutableMap<String, String> expectedNativeLibrariesByApkPath =
        getNativeLibrariesByApkPath(codeTransparencyMetadata);
    ImmutableSet.Builder<String> pathsToModifiedFilesBuilder = ImmutableSet.builder();
    apkFile.stream()
        .forEach(
            zipEntry -> {
              String fileHash = getFileHash(apkFile, zipEntry);
              if (isNativeLibrary(zipEntry)
                  && !Optional.ofNullable(expectedNativeLibrariesByApkPath.get(zipEntry.getName()))
                      .equals(Optional.of(fileHash))) {
                pathsToModifiedFilesBuilder.add(zipEntry.getName());
              }
            });
    return pathsToModifiedFilesBuilder.build();
  }

  private static ImmutableSet<String> getDexFiles(CodeTransparency codeTransparency) {
    return codeTransparency.getCodeRelatedFileList().stream()
        .filter(ApkTransparencyCheckUtils::isDexFile)
        .map(CodeRelatedFile::getSha256)
        .collect(toImmutableSet());
  }

  private static ImmutableMap<String, String> getNativeLibrariesByApkPath(
      CodeTransparency codeTransparency) {
    return codeTransparency.getCodeRelatedFileList().stream()
        .filter(
            codeRelatedFile ->
                codeRelatedFile.getType().equals(CodeRelatedFile.Type.NATIVE_LIBRARY))
        .collect(toImmutableMap(CodeRelatedFile::getApkPath, CodeRelatedFile::getSha256));
  }

  private static boolean isDexFile(ZipEntry zipEntry) {
    return zipEntry.getName().endsWith(".dex");
  }

  private static boolean isDexFile(CodeRelatedFile codeRelatedFile) {
    return codeRelatedFile.getType().equals(CodeRelatedFile.Type.DEX)
        // Code transparency files generated using Bundletool with version older than
        // 1.8.1 do not have type field set for dex files.
        || (codeRelatedFile.getType().equals(CodeRelatedFile.Type.TYPE_UNSPECIFIED)
            && codeRelatedFile.getPath().endsWith(".dex"));
  }

  private static boolean isNativeLibrary(ZipEntry zipEntry) {
    return zipEntry.getName().endsWith(".so");
  }

  private static String getFileHash(ZipFile apkFile, ZipEntry zipEntry) {
    try {
      return ZipUtils.asByteSource(apkFile, zipEntry).hash(Hashing.sha256()).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ApkTransparencyCheckUtils() {}
}
