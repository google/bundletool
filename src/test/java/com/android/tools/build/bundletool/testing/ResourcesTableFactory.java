/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.ProtoUtils.mergeFromProtos;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.ANY_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.NONE_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.TVDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XHDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XXHDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.XXXHDPI_VALUE;
import static java.util.Arrays.asList;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.EntryId;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Source;
import com.android.aapt.Resources.StringPool;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.TypeId;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.Visibility;
import com.android.aapt.Resources.Visibility.Level;
import java.util.Optional;

/** Factory to build resource tables for tests. */
public final class ResourcesTableFactory {

  public static final Configuration LDPI =
      Configuration.newBuilder().setDensity(LDPI_VALUE).build();
  public static final Configuration MDPI =
      Configuration.newBuilder().setDensity(MDPI_VALUE).build();
  public static final Configuration TVDPI =
      Configuration.newBuilder().setDensity(TVDPI_VALUE).build();
  public static final Configuration HDPI =
      Configuration.newBuilder().setDensity(HDPI_VALUE).build();
  public static final Configuration XHDPI =
      Configuration.newBuilder().setDensity(XHDPI_VALUE).build();
  public static final Configuration XXHDPI =
      Configuration.newBuilder().setDensity(XXHDPI_VALUE).build();
  public static final Configuration _560DPI = forDpi(560);
  public static final Configuration XXXHDPI =
      Configuration.newBuilder().setDensity(XXXHDPI_VALUE).build();
  public static final Configuration ANY_DPI =
      Configuration.newBuilder().setDensity(ANY_DENSITY_VALUE).build();
  public static final Configuration DEFAULT_DPI =
      Configuration.newBuilder().setDensity(DEFAULT_DENSITY_VALUE).build();
  public static final Configuration NO_DPI =
      Configuration.newBuilder().setDensity(NONE_DENSITY_VALUE).build();

  // package id space is reserved for Android Framework from 0x00 till 0x7e.
  public static final int USER_PACKAGE_OFFSET = 0x7f;
  public static final int TEST_LABEL_RESOURCE_ID = 0x7f010000;

  public static Configuration locale(String locale) {
    return Configuration.newBuilder().setLocale(locale).build();
  }

  public static Configuration forDpi(int dpi) {
    return Configuration.newBuilder().setDensity(dpi).build();
  }

  public static Configuration sdk(int apiLevel) {
    return Configuration.newBuilder().setSdkVersion(apiLevel).build();
  }

  public static Configuration mergeConfigs(Configuration config, Configuration... configs) {
    return mergeFromProtos(config, configs);
  }

  public static ConfigValue onlyConfig(Configuration configuration) {
    return ConfigValue.newBuilder().setConfig(configuration).build();
  }

  public static ConfigValue fileReference(String path, Configuration configuration) {
    return fileReference(path, /* type= */ FileReference.Type.UNKNOWN, configuration);
  }

  public static ConfigValue fileReference(
      String path, FileReference.Type type, Configuration configuration) {
    return ConfigValue.newBuilder()
        .setConfig(configuration)
        .setValue(
            Value.newBuilder()
                .setItem(
                    Item.newBuilder()
                        .setFile(FileReference.newBuilder().setPath(path).setType(type))))
        .build();
  }

  public static ConfigValue reference(int referencedId, Configuration configuration) {
    return ConfigValue.newBuilder()
        .setConfig(configuration)
        .setValue(
            Value.newBuilder()
                .setItem(Item.newBuilder().setRef(Reference.newBuilder().setId(referencedId))))
        .build();
  }

  public static ConfigValue value(String value, Configuration configuration) {
    return value(value, configuration, Optional.empty());
  }

  public static ConfigValue value(String value, Configuration configuration, Source source) {
    return value(value, configuration, Optional.of(source));
  }

  private static ConfigValue value(
      String value, Configuration configuration, Optional<Source> source) {
    Value.Builder valueBuilder =
        Value.newBuilder()
            .setItem(Item.newBuilder().setStr(Resources.String.newBuilder().setValue(value)));

    source.ifPresent(valueBuilder::setSource);

    return ConfigValue.newBuilder().setConfig(configuration).setValue(valueBuilder).build();
  }

  public static ResourceTable resourceTable(StringPool sourcePool, Package... packages) {
    return resourceTable(Optional.of(sourcePool), packages);
  }

  public static ResourceTable resourceTable(Package... packages) {
    return resourceTable(Optional.empty(), packages);
  }

  private static ResourceTable resourceTable(Optional<StringPool> sourcePool, Package[] packages) {
    ResourceTable.Builder table = ResourceTable.newBuilder().addAllPackage(asList(packages));
    sourcePool.ifPresent(table::setSourcePool);
    return table.build();
  }

  public static Package pkg(int id, String packageName, Type... types) {
    return Package.newBuilder()
        .setPackageId(PackageId.newBuilder().setId(id))
        .setPackageName(packageName)
        .addAllType(asList(types))
        .build();
  }

  public static Type type(int id, String name, Entry... entries) {
    return Type.newBuilder()
        .setTypeId(TypeId.newBuilder().setId(id))
        .setName(name)
        .addAllEntry(asList(entries))
        .build();
  }

  public static Entry entry(int id, String entryName, ConfigValue... values) {
    return Entry.newBuilder()
        .setEntryId(EntryId.newBuilder().setId(id))
        .setName(entryName)
        .setVisibility(Visibility.newBuilder().setLevel(Level.PUBLIC))
        .addAllConfigValue(asList(values))
        .build();
  }

  public static ResourceTable createResourceTable(
      int entryId, String entryName, ConfigValue... values) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(0x01, "drawable", entry(entryId, entryName, values))));
  }

  public static ResourceTable resourceTableWithTestLabel(Configuration config, String value) {
    return new ResourceTableBuilder()
        .addPackage("com.test.app")
        .addResource("string", "test_label", value(value, config))
        .build();
  }

  public static ResourceTable resourceTableWithTestLabel(String value) {
    return resourceTableWithTestLabel(Configuration.getDefaultInstance(), value);
  }

  // do not instantiate
  private ResourcesTableFactory() {}
}
