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
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoPrintUtils;
import com.android.tools.build.bundletool.xml.XPathResolver;
import com.android.tools.build.bundletool.xml.XPathResolver.XPathResult;
import com.android.tools.build.bundletool.xml.XmlNamespaceContext;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

final class DumpManager {

  private final PrintStream printStream;
  private final Path bundlePath;

  DumpManager(OutputStream outputStream, Path bundlePath) {
    this.printStream = new PrintStream(outputStream);
    this.bundlePath = bundlePath;
  }

  void printManifest(BundleModuleName moduleName, Optional<String> xPathExpression) {
    // Extract the manifest from the bundle.
    ZipPath manifestPath =
        ZipPath.create(moduleName.getName()).resolve(SpecialModuleEntry.ANDROID_MANIFEST.getPath());
    XmlProtoNode manifestProto =
        new XmlProtoNode(extractAndParse(bundlePath, manifestPath, XmlNode::parseFrom));

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

  void printResources(Predicate<ResourceTableEntry> resourcePredicate, boolean printValues) {
    ImmutableList<ResourceTable> resourceTables;
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      resourceTables =
          ZipUtils.allFileEntriesPaths(zipFile)
              .filter(path -> path.endsWith(SpecialModuleEntry.RESOURCE_TABLE.getPath()))
              .map(path -> extractAndParse(zipFile, path, ResourceTable::parseFrom))
              .collect(toImmutableList());
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when reading the bundle.", e);
    }

    ImmutableListMultimap<String, ResourceTableEntry> entriesByPackageName =
        resourceTables.stream()
            .flatMap(ResourcesUtils::entries)
            .filter(resourcePredicate)
            .collect(groupingBySortedKeys(entry -> entry.getPackage().getPackageName()));

    for (String packageName : entriesByPackageName.keySet()) {
      printStream.printf("Package '%s':%n", packageName);
      entriesByPackageName.get(packageName).forEach(entry -> printEntry(entry, printValues));
      printStream.println();
    }
  }

  void printBundleConfig() {
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      BundleConfig bundleConfig =
          extractAndParse(zipFile, ZipPath.create("BundleConfig.pb"), BundleConfig::parseFrom);
      printStream.println(JsonFormat.printer().print(bundleConfig));
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when reading the bundle.", e);
    }
  }

  void printRuntimeEnabledSdkConfig() {
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      AppBundle appBundle = AppBundle.buildFromZip(zipFile);
      RuntimeEnabledSdkConfig allRuntimeEnabledSdks =
          RuntimeEnabledSdkConfig.newBuilder()
              .addAllRuntimeEnabledSdk(appBundle.getRuntimeEnabledSdkDependencies().values())
              .build();
      printStream.println(JsonFormat.printer().print(allRuntimeEnabledSdks));
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when reading the bundle.", e);
    }
  }

  private void printEntry(ResourceTableEntry entry, boolean printValues) {
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

  private static <T> T extractAndParse(
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

  private static <T> T extractAndParse(
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

  /** Parser of a compiled proto from an {@link InputStream}. */
  private interface ProtoParser<T> {
    T parse(InputStream is) throws IOException;
  }
}
