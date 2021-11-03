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

package com.android.tools.build.bundletool.model.manifestelements;

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;

import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * Represents Intent filter element of Android manifest.
 *
 * <p>This is not an exhaustive representation, some attributes and child elements might be missing.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class IntentFilter {
  public static final String INTENT_FILTER_ELEMENT_NAME = "intent-filter";
  public static final String CATEGORY_ELEMENT_NAME = "category";

  abstract Optional<String> getActionName();

  abstract Optional<String> getCategoryName();

  public static Builder builder() {
    return new AutoValue_IntentFilter.Builder();
  }

  @Memoized
  public XmlProtoElement asXmlProtoElement() {
    XmlProtoElementBuilder elementBuilder =
        XmlProtoElementBuilder.create(INTENT_FILTER_ELEMENT_NAME);
    setActionElement(elementBuilder);
    setCategoryElement(elementBuilder);
    return elementBuilder.build();
  }

  private void setActionElement(XmlProtoElementBuilder elementBuilder) {
    if (getActionName().isPresent()) {
      elementBuilder.addChildElement(
          XmlProtoElementBuilder.create(ACTION_ELEMENT_NAME)
              .addAttribute(
                  XmlProtoAttributeBuilder.createAndroidAttribute(
                          NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                      .setValueAsString(getActionName().get())));
    }
  }

  private void setCategoryElement(XmlProtoElementBuilder elementBuilder) {
    if (getCategoryName().isPresent()) {
      elementBuilder.addChildElement(
          XmlProtoElementBuilder.create(CATEGORY_ELEMENT_NAME)
              .addAttribute(
                  XmlProtoAttributeBuilder.createAndroidAttribute(
                          NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                      .setValueAsString(getCategoryName().get())));
    }
  }

  /** Builder for IntentFilter. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setActionName(String actionName);

    public abstract Builder setCategoryName(String categoryName);

    public abstract IntentFilter build();
  }
}
