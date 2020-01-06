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
package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameProvider;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.android.aapt.Resources.XmlElement;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ManifestProtoUtilsTest {

  @Test
  public void splitNameAttributeAddedToActivity() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest(
                "com.test.app",
                withMainActivity("MainActivity"),
                withSplitNameActivity("FooActivity", "foo")));

    ImmutableList<XmlElement> activities =
        manifest
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(2);
    XmlElement activityElement = activities.get(1);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooActivity"),
            xmlAttribute(
                ANDROID_NAMESPACE_URI, SPLIT_NAME_ATTRIBUTE_NAME, SPLIT_NAME_RESOURCE_ID, "foo"));
  }

  @Test
  public void splitNameAttributeAddedToService() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameService("FooService", "foo")));

    ImmutableList<XmlElement> services =
        manifest
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(SERVICE_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(services).hasSize(1);
    XmlElement serviceElement = Iterables.getOnlyElement(services);
    assertThat(serviceElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooService"),
            xmlAttribute(
                ANDROID_NAMESPACE_URI, SPLIT_NAME_ATTRIBUTE_NAME, SPLIT_NAME_RESOURCE_ID, "foo"));
  }

  @Test
  public void splitNameAttributeAddedToProvider() {
    AndroidManifest manifest =
        AndroidManifest.create(
            androidManifest("com.test.app", withSplitNameProvider("FooProvider", "foo")));

    ImmutableList<XmlElement> providers =
        manifest
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(PROVIDER_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(providers).hasSize(1);
    XmlElement providerElement = Iterables.getOnlyElement(providers);
    assertThat(providerElement.getAttributeList())
        .containsExactly(
            xmlAttribute(
                ANDROID_NAMESPACE_URI, NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID, "FooProvider"),
            xmlAttribute(
                ANDROID_NAMESPACE_URI, SPLIT_NAME_ATTRIBUTE_NAME, SPLIT_NAME_RESOURCE_ID, "foo"));
  }
}
