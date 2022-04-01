/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.build.bundletool.archive;

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ManifestEditor;
import com.android.tools.build.bundletool.model.manifestelements.Activity;
import com.android.tools.build.bundletool.model.manifestelements.IntentFilter;
import com.android.tools.build.bundletool.model.manifestelements.Receiver;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import java.util.Optional;

/** Utility methods for creation of archived manifest. */
public final class ArchivedAndroidManifestUtils {
  public static final String META_DATA_KEY_ARCHIVED = "com.android.vending.archive";

  public static final String REACTIVATE_ACTIVITY_NAME =
      "com.google.android.archive.ReactivateActivity";
  public static final String HOLO_LIGHT_NO_ACTION_BAR_THEME =
      "@android:style/Theme.Holo.Light.NoActionBar";
  public static final String MAIN_ACTION_NAME = "android.intent.action.MAIN";
  public static final String LAUNCHER_CATEGORY_NAME = "android.intent.category.LAUNCHER";

  public static final String UPDATE_BROADCAST_RECEIVER_NAME =
      "com.google.android.archive.UpdateBroadcastReceiver";
  public static final String MY_PACKAGE_REPLACED_ACTION_NAME =
      "android.intent.action.MY_PACKAGE_REPLACED";

  public static AndroidManifest createArchivedManifest(AndroidManifest manifest) {
    checkNotNull(manifest);

    ManifestEditor editor =
        new ManifestEditor(createMinimalManifestTag(), BundleToolVersion.getCurrentVersion())
            .setPackage(manifest.getPackageName())
            .addMetaDataBoolean(META_DATA_KEY_ARCHIVED, true);

    manifest.getVersionCode().ifPresent(editor::setVersionCode);
    manifest.getVersionName().ifPresent(editor::setVersionName);
    manifest.getSharedUserId().ifPresent(editor::setSharedUserId);
    manifest.getSharedUserLabel().ifPresent(editor::setSharedUserLabel);
    manifest.getMinSdkVersion().ifPresent(editor::setMinSdkVersion);
    manifest.getMaxSdkVersion().ifPresent(editor::setMaxSdkVersion);
    manifest.getTargetSdkVersion().ifPresent(editor::setTargetSdkVersion);
    manifest.getTargetSandboxVersion().ifPresent(editor::setTargetSandboxVersion);

    if (manifest.hasApplicationElement()) {
      manifest.getDescription().ifPresent(editor::setDescription);
      manifest.getHasFragileUserData().ifPresent(editor::setHasFragileUserData);
      manifest.getIsGame().ifPresent(editor::setIsGame);
      manifest.getIcon().ifPresent(editor::setIcon);
      if (manifest.hasLabelString()) {
        manifest.getLabelString().ifPresent(editor::setLabelAsString);
      }
      if (manifest.hasLabelRefId()) {
        manifest.getLabelRefId().ifPresent(editor::setLabelAsRefId);
      }
      getArchivedAllowBackup(manifest).ifPresent(editor::setAllowBackup);
      manifest.getFullBackupOnly().ifPresent(editor::setFullBackupOnly);
      manifest.getFullBackupContent().ifPresent(editor::setFullBackupContent);
      manifest.getDataExtractionRules().ifPresent(editor::setDataExtractionRules);
    }

    editor.copyPermissions(manifest);
    editor.copyPermissionGroups(manifest);

    editor.addActivity(createReactivateActivity());
    editor.addReceiver(createUpdateBroadcastReceiver());

    return editor.save();
  }

  private static Optional<Boolean> getArchivedAllowBackup(AndroidManifest manifest) {
    // Backup needs to be disabled if Backup Agent is provided and Full Backup Only is disabled.
    // Custom backup agent cannot be kept because it relies on app code that is not present in its
    // archived variant.
    return manifest.getAllowBackup().orElse(true)
            && (!manifest.hasBackupAgent() || manifest.getFullBackupOnly().orElse(false))
        ? manifest.getAllowBackup()
        : Optional.of(Boolean.FALSE);
  }

  private static XmlProtoNode createMinimalManifestTag() {
    return XmlProtoNode.createElementNode(
        XmlProtoElementBuilder.create("manifest")
            .addNamespaceDeclaration("android", ANDROID_NAMESPACE_URI)
            .build());
  }

  private static Activity createReactivateActivity() {
    return Activity.builder()
        .setName(REACTIVATE_ACTIVITY_NAME)
        .setTheme(HOLO_LIGHT_NO_ACTION_BAR_THEME)
        .setExported(true)
        .setExcludeFromRecents(true)
        .setStateNotNeeded(true)
        .setIntentFilter(
            IntentFilter.builder()
                .setActionName(MAIN_ACTION_NAME)
                .setCategoryName(LAUNCHER_CATEGORY_NAME)
                .build())
        .build();
  }

  private static Receiver createUpdateBroadcastReceiver() {
    return Receiver.builder()
        .setName(UPDATE_BROADCAST_RECEIVER_NAME)
        .setExported(true)
        .setIntentFilter(
            IntentFilter.builder().setActionName(MY_PACKAGE_REPLACED_ACTION_NAME).build())
        .build();
  }

  private ArchivedAndroidManifestUtils() {}
}
