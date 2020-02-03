/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.exceptions.manifest;

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.ManifestModulesDifferentVersionCodes;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/** Thrown when {@link BundleModule} version codes have conflicting values. */
public class ManifestVersionCodeConflictException extends ManifestValidationException {

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  private final ImmutableList<Integer> versionCodes;

  public ManifestVersionCodeConflictException(Integer... versionCodes) {
    super(
        "App Bundle modules should have the same version code but found [%s].",
        COMMA_JOINER.join(versionCodes));
    this.versionCodes = ImmutableList.copyOf(versionCodes);
  }

  @Override
  protected void customizeProto(BundleToolError.Builder builder) {
    builder.setManifestModulesDifferentVersionCodes(
        ManifestModulesDifferentVersionCodes.newBuilder().addAllVersionCodes(versionCodes));
  }
}
