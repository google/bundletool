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
package com.android.tools.build.bundletool.model.utils.xmlproto;

import com.android.aapt.Resources.XmlNamespace;
import com.google.errorprone.annotations.Immutable;

/** Wrapper around the {@link XmlNamespace} proto, providing a fluent API. */
@Immutable
public final class XmlProtoNamespace {

  private final XmlNamespace namespace;

  public XmlProtoNamespace(XmlNamespace namespace) {
    this.namespace = namespace;
  }

  public XmlNamespace getProto() {
    return namespace;
  }

  public static XmlProtoNamespace create(String prefix, String uri) {
    return new XmlProtoNamespace(XmlNamespace.newBuilder().setPrefix(prefix).setUri(uri).build());
  }

  public String getPrefix() {
    return namespace.getPrefix();
  }

  public String getUri() {
    return namespace.getUri();
  }

  @Override
  public int hashCode() {
    return namespace.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XmlProtoNamespace)) {
      return false;
    }
    return namespace.equals(((XmlProtoNamespace) o).getProto());
  }
}
