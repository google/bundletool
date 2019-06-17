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
package com.android.tools.build.bundletool.xml;

import com.google.common.collect.ImmutableBiMap;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Node;

/** Utility methods related to XML manipulation. */
public final class XmlUtils {

  static final ImmutableBiMap<String, String> COMMON_NAMESPACE_PREFIXES =
      ImmutableBiMap.<String, String>builder()
          .put("http://schemas.android.com/apk/res/android", "android")
          .put("http://schemas.android.com/apk/distribution", "dist")
          .put("http://schemas.android.com/tools", "tools")
          .build();

  public static String documentToString(Node document) {
    StringWriter sw = new StringWriter();
    try {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.transform(new DOMSource(document), new StreamResult(sw));
    } catch (TransformerException e) {
      throw new IllegalStateException("An error occurred while converting the XML to a string", e);
    }
    return sw.toString();
  }

  private XmlUtils() {}
}
