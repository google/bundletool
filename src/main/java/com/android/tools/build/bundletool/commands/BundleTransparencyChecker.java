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
package com.android.tools.build.bundletool.commands;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.transparency.CodeTransparencyChecker;
import com.android.tools.build.bundletool.transparency.TransparencyCheckResult;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Checks code transparency in a given bundle. */
final class BundleTransparencyChecker {

  static void checkTransparency(CheckTransparencyCommand command, PrintStream outputStream) {
    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().get().toFile())) {
      AppBundle inputBundle = AppBundle.buildFromZip(bundleZip);
      Optional<ByteSource> signedTransparencyFile =
          inputBundle
              .getBundleMetadata()
              .getFileAsByteSource(
                  BundleMetadata.BUNDLETOOL_NAMESPACE,
                  BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME);
      if (!signedTransparencyFile.isPresent()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Bundle does not include code transparency metadata. Run `add-transparency`"
                    + " command to add code transparency metadata to the bundle.")
            .build();
      }
      TransparencyCheckResult transparencyCheckResult =
          CodeTransparencyChecker.checkTransparency(inputBundle, signedTransparencyFile.get());
      if (!transparencyCheckResult.signatureVerified()) {
        outputStream.print("Code transparency verification failed because signature is invalid.");
      } else if (!transparencyCheckResult.fileContentsVerified()) {
        outputStream.print(
            "Code transparency verification failed because code was modified after transparency"
                + " metadata generation.\n"
                + transparencyCheckResult.getDiffAsString());
      } else {
        outputStream.print(
            "Code transparency verified. Public key certificate fingerprint: "
                + transparencyCheckResult.certificateThumbprint().get());
      }
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The App Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the App Bundle.", e);
    }
  }

  private BundleTransparencyChecker() {}
}
