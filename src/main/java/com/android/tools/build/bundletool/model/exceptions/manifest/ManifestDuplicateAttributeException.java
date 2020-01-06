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

package com.android.tools.build.bundletool.model.exceptions.manifest;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.ManifestDuplicateAttributeError;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.google.common.collect.ImmutableSet;

/**
 * Exception thrown when a manifest attribute is filled multiple times while it is expected only
 * once.
 */
public class ManifestDuplicateAttributeException extends ManifestValidationException {

  private final String attributeName;
  private final String moduleName;

  public ManifestDuplicateAttributeException(
      String attributeName, ImmutableSet<XmlProtoAttribute> attributes, String moduleName) {
    super(
        "The attribute '%s' cannot be declared more than once (module '%s', values %s).",
        attributeName,
        moduleName,
        attributes.stream()
            .map(attr -> "'" + attr.getValueAsString() + "'")
            .collect(toImmutableSet()));
    this.attributeName = attributeName;
    this.moduleName = moduleName;
  }

  @Override
  protected void customizeProto(BundleToolError.Builder builder) {
    builder.setManifestDuplicateAttribute(
        ManifestDuplicateAttributeError.newBuilder()
            .setAttributeName(attributeName)
            .setModuleName(moduleName));
  }
}
