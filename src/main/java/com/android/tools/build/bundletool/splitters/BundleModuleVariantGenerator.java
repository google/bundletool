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

package com.android.tools.build.bundletool.splitters;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import java.util.stream.Stream;

/**
 * Generates additional variant targetings that would be created from the {@link BundleModule}.
 *
 * <p>Each variant generator generates set of variant targetings, skipping the default variant
 * targeting. These targetings are later merged together in the {@link
 * PerModuleVariantTargetingGenerator}.
 */
public interface BundleModuleVariantGenerator {
  Stream<VariantTargeting> generate(BundleModule module);
}
