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

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXPORTED_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXPORTED_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_RESOURCE_ID;

import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * Represents Activity element of Android manifest.
 *
 * <p>This is not an exhaustive representation, some attributes and child elements might be missing.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class Activity {
  public static final String EXCLUDE_FROM_RECENTS_ELEMENT_NAME = "excludeFromRecents";
  public static final String STATE_NOT_NEEDED_ELEMENT_NAME = "stateNotNeeded";
  public static final String NO_HISTORY_ELEMENT_NAME = "noHistory";

  public static final int EXCLUDE_FROM_RECENTS_RESOURCE_ID = 0x01010017;
  public static final int STATE_NOT_NEEDED_RESOURCE_ID = 0x01010016;
  public static final int NO_HISTORY_RESOURCE_ID = 0x0101022d;

  abstract Optional<String> getName();

  abstract Optional<Integer> getTheme();

  abstract Optional<Boolean> getExported();

  abstract Optional<Boolean> getExcludeFromRecents();

  abstract Optional<Boolean> getStateNotNeeded();

  abstract Optional<Boolean> getNoHistory();

  abstract Optional<IntentFilter> getIntentFilter();

  public static Builder builder() {
    return new AutoValue_Activity.Builder();
  }

  @Memoized
  public XmlProtoElement asXmlProtoElement() {
    XmlProtoElementBuilder elementBuilder = XmlProtoElementBuilder.create(ACTIVITY_ELEMENT_NAME);
    setNameAttribute(elementBuilder);
    setThemeAttribute(elementBuilder);
    setExportedAttribute(elementBuilder);
    setExcludeFromRecentsAttribute(elementBuilder);
    setStateNotNeeded(elementBuilder);
    setNoHistory(elementBuilder);
    setIntentFilterElement(elementBuilder);
    return elementBuilder.build();
  }

  private void setNameAttribute(XmlProtoElementBuilder elementBuilder) {
    if (getName().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
          .setValueAsString(getName().get());
    }
  }

  private void setThemeAttribute(XmlProtoElementBuilder elementBuilder) {
    if (getTheme().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(THEME_ATTRIBUTE_NAME, THEME_RESOURCE_ID)
          .setValueAsRefId(getTheme().get());
    }
  }

  private void setExportedAttribute(XmlProtoElementBuilder elementBuilder) {
    if (getExported().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(EXPORTED_ATTRIBUTE_NAME, EXPORTED_RESOURCE_ID)
          .setValueAsBoolean(getExported().get());
    }
  }

  private void setExcludeFromRecentsAttribute(XmlProtoElementBuilder elementBuilder) {
    if (getExcludeFromRecents().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(
              EXCLUDE_FROM_RECENTS_ELEMENT_NAME, EXCLUDE_FROM_RECENTS_RESOURCE_ID)
          .setValueAsBoolean(getExcludeFromRecents().get());
    }
  }

  private void setStateNotNeeded(XmlProtoElementBuilder elementBuilder) {
    if (getStateNotNeeded().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(STATE_NOT_NEEDED_ELEMENT_NAME, STATE_NOT_NEEDED_RESOURCE_ID)
          .setValueAsBoolean(getStateNotNeeded().get());
    }
  }

  private void setNoHistory(XmlProtoElementBuilder elementBuilder) {
    if (getNoHistory().isPresent()) {
      elementBuilder
          .getOrCreateAndroidAttribute(NO_HISTORY_ELEMENT_NAME, NO_HISTORY_RESOURCE_ID)
          .setValueAsBoolean(getNoHistory().get());
    }
  }

  private void setIntentFilterElement(XmlProtoElementBuilder elementBuilder) {
    if (getIntentFilter().isPresent()) {
      elementBuilder.addChildElement(getIntentFilter().get().asXmlProtoElement().toBuilder());
    }
  }

  /** Builder for Activity. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setTheme(int themeResId);

    public abstract Builder setExported(boolean exported);

    public abstract Builder setExcludeFromRecents(boolean excludeFromRecents);

    public abstract Builder setStateNotNeeded(boolean stateNotNeeded);

    public abstract Builder setNoHistory(boolean noHistory);

    public abstract Builder setIntentFilter(IntentFilter intentFilter);

    public abstract Activity build();
  }
}
