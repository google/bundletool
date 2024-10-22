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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingBySortedKeys;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoPrintUtils;
import com.android.tools.build.bundletool.xml.XPathResolver;
import com.android.tools.build.bundletool.xml.XPathResolver.XPathResult;
import com.android.tools.build.bundletool.xml.XmlNamespaceContext;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

/** Utility class for the dump commands {@link DumpCommand} and {@link DumpSdkBundleCommand}. */
public final class DumpManagerUtils {

  public static void printManifest(
      XmlProtoNode manifestProto, Optional<String> xPathExpression, PrintStream printStream) {

    // Convert the proto to real XML.
    Document document = XmlProtoToXmlConverter.convert(manifestProto);

    // Select the output.
    String output;
    if (xPathExpression.isPresent()) {
      try {
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(new XmlNamespaceContext(manifestProto));
        XPathExpression compiledXPathExpression = xPath.compile(xPathExpression.get());
        XPathResult xPathResult = XPathResolver.resolve(document, compiledXPathExpression);
        output = xPathResult.toString();
      } catch (XPathExpressionException e) {
        throw InvalidCommandException.builder()
            .withInternalMessage("Error in the XPath expression: " + xPathExpression)
            .withCause(e)
            .build();
      }
    } else {
      output = XmlUtils.documentToString(document);
    }

    // Print the output.
    printStream.println(output.trim());
  }

  public static void printBundleConfig(MessageOrBuilder bundleConfig, PrintStream printStream) {
    try {
      printStream.println(JsonFormat.printer().print(bundleConfig));
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when reading the bundle.", e);
    }
  }

  public static void printResources(
      Predicate<ResourceTableEntry> resourcePredicate,
      boolean printValues,
      ImmutableList<ResourceTable> resourceTables,
      PrintStream printStream) {
    ImmutableListMultimap<String, ResourceTableEntry> entriesByPackageName =
        resourceTables.stream()
            .flatMap(ResourcesUtils::entries)
            .filter(resourcePredicate)
            .collect(groupingBySortedKeys(entry -> entry.getPackage().getPackageName()));

    for (String packageName : entriesByPackageName.keySet()) {
      printStream.printf("Package '%s':%n", packageName);
      entriesByPackageName
          .get(packageName)
          .forEach(entry -> printEntry(entry, printValues, printStream));
      printStream.println();
    }
  }

  private static void printEntry(
      ResourceTableEntry entry, boolean printValues, PrintStream printStream) {
    printStream.printf(
        "0x%08x - %s/%s%n",
        entry.getResourceId().getFullResourceId(),
        entry.getType().getName(),
        entry.getEntry().getName());

    for (ConfigValue configValue : entry.getEntry().getConfigValueList()) {
      printStream.print('\t');
      if (configValue.getConfig().equals(Configuration.getDefaultInstance())) {
        printStream.print("(default)");
      } else {
        printStream.print(configValue.getConfig().toString().trim());
      }
      if (printValues) {
        printStream.printf(
            " - [%s] %s",
            XmlProtoPrintUtils.getValueTypeAsString(configValue.getValue()),
            XmlProtoPrintUtils.getValueAsString(configValue.getValue()));
      }
      printStream.println();
    }
  }

  public static <T> T extractAndParseFromSdkBundle(
      Path bundlePath, ZipPath filePath, ProtoParser<T> protoParser) {
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      ZipEntry modulesFile = zipFile.getEntry("modules.resm");
      ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream(modulesFile));
      return extractAndParse(zipInputStream, filePath, protoParser);
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Bundle is not a valid zip file.")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when trying to open the bundle.", e);
    }
  }

  public static <T> T extractAndParseFromAppBundle(
      Path bundlePath, ZipPath filePath, ProtoParser<T> protoParser) {
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      return extractAndParse(zipFile, filePath, protoParser);
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Bundle is not a valid zip file.")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when trying to open the bundle.", e);
    }
  }

  public static <T> T extractAndParse(
      ZipInputStream zipInputStream, ZipPath filePath, ProtoParser<T> protoParser) {
    try {
      ZipEntry zipEntry;
      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        if (zipEntry.getName().equals(filePath.toString())) {
          return protoParser.parse(zipInputStream);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Error occurred when trying to read file '" + filePath + "' from bundle.", e);
    }
    throw InvalidBundleException.builder()
        .withUserMessage("File '%s' not found.", filePath)
        .build();
  }

  public static <T> T extractAndParse(
      ZipFile zipFile, ZipPath filePath, ProtoParser<T> protoParser) {
    ZipEntry fileEntry = zipFile.getEntry(filePath.toString());
    if (fileEntry == null) {
      throw InvalidBundleException.builder()
          .withUserMessage("File '%s' not found.", filePath)
          .build();
    }

    try (InputStream inputStream = zipFile.getInputStream(fileEntry)) {
      return protoParser.parse(inputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Error occurred when trying to read file '" + filePath + "' from bundle.", e);
    }
  }

  private DumpManagerUtils() {}

  /** Parser of a compiled proto from an {@link InputStream}. */
  public interface ProtoParser<T> {
    T parse(InputStream is) throws IOException;
  }
}
