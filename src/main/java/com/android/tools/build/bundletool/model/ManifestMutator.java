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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;

import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElementBuilder;
import java.util.function.Consumer;

/** Represents a mutation to manifest, which can then be applied to manifest for editing it. */
public interface ManifestMutator extends Consumer<XmlProtoElementBuilder> {

  public static ManifestMutator withExtractNativeLibs(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute(
                EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID)
            .setValueAsBoolean(value);
  }
}
