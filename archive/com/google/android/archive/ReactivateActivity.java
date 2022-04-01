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

package com.google.android.archive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;

/** Activity that triggers the reactivation of an app through the app store. */
public class ReactivateActivity extends Activity implements DialogInterface.OnClickListener {

  private String appStorePackageName;
  private boolean processingError;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    appStorePackageName = getAppStorePackageName();
  }

  @Override
  public void onResume() {
    super.onResume();
    // Ensures we don't try to send another intent immediately after the first one failed, instead
    // an error dialog is shown.
    if (processingError) {
      return;
    }
    Intent intent = new Intent();
    intent.setAction("com.google.android.STORE_ARCHIVE");
    intent.setPackage(appStorePackageName);
    try {
      startActivityForResult(intent, /* flags= */ 0);
    } catch (ActivityNotFoundException e) {
      // We handle this case in onActivityResult, because a RESULT_CANCELED is emitted.
    }
  }

  /** Returns true if the targeted Store is installed and enabled. */
  private boolean isStoreInstalled() {
    try {
      return getPackageManager().getApplicationInfo(appStorePackageName, 0).enabled;
    } catch (NameNotFoundException e) {
      return false;
    }
  }

  private AlertDialog buildErrorDialog() {
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle("Installation failed")
            .setCancelable(false)
            .setNeutralButton("Close", this)
            .setMessage(
                String.format(
                    "The app %s is currently archived and must be reinstalled from an"
                        + " official app store.",
                    getAppName()));

    if (isStoreInstalled()) {
      dialog.setPositiveButton("Reinstall", this);
    }

    return dialog.create();
  }

  private String getAppName() {
    return getApplicationInfo().loadLabel(getPackageManager()).toString();
  }

  @Override
  public void onClick(DialogInterface ignored, int buttonType) {
    processingError = false;
    switch (buttonType) {
      case DialogInterface.BUTTON_POSITIVE:
        openStorePageForApp();
        break;
      case DialogInterface.BUTTON_NEUTRAL:
      default:
        // Nothing specific, just close the app.
        finish();
        break;
    }
  }

  private void openStorePageForApp() {
    Intent intent =
        new Intent(Intent.ACTION_VIEW)
            .setPackage(appStorePackageName)
            .setData(Uri.parse(String.format("market://details?id=%s", getPackageName())));

    startActivity(intent);
  }

  @Override
  public void onActivityResult(int ignored1, int resultCode, Intent ignored2) {
    if (resultCode == Activity.RESULT_CANCELED) {
      processingError = true;
      buildErrorDialog().show();
    } else {
      // At this point the reactivation has completed and the app should be restarted immediately,
      // if there is some delay here we don't want to show an empty activity.
      finish();
    }
  }

  /**
   * Getting resource by id does not work because classes.dex is prebuild and
   * reactivation_app_store_package_name resource is added dynamically with the next available id.
   */
  @SuppressLint("DiscouragedApi")
  private String getAppStorePackageName() {
    return getResources()
        .getString(
            getResources()
                .getIdentifier("reactivation_app_store_package_name", "string", getPackageName()));
  }
}
