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
package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.sdkmodule.DexAndResourceRepackager.getCompatSdkConfigPathInAssets;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.xml.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Injects RuntimeEnabledSdkTable.xml config into the main split of the base module, as well as
 * standalone splits, in the backwards-compatible variant of apps that have runtime-enabled SDK
 * dependencies.
 *
 * <p>RuntimeEnabledSdkTable.xml contains paths to compat SDK config files inside the assets
 * directory. There is 1 compat SDK config file per runtime-enabled SDK dependency of the app. Here
 * is what example RuntimeEnabledSdkTable.xml looks like:
 *
 * <pre>{@code
 * <runtime-enabled-sdk-table>
 *   <runtime-enabled-sdk>
 *     <package-name>com.sdk1</package-name>
 *     <compat-config-path>RuntimeEnabledSdk-com.sdk1/CompatSdkConfig.xml</compat-config-path>
 *   </runtime-enabled-sdk>
 *   <runtime-enabled-sdk>
 *     <package-name>com.sdk2</package-name>
 *     <version-major>11000<version-major/>
 *     <compat-config-path>RuntimeEnabledSdk-com.sdk2/CompatSdkConfig.xml</compat-config-path>
 *   </runtime-enabled-sdk>
 * </runtime-enabled-sdk-table>
 * }</pre>
 */
public final class RuntimeEnabledSdkTableInjector {

  @VisibleForTesting
  public static final String RUNTIME_ENABLED_SDK_TABLE_FILE_PATH =
      "assets/RuntimeEnabledSdkTable.xml";

  private static final String RUNTIME_ENABLED_SDK_TABLE_ELEMENT_NAME = "runtime-enabled-sdk-table";
  private static final String RUNTIME_ENABELD_SDK_ELEMENT_NAME = "runtime-enabled-sdk";
  private static final String SDK_PACKAGE_NAME_ELEMENT_NAME = "package-name";

  private static final String SDK_VERSION_MAJOR_ELEMENT_NAME = "version-major";
  private static final String COMPAT_CONFIG_PATH_ELEMENT_NAME = "compat-config-path";

  private final AppBundle appBundle;

  public RuntimeEnabledSdkTableInjector(AppBundle appBundle) {
    this.appBundle = appBundle;
  }

  public ModuleSplit inject(ModuleSplit split) {
    if (appBundle.getRuntimeEnabledSdkDependencies().isEmpty()
        || !shouldAddRuntimeEnabledSdkTable(split)) {
      return split;
    }
    return split.toBuilder()
        .addEntry(
            ModuleEntry.builder()
                .setPath(ZipPath.create(RUNTIME_ENABLED_SDK_TABLE_FILE_PATH))
                .setContent(
                    ByteSource.wrap(
                        generateRuntimeEnabledSdkTableBytes(
                            appBundle.getRuntimeEnabledSdkDependencies().values())))
                .build())
        .build();
  }

  public static byte[] generateRuntimeEnabledSdkTableBytes(
      ImmutableCollection<RuntimeEnabledSdk> runtimeEnabledSdks) {
    return XmlUtils.documentToString(
            getRuntimeEnabledSdkTable(ImmutableSet.copyOf(runtimeEnabledSdks)))
        .getBytes(UTF_8);
  }

  private boolean shouldAddRuntimeEnabledSdkTable(ModuleSplit split) {
    return !split.getVariantTargeting().getSdkRuntimeTargeting().getRequiresSdkRuntime()
        && (split.getSplitType() == SplitType.STANDALONE
            || (split.isMasterSplit() && split.isBaseModuleSplit()));
  }

  private static Document getRuntimeEnabledSdkTable(
      ImmutableSet<RuntimeEnabledSdk> runtimeEnabledSdks) {
    Document runtimeEnabledSdkTable;
    try {
      runtimeEnabledSdkTable =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
    runtimeEnabledSdkTable.appendChild(
        createRuntimeEnabledSdkTableNode(runtimeEnabledSdkTable, runtimeEnabledSdks));
    return runtimeEnabledSdkTable;
  }

  private static  Node createRuntimeEnabledSdkTableNode(
      Document xmlFactory, ImmutableSet<RuntimeEnabledSdk> runtimeEnabledSdks) {
    Element runtimeEnabledSdkTableNode =
        xmlFactory.createElement(RUNTIME_ENABLED_SDK_TABLE_ELEMENT_NAME);
    runtimeEnabledSdks.forEach(
        runtimeEnabledSdk ->
            runtimeEnabledSdkTableNode.appendChild(
                createRuntimeEnabledSdkNode(xmlFactory, runtimeEnabledSdk)));
    return runtimeEnabledSdkTableNode;
  }

  private static Node createRuntimeEnabledSdkNode(
      Document xmlFactory, RuntimeEnabledSdk runtimeEnabledSdk) {
    Element runtimeEnabledSdkNode = xmlFactory.createElement(RUNTIME_ENABELD_SDK_ELEMENT_NAME);
    Element sdkPackageNameNode = xmlFactory.createElement(SDK_PACKAGE_NAME_ELEMENT_NAME);
    sdkPackageNameNode.setTextContent(runtimeEnabledSdk.getPackageName());
    Element sdkVersionMajorNode = xmlFactory.createElement(SDK_VERSION_MAJOR_ELEMENT_NAME);
    sdkVersionMajorNode.setTextContent(
        String.valueOf(
            RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(
                runtimeEnabledSdk.getVersionMajor(), runtimeEnabledSdk.getVersionMinor())));
    Element compatConfigPathNode = xmlFactory.createElement(COMPAT_CONFIG_PATH_ELEMENT_NAME);
    compatConfigPathNode.setTextContent(
        getCompatSdkConfigPathInAssets(runtimeEnabledSdk.getPackageName()));
    runtimeEnabledSdkNode.appendChild(sdkPackageNameNode);
    runtimeEnabledSdkNode.appendChild(sdkVersionMajorNode);
    runtimeEnabledSdkNode.appendChild(compatConfigPathNode);
    return runtimeEnabledSdkNode;
  }
}
