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
package com.android.tools.build.bundletool.sdkmodule;

import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.remapPackageIdInResourceId;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.aapt.Resources.Array;
import com.android.aapt.Resources.Attribute;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.Plural;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Styleable;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.Value;
import com.android.tools.build.bundletool.model.BundleModule;

/**
 * Remaps resource IDs in the resource table of the given {@link BundleModule} with a new package
 * ID.
 */
final class ResourceTablePackageIdRemapper {

  private final int newPackageId;

  ResourceTablePackageIdRemapper(int newPackageId) {
    this.newPackageId = newPackageId;
  }

  /**
   * Updates resource IDs in the resource table of the given module with the {@code newPackageId}.
   */
  BundleModule remap(BundleModule module) {
    if (!module.getResourceTable().isPresent()) {
      return module;
    }
    ResourceTable resourceTable = module.getResourceTable().get();
    checkArgument(
        resourceTable.getPackageCount() <= 1,
        "Module '%s' contains resource table with %s 'package' entries, but only 1 entry is"
            + " allowed.",
        module.getName().getName(),
        resourceTable.getPackageCount());

    ResourceTable.Builder remappedResourceTable = resourceTable.toBuilder();
    remappedResourceTable.getPackageBuilderList().forEach(this::remapInPackage);
    return module.toBuilder().setResourceTable(remappedResourceTable.build()).build();
  }

  private void remapInPackage(Package.Builder resourceTablePackage) {
    resourceTablePackage
        .setPackageId(PackageId.newBuilder().setId(newPackageId))
        .getTypeBuilderList()
        .forEach(this::remapInType);
  }

  private void remapInType(Type.Builder type) {
    type.getEntryBuilderList().forEach(this::remapInEntry);
  }

  private void remapInEntry(Entry.Builder entry) {
    entry.getConfigValueBuilderList().forEach(this::remapInConfigValue);
  }

  private void remapInConfigValue(ConfigValue.Builder configValue) {
    if (!configValue.hasValue()) {
      return;
    }
    remapInValue(configValue.getValueBuilder());
  }

  private void remapInValue(Value.Builder value) {
    if (value.hasItem()) {
      remapInItemValue(value);
    } else {
      remapInCompoundValue(value);
    }
  }

  private void remapInItemValue(Value.Builder value) {
    if (!value.hasItem()) {
      return;
    }
    remapInItem(value.getItemBuilder());
  }

  private void remapInItem(Item.Builder item) {
    if (!item.hasRef()) {
      return;
    }
    remapInReference(item.getRefBuilder());
  }

  private void remapInCompoundValue(Value.Builder value) {
    if (!value.hasCompoundValue()) {
      return;
    }
    remapInCompoundValue(value.getCompoundValueBuilder());
  }

  private void remapInCompoundValue(CompoundValue.Builder compoundValue) {
    switch (compoundValue.getValueCase()) {
      case ATTR:
        remapInAttributeCompoundValue(compoundValue);
        break;
      case STYLE:
        remapInStyleCompoundValue(compoundValue);
        break;
      case STYLEABLE:
        remapInStylableCompoundValue(compoundValue);
        break;
      case ARRAY:
        remapInArrayCompoundValue(compoundValue);
        break;
      case PLURAL:
        remapInPluralCompoundValue(compoundValue);
        break;
      case MACRO:
      case VALUE_NOT_SET:
    }
  }

  private void remapInAttributeCompoundValue(CompoundValue.Builder compoundValue) {
    if (!compoundValue.hasAttr()) {
      return;
    }
    remapInAttribute(compoundValue.getAttrBuilder());
  }

  private void remapInStyleCompoundValue(CompoundValue.Builder compoundValue) {
    if (!compoundValue.hasStyle()) {
      return;
    }
    remapInStyle(compoundValue.getStyleBuilder());
  }

  private void remapInStylableCompoundValue(CompoundValue.Builder compoundValue) {
    if (!compoundValue.hasStyleable()) {
      return;
    }
    remapInStyleable(compoundValue.getStyleableBuilder());
  }

  private void remapInArrayCompoundValue(CompoundValue.Builder compoundValue) {
    if (!compoundValue.hasArray()) {
      return;
    }
    remapInArray(compoundValue.getArrayBuilder());
  }

  private void remapInPluralCompoundValue(CompoundValue.Builder compoundValue) {
    if (!compoundValue.hasPlural()) {
      return;
    }
    remapInPlural(compoundValue.getPluralBuilder());
  }

  private void remapInAttribute(Attribute.Builder attribute) {
    attribute.getSymbolBuilderList().forEach(this::remapInSymbol);
  }

  private void remapInSymbol(Attribute.Symbol.Builder symbol) {
    if (!symbol.hasName()) {
      return;
    }
    remapInReference(symbol.getNameBuilder());
  }

  private void remapInStyle(Style.Builder style) {
    if (style.hasParent()) {
      remapInReference(style.getParentBuilder());
    }
    style.getEntryBuilderList().forEach(this::remapInStyleEntry);
  }

  private void remapInStyleable(Styleable.Builder styleable) {
    styleable.getEntryBuilderList().forEach(this::remapInStyleableEntry);
  }

  private void remapInArray(Array.Builder array) {
    array.getElementBuilderList().forEach(this::remapInArrayElement);
  }

  private void remapInPlural(Plural.Builder plural) {
    plural.getEntryBuilderList().forEach(this::remapInPluralEntry);
  }

  private void remapInStyleEntry(Style.Entry.Builder styleEntry) {
    if (!styleEntry.hasKey()) {
      return;
    }
    remapInReference(styleEntry.getKeyBuilder());
  }

  private void remapInStyleableEntry(Styleable.Entry.Builder styleableEntry) {
    if (!styleableEntry.hasAttr()) {
      return;
    }
    remapInReference(styleableEntry.getAttrBuilder());
  }

  private void remapInArrayElement(Array.Element.Builder element) {
    if (!element.hasItem()) {
      return;
    }
    remapInItem(element.getItemBuilder());
  }

  private void remapInPluralEntry(Plural.Entry.Builder entry) {
    if (!entry.hasItem()) {
      return;
    }
    remapInItem(entry.getItemBuilder());
  }

  private void remapInReference(Reference.Builder reference) {
    reference.setId(remapPackageIdInResourceId(reference.getId(), newPackageId));
  }
}
