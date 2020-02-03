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

package com.android.tools.build.bundletool.splitters;

import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;

/**
 * Splits a Bundle Module represented as a {@link ModuleSplit}.
 *
 * <p>Each splitter must generate a set of ModuleSplits that when merged together, don't leave
 * behind any valid entry from the input ModuleSplit. Valid entry here means having a valid path
 * with regards to App Bundle Format.
 */
public interface ModuleSplitSplitter {

  ImmutableCollection<ModuleSplit> split(ModuleSplit split);
}
