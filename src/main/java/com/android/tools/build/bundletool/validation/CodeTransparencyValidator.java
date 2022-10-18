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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.transparency.BundleTransparencyCheckUtils.checkTransparency;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.transparency.TransparencyCheckResult;
import com.google.common.io.ByteSource;
import java.util.Optional;

/** Code transparency file validation. */
public final class CodeTransparencyValidator extends SubValidator {

  @Override
  public void validateBundle(AppBundle bundle) {
    Optional<ByteSource> signedTransparencyFile =
        bundle
            .getBundleMetadata()
            .getFileAsByteSource(
                BundleMetadata.BUNDLETOOL_NAMESPACE, BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME);
    if (!signedTransparencyFile.isPresent()) {
      return;
    }

    TransparencyCheckResult transparencyCheckResult =
        checkTransparency(bundle, signedTransparencyFile.get());
    if (!transparencyCheckResult.verified()) {
      throw InvalidBundleException.builder()
          .withUserMessage(transparencyCheckResult.getErrorMessage())
          .build();
    }
  }
}
