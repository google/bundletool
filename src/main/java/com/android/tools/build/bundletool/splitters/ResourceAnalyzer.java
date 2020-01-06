/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

/** Provides insights into resources of an app. */
public class ResourceAnalyzer {

  private final AppBundle appBundle;
  private final ResourceIndex resourceIndex;

  public ResourceAnalyzer(AppBundle appBundle) {
    this.appBundle = appBundle;
    this.resourceIndex = new ResourceIndex(appBundle);
  }

  /**
   * Determines which resources are reachable from the {@code AndroidManifest.xml} of the {@code
   * base} module.
   *
   * <p>Note that this does NOT include resources from static libraries.
   */
  public ImmutableSet<ResourceId> findAllAppResourcesReachableFromBaseManifest()
      throws IOException {

    ImmutableSet<ResourceId> resourceIdsInBaseManifest =
        findAllReferencedAppResources(
            appBundle.getBaseModule().getAndroidManifest().getManifestRoot().getProto(),
            appBundle.getBaseModule());

    return transitiveClosure(resourceIdsInBaseManifest);
  }

  private ImmutableSet<ResourceId> transitiveClosure(ImmutableSet<ResourceId> anchorResources)
      throws IOException {
    Set<ResourceId> referencedResources = new HashSet<>();

    Queue<ResourceId> resourcesToInspect = new ArrayDeque<>();
    resourcesToInspect.addAll(anchorResources);

    while (!resourcesToInspect.isEmpty()) {
      ResourceId resourceId = resourcesToInspect.remove();
      if (referencedResources.contains(resourceId)
          || !resourceIndex.isResourceFromApp(resourceId)) {
        continue;
      }
      referencedResources.add(resourceId);

      ResourceTableEntry resourceEntry = resourceIndex.getEntryForResourceId(resourceId);
      BundleModule module = resourceIndex.getModuleForResourceId(resourceId);
      for (ConfigValue configValue : resourceEntry.getEntry().getConfigValueList()) {
        switch (configValue.getValue().getValueCase()) {
          case ITEM:
            Item item = configValue.getValue().getItem();
            resourcesToInspect.addAll(findAllReferencedAppResources(item, module));
            break;

          case COMPOUND_VALUE:
            CompoundValue compoundValue = configValue.getValue().getCompoundValue();
            resourcesToInspect.addAll(findAllReferencedAppResources(compoundValue, module));
            break;

          case VALUE_NOT_SET:
            // Do nothing
        }
      }
    }

    return ImmutableSet.copyOf(referencedResources);
  }

  private ImmutableSet<ResourceId> findAllReferencedAppResources(
      XmlNode xmlRoot, BundleModule module) {
    return getAllAttributesRecursively(xmlRoot.getElement())
        .filter(XmlAttribute::hasCompiledItem)
        .map(XmlAttribute::getCompiledItem)
        .flatMap(item -> findAllReferencedAppResources(item, module).stream())
        .collect(toImmutableSet());
  }

  private ImmutableSet<ResourceId> findAllReferencedAppResources(Item item, BundleModule module) {
    switch (item.getValueCase()) {
      case REF:
        if (item.getRef().getId() != 0) {
          return ImmutableSet.of(ResourceId.create(item.getRef().getId()));
        }
        // Note that if the `id` field of the reference is not set, it is a reference to resource
        // from a static library. Such resource doesn't live inside the app's resource table and
        // we don't need to consider it.
        break;

      case FILE:
        FileReference fileRef = item.getFile();
        if (!fileRef.getType().equals(FileReference.Type.PROTO_XML)) {
          return ImmutableSet.of();
        }
        ZipPath xmlResourcePath = ZipPath.create(fileRef.getPath());
        try (InputStream is = module.getEntry(xmlResourcePath).get().getContent()) {
          XmlNode xmlRoot = XmlNode.parseFrom(is);
          return findAllReferencedAppResources(xmlRoot, module);
        } catch (InvalidProtocolBufferException e) {
          throw ValidationException.builder()
              .withMessage("Error parsing XML file '%s'.", xmlResourcePath)
              .withCause(e)
              .build();
        } catch (IOException e) {
          throw new UncheckedIOException(
              String.format(
                  "Failed to parse file '%s' in module '%s'.",
                  xmlResourcePath, module.getName().getName()),
              e);
        }

      default:
        break;
    }

    return ImmutableSet.of();
  }

  private ImmutableSet<ResourceId> findAllReferencedAppResources(
      CompoundValue compoundValue, BundleModule module) throws IOException {
    switch (compoundValue.getValueCase()) {
      case ATTR:
        return compoundValue.getAttr().getSymbolList().stream()
            .map(symbol -> symbol.getName().getId())
            .filter(id -> id != 0)
            .map(ResourceId::create)
            .collect(toImmutableSet());

      case STYLE:
        ImmutableSet.Builder<ResourceId> referencedResources = ImmutableSet.builder();
        if (compoundValue.getStyle().getParent().getId() != 0) {
          referencedResources.add(ResourceId.create(compoundValue.getStyle().getParent().getId()));
        }
        for (Style.Entry entry : compoundValue.getStyle().getEntryList()) {
          referencedResources.addAll(findAllReferencedAppResources(entry.getItem(), module));
          if (entry.getKey().getId() != 0) {
            referencedResources.add(ResourceId.create(entry.getKey().getId()));
          }
        }
        return referencedResources.build();

      default:
        break;
    }
    return ImmutableSet.of();
  }

  private static Stream<XmlAttribute> getAllAttributesRecursively(XmlElement element) {
    return Stream.concat(
        element.getAttributeList().stream(),
        element.getChildList().stream()
            .filter(node -> node.hasElement())
            .flatMap(node -> getAllAttributesRecursively(node.getElement())));
  }

  private static class ResourceIndex {
    private final ImmutableMap<ResourceId, BundleModule> resourceIdToModule;
    private final ImmutableMap<ResourceId, ResourceTableEntry> resourceIdToEntry;

    private ResourceIndex(AppBundle appBundle) {
      ImmutableMap.Builder<ResourceId, BundleModule> resourceIdToModule = ImmutableMap.builder();
      ImmutableMap.Builder<ResourceId, ResourceTableEntry> resourceIdToEntry =
          ImmutableMap.builder();
      for (BundleModule module : appBundle.getFeatureModules().values()) {
        if (!module.getResourceTable().isPresent()) {
          continue;
        }
        ResourceTable resourceTable = module.getResourceTable().get();
        ResourcesUtils.entries(resourceTable)
            .forEach(
                entry -> {
                  resourceIdToModule.put(entry.getResourceId(), module);
                  resourceIdToEntry.put(entry.getResourceId(), entry);
                });
      }

      this.resourceIdToModule = resourceIdToModule.build();
      this.resourceIdToEntry = resourceIdToEntry.build();
    }

    BundleModule getModuleForResourceId(ResourceId resourceId) {
      return checkNotNull(
          resourceIdToModule.get(resourceId), "Resource ID %s not found", resourceId);
    }

    ResourceTableEntry getEntryForResourceId(ResourceId resourceId) {
      return checkNotNull(
          resourceIdToEntry.get(resourceId), "Resource ID %s not found", resourceId);
    }

    ImmutableSet<ResourceId> getAllResourceIds() {
      return resourceIdToModule.keySet();
    }

    boolean isResourceFromApp(ResourceId resourceId) {
      return getAllResourceIds().contains(resourceId);
    }
  }
}
