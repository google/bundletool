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

package com.android.tools.build.bundletool.archive;

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANIMATE_LAYOUT_CHANGES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANIMATE_LAYOUT_CHANGES_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.FITS_SYSTEM_WINDOWS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.FITS_SYSTEM_WINDOWS_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.FRAME_LAYOUT_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.IMAGE_VIEW_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_GRAVITY_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_GRAVITY_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_HEIGHT_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_HEIGHT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_WIDTH_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LAYOUT_WIDTH_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SRC_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SRC_RESOURCE_ID;

import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/** Representation of FrameLayout XmlNode. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ArchivedSplashScreenLayout {

  // These values are obtained from an already compiled xml with these attributes.
  static final int DP_170_INTEGER = 43521;
  static final int MATCH_PARENT = -1;
  static final int LAYOUT_GRAVITY_CENTER = 17;

  abstract Optional<Integer> getLayoutWidth();

  abstract Optional<Integer> getLayoutHeight();

  abstract Optional<Boolean> getAnimateLayoutChanges();

  abstract Optional<Boolean> getFitsSystemWindows();

  abstract Optional<Integer> getImageResourceId();

  public static Builder builder() {
    return new AutoValue_ArchivedSplashScreenLayout.Builder();
  }

  public XmlProtoElement asXmlProtoElement() {
    XmlProtoElementBuilder elementBuilder =
        XmlProtoElementBuilder.create(FRAME_LAYOUT_ELEMENT_NAME)
            .addNamespaceDeclaration("android", ANDROID_NAMESPACE_URI);
    setLayoutHeight(elementBuilder);
    setLayoutWidth(elementBuilder);
    setAnimateLayoutChanges(elementBuilder);
    setFitsSystemWindows(elementBuilder);
    setSplashScreenIconImage(elementBuilder);
    return elementBuilder.build();
  }

  private void setLayoutWidth(XmlProtoElementBuilder elementBuilder) {
    if (getLayoutWidth().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(LAYOUT_WIDTH_ATTRIBUTE_NAME, LAYOUT_WIDTH_RESOURCE_ID)
          .setValueAsDimension(getLayoutWidth().get());
    }
  }

  private void setLayoutHeight(XmlProtoElementBuilder elementBuilder) {
    if (getLayoutHeight().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(LAYOUT_HEIGHT_ATTRIBUTE_NAME, LAYOUT_HEIGHT_RESOURCE_ID)
          .setValueAsDimension(getLayoutHeight().get());
    }
  }

  private void setAnimateLayoutChanges(XmlProtoElementBuilder elementBuilder) {
    if (getAnimateLayoutChanges().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(
              ANIMATE_LAYOUT_CHANGES_ATTRIBUTE_NAME, ANIMATE_LAYOUT_CHANGES_RESOURCE_ID)
          .setValueAsBoolean(getAnimateLayoutChanges().get());
    }
  }

  private void setFitsSystemWindows(XmlProtoElementBuilder elementBuilder) {
    if (getFitsSystemWindows().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(
              FITS_SYSTEM_WINDOWS_ATTRIBUTE_NAME, FITS_SYSTEM_WINDOWS_RESOURCE_ID)
          .setValueAsBoolean(getFitsSystemWindows().get());
    }
  }

  private void setSplashScreenIconImage(XmlProtoElementBuilder elementBuilder) {
    if (getImageResourceId().isPresent()) {
      XmlProtoElementBuilder imageViewElement =
          XmlProtoElementBuilder.create(IMAGE_VIEW_ELEMENT_NAME);
      imageViewElement
          .getOrCreateAndroidAttribute(LAYOUT_HEIGHT_ATTRIBUTE_NAME, LAYOUT_HEIGHT_RESOURCE_ID)
          .setValueAsDimension(DP_170_INTEGER);
      imageViewElement
          .getOrCreateAndroidAttribute(LAYOUT_WIDTH_ATTRIBUTE_NAME, LAYOUT_WIDTH_RESOURCE_ID)
          .setValueAsDimension(DP_170_INTEGER);
      imageViewElement
          .getOrCreateAndroidAttribute(LAYOUT_GRAVITY_ATTRIBUTE_NAME, LAYOUT_GRAVITY_RESOURCE_ID)
          .setValueAsHexInteger(LAYOUT_GRAVITY_CENTER);
      imageViewElement
          .getOrCreateAndroidAttribute(SRC_ATTRIBUTE_NAME, SRC_RESOURCE_ID)
          .setValueAsRefId(getImageResourceId().get());
      elementBuilder.addChildElement(imageViewElement);
    }
  }

  /** Builder for FrameLayout object */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLayoutWidth(int width);

    public abstract Builder setLayoutHeight(int height);

    public abstract Builder setAnimateLayoutChanges(boolean animateLayoutChanges);

    public abstract Builder setFitsSystemWindows(boolean fitsSystemWindows);

    public abstract Builder setImageResourceId(int resourceId);

    public abstract ArchivedSplashScreenLayout build();
  }
}
