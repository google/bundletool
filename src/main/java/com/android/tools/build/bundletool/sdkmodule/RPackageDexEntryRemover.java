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
package com.android.tools.build.bundletool.sdkmodule;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.max;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntriesMutator;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutator that removes dex file with the highest index from the list of entries. This is used when
 * converting Android SDK Bundle to Android App Bundle module, where the last dex file of ASB should
 * not be included. This is because it contains the RPackage class for the SDK, which should instead
 * be inherited from the app's base module.
 */
public class RPackageDexEntryRemover extends ModuleEntriesMutator {

  private static final Pattern CLASSES_DEX_REGEX_PATTERN =
      Pattern.compile("dex/classes(\\d*)\\.dex");

  @Override
  public Predicate<ModuleEntry> getFilter() {
    return entry -> entry.getPath().toString().matches(CLASSES_DEX_REGEX_PATTERN.pattern());
  }

  @Override
  public Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> getMutator() {
    return (dexEntries) ->
        dexEntries.stream()
            .sorted(Comparator.comparing(dexEntry -> getClassesIndexForDexPath(dexEntry.getPath())))
            .limit(max(dexEntries.size() - 1, 0))
            .collect(toImmutableList());
  }

  @Override
  public boolean shouldApplyMutation(BundleModule module) {
    return true;
  }

  private static int getClassesIndexForDexPath(ZipPath entryPath) {
    String fileName = entryPath.toString();

    Matcher matcher = CLASSES_DEX_REGEX_PATTERN.matcher(fileName);
    checkState(matcher.matches());

    String num = matcher.group(1);
    if (num.isEmpty()) {
      return 0; // dex/classes.dex
    }
    return Integer.parseInt(num);
  }
}
