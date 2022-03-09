/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.UnsignedEmbeddedApkConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Set;
import javax.inject.Inject;

/** Identify embedded APKs which should be signed with the same key as generated APKs. */
public class EmbeddedApkSigningPreprocessor implements AppBundlePreprocessor {

  @Inject
  EmbeddedApkSigningPreprocessor() {}

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    ImmutableSet<ZipPath> unsignedEmbeddedApkPaths =
        bundle.getBundleConfig().getUnsignedEmbeddedApkConfigList().stream()
            .map(UnsignedEmbeddedApkConfig::getPath)
            .map(ZipPath::create)
            .collect(toImmutableSet());
    ImmutableSet.Builder<ZipPath> foundApkPaths = ImmutableSet.builder();

    AppBundle appBundle =
        bundle.toBuilder()
            .setRawModules(
                setShouldSign(
                    bundle.getModules().values(), unsignedEmbeddedApkPaths, foundApkPaths))
            .build();

    Set<ZipPath> missingApks = Sets.difference(unsignedEmbeddedApkPaths, foundApkPaths.build());
    if (!missingApks.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Unsigned embedded APKs specified in bundle config but missing from bundle: %s",
              missingApks)
          .build();
    }

    return appBundle;
  }

  @CheckReturnValue
  private static ImmutableList<BundleModule> setShouldSign(
      ImmutableCollection<BundleModule> modules,
      ImmutableSet<ZipPath> unsignedEmbeddedApkPaths,
      ImmutableSet.Builder<ZipPath> foundApkPaths) {
    return modules.stream()
        .map(module -> setShouldSign(module, unsignedEmbeddedApkPaths, foundApkPaths))
        .collect(toImmutableList());
  }

  private static BundleModule setShouldSign(
      BundleModule module,
      ImmutableSet<ZipPath> unsignedEmbeddedApkPaths,
      ImmutableSet.Builder<ZipPath> foundApkPaths) {
    return module.toBuilder()
        .setRawEntries(
            module.getEntries().stream()
                .map(entry -> setShouldSign(entry, unsignedEmbeddedApkPaths, foundApkPaths))
                .collect(toImmutableList()))
        .build();
  }

  private static ModuleEntry setShouldSign(
      ModuleEntry moduleEntry,
      ImmutableSet<ZipPath> unsignedEmbeddedApkPaths,
      ImmutableSet.Builder<ZipPath> foundApkPaths) {
    boolean shouldSign = unsignedEmbeddedApkPaths.contains(moduleEntry.getPath());
    if (shouldSign) {
      foundApkPaths.add(moduleEntry.getPath());
    }
    return moduleEntry.toBuilder().setShouldSign(shouldSign).build();
  }
}
