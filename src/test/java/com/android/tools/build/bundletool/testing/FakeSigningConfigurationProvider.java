/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.build.bundletool.testing;

import com.android.tools.build.bundletool.model.ApksigSigningConfiguration;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import java.util.function.Function;

/** Fake customizable implementation for {@link SigningConfigurationProvider}. */
public class FakeSigningConfigurationProvider implements SigningConfigurationProvider {

  private final Function<ApkDescription, ApksigSigningConfiguration> provider;
  private boolean restrictedV3SigningConfig;

  public FakeSigningConfigurationProvider(
      Function<ApkDescription, ApksigSigningConfiguration> provider) {
    this.provider = provider;
  }

  public FakeSigningConfigurationProvider(
      Function<ApkDescription, ApksigSigningConfiguration> provider,
      boolean restrictedV3SigningConfig) {
    this.provider = provider;
    this.restrictedV3SigningConfig = restrictedV3SigningConfig;
  }

  @Override
  public ApksigSigningConfiguration getSigningConfiguration(ApkDescription apkDescription) {
    return provider.apply(apkDescription);
  }

  @Override
  public boolean hasRestrictedV3SigningConfig() {
    return restrictedV3SigningConfig;
  }
}
