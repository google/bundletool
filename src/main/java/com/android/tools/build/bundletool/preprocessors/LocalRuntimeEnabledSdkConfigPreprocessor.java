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
package com.android.tools.build.bundletool.preprocessors;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.android.bundle.RuntimeEnabledSdkConfigProto.CertificateOverride;
import com.android.bundle.RuntimeEnabledSdkConfigProto.LocalDeploymentRuntimeEnabledSdkConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Preprocessor that overrides runtime-enabled SDK config of the app bundle for local deployment.
 */
public class LocalRuntimeEnabledSdkConfigPreprocessor implements AppBundlePreprocessor {

  private final ImmutableMap<String, String> certificateOverridesBySdkName;
  private final String defaultCertificateOverride;

  @Inject
  LocalRuntimeEnabledSdkConfigPreprocessor(
      Optional<LocalDeploymentRuntimeEnabledSdkConfig> localRuntimeEnabledSdkConfig) {
    this.defaultCertificateOverride =
        localRuntimeEnabledSdkConfig
            .map(config -> config.getCertificateOverrides().getDefaultCertificateOverride())
            .orElse("");
    this.certificateOverridesBySdkName =
        localRuntimeEnabledSdkConfig
            .map(LocalRuntimeEnabledSdkConfigPreprocessor::unnestCertificateOverrides)
            .orElse(ImmutableMap.of());
  }

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    if (certificateOverridesBySdkName.isEmpty() && defaultCertificateOverride.isEmpty()) {
      return bundle;
    }
    ImmutableList<BundleModule> updatedModules =
        bundle.getModules().values().stream()
            .map(this::updateRuntimeEnabledSdkConfig)
            .collect(toImmutableList());
    return bundle.toBuilder()
        .setRawModules(updatedModules)
        .setRuntimeEnabledSdkDependencies(getAllRuntimeEnabledSdkDependencies(updatedModules))
        .build();
  }

  private BundleModule updateRuntimeEnabledSdkConfig(BundleModule module) {
    if (module.getRuntimeEnabledSdkConfig().isPresent()) {
      return module.toBuilder()
          .setRuntimeEnabledSdkConfig(
              updateRuntimeEnabledSdkConfig(module.getRuntimeEnabledSdkConfig().get()))
          .build();
    }
    return module;
  }

  private RuntimeEnabledSdkConfig updateRuntimeEnabledSdkConfig(RuntimeEnabledSdkConfig config) {
    RuntimeEnabledSdkConfig.Builder configBuilder = config.toBuilder();
    configBuilder.getRuntimeEnabledSdkBuilderList().forEach(this::updateRuntimeEnabledSdk);
    return configBuilder.build();
  }

  private void updateRuntimeEnabledSdk(RuntimeEnabledSdk.Builder runtimeEnabledSdkBuilder) {
    String sdkPackageName = runtimeEnabledSdkBuilder.getPackageName();
    if (certificateOverridesBySdkName.containsKey(sdkPackageName)) {
      runtimeEnabledSdkBuilder.setCertificateDigest(
          certificateOverridesBySdkName.get(sdkPackageName));
    } else if (!defaultCertificateOverride.isEmpty()) {
      runtimeEnabledSdkBuilder.setCertificateDigest(defaultCertificateOverride);
    }
  }

  private static ImmutableMap<String, String> unnestCertificateOverrides(
      LocalDeploymentRuntimeEnabledSdkConfig localRuntimeEnabledSdkConfig) {
    return localRuntimeEnabledSdkConfig
        .getCertificateOverrides()
        .getPerSdkCertificateOverrideList()
        .stream()
        .collect(
            toImmutableMap(
                CertificateOverride::getSdkPackageName, CertificateOverride::getCertificateDigest));
  }

  private ImmutableMap<String, RuntimeEnabledSdk> getAllRuntimeEnabledSdkDependencies(
      ImmutableList<BundleModule> modules) {
    return modules.stream()
        .filter(module -> module.getRuntimeEnabledSdkConfig().isPresent())
        .map(module -> module.getRuntimeEnabledSdkConfig().get())
        .flatMap(
            runtimeEnabledSdkConfig -> runtimeEnabledSdkConfig.getRuntimeEnabledSdkList().stream())
        .collect(toImmutableMap(RuntimeEnabledSdk::getPackageName, identity()));
  }
}
