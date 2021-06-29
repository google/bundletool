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

import com.android.tools.build.bundletool.commands.CheckTransparencyCommand;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Executes {@link CheckTransparencyCommand} in BUNDLE mode. */
public final class BundleModeTransparencyChecker {

  public static TransparencyCheckResult checkTransparency(CheckTransparencyCommand command) {
    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().get().toFile())) {
      AppBundle inputBundle = AppBundle.buildFromZip(bundleZip);
      return BundleTransparencyCheckUtils.checkTransparency(inputBundle);
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The App Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the App Bundle.", e);
    }
  }

  private BundleModeTransparencyChecker() {}
}
