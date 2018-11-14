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

import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static java.util.stream.Collectors.toList;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.xml.XPathResolver;
import com.android.tools.build.bundletool.xml.XPathResolver.XPathResult;
import com.android.tools.build.bundletool.xml.XmlNamespaceContext;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

/** Command that prints information about a given Android App Bundle. */
@AutoValue
public abstract class DumpCommand {

  public static final String COMMAND_NAME = "dump";

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");
  private static final Flag<String> MODULE_FLAG = Flag.string("module");
  private static final Flag<String> XPATH_FLAG = Flag.string("xpath");

  public abstract Path getBundlePath();

  public abstract PrintStream getOutputStream();

  public abstract DumpTarget getDumpTarget();

  public abstract String getModuleName();

  public abstract Optional<String> getXPathExpression();

  public static Builder builder() {
    return new AutoValue_DumpCommand.Builder()
        .setModuleName(BundleModuleName.BASE_MODULE_NAME)
        .setOutputStream(System.out);
  }

  /** Builder for the {@link DumpCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the bundle. */
    public abstract Builder setBundlePath(Path bundlePath);

    /** Sets the output stream where the dump should be printed. */
    public abstract Builder setOutputStream(PrintStream outputStream);

    /** Sets the target of the dump, e.g. the manifest. */
    public abstract Builder setDumpTarget(DumpTarget dumpTarget);

    /** Sets the module for the target of the dump. */
    public abstract Builder setModuleName(String moduleName);

    public abstract Builder setXPathExpression(String xPathExpression);

    public abstract DumpCommand build();
  }

  public static DumpCommand fromFlags(ParsedFlags flags) {
    DumpTarget dumpTarget = parseDumpTarget(flags);

    Path bundlePath = BUNDLE_LOCATION_FLAG.getRequiredValue(flags);
    String moduleName = MODULE_FLAG.getValue(flags).orElse(BundleModuleName.BASE_MODULE_NAME);
    Optional<String> xPath = XPATH_FLAG.getValue(flags);

    DumpCommand.Builder dumpCommand =
        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(dumpTarget)
            .setModuleName(moduleName);

    xPath.ifPresent(dumpCommand::setXPathExpression);

    return dumpCommand.build();
  }

  public void execute() {
    validateInput();

    switch (getDumpTarget()) {
      case MANIFEST:
        printManifest(getXPathExpression());
        break;
    }
  }

  private void printManifest(Optional<String> xPathExpression) {
    // Extract the manifest from the bundle.
    XmlProtoNode manifestProto;
    try (ZipFile zipFile = new ZipFile(getBundlePath().toFile())) {
      ZipPath manifestPath = ZipPath.create(getModuleName()).resolve(BundleModule.MANIFEST_PATH);
      ZipEntry manifestEntry = zipFile.getEntry(manifestPath.toString());
      if (manifestEntry == null) {
        throw ValidationException.builder()
            .withMessage(
                "No manifest found for module '%s'. Does the module exist?", getModuleName())
            .build();
      }

      try (InputStream manifestInputStream = zipFile.getInputStream(manifestEntry)) {
        manifestProto = new XmlProtoNode(XmlNode.parseFrom(manifestInputStream));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read the manifest from the bundle.", e);
    }

    // Convert the proto to real XML.
    Document document = XmlProtoToXmlConverter.convert(manifestProto);

    // Select the output.
    String output;
    if (xPathExpression.isPresent()) {
      try {
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(new XmlNamespaceContext(manifestProto));
        XPathExpression compiledXPathExpression = xPath.compile(xPathExpression.get());
        XPathResult xPathResult  = XPathResolver.resolve(document, compiledXPathExpression);
        output = xPathResult.toString();
      } catch (XPathExpressionException e) {
        throw new ValidationException("Error in the XPath expression: " + xPathExpression, e);
      }
    } else {
      output = XmlUtils.documentToString(document);
    }

    // Print the output.
    getOutputStream().println(output.trim());
  }

  private void validateInput() {
    checkFileExistsAndReadable(getBundlePath());
  }

  private static DumpTarget parseDumpTarget(ParsedFlags flags) {
    String subCommand =
        flags
            .getSubCommand()
            .orElseThrow(() -> new ValidationException("Target of the dump not found."));

    DumpTarget dumpTarget;
    switch (subCommand) {
      case "manifest":
        dumpTarget = DumpTarget.MANIFEST;
        break;
      default:
        throw ValidationException.builder()
            .withMessage(
                "Unrecognized dump target: '%s'. Accepted values are: %s",
                subCommand,
                Arrays.stream(DumpTarget.values())
                    .map(Enum::toString)
                    .map(String::toLowerCase)
                    .collect(toList()))
            .build();
    }
    return dumpTarget;
  }

  /** Target of the dump. */
  public enum DumpTarget {
    MANIFEST,
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Prints files or extract values from the bundle in a human-readable form.")
                .addAdditionalParagraph(
                    "To print the manifest, one can for example run: "
                        + "bundletool dump manifest --bundle=/tmp/app.aab")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("bundle")
                .setDescription("Path to the Android App Bundle.")
                .setExampleValue("app.aab")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("module")
                .setDescription("Name of the module to apply the dump for. Defaults to 'base'.")
                .setExampleValue("base")
                .setOptional(true)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("xpath")
                .setDescription(
                    "XPath expression to extract the value of attributes from the XML file being "
                    + "dumped.")
                .setExampleValue("/manifest/@android:versionCode")
                .setOptional(true)
                .build())
        .build();
  }
}
