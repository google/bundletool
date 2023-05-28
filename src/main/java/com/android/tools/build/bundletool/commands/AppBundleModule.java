/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/**
 * Dagger module for components that manipulate an App Bundle.
 */
@Module
public abstract class AppBundleModule {

    @CommandScoped
    @Provides
    static BundleConfig provideBundleConfig(AppBundle appBundle) {
        return appBundle.getBundleConfig();
    }

    @CommandScoped
    @Provides
    static BundleMetadata provideBundleMetadata(AppBundle appBundle) {
        return appBundle.getBundleMetadata();
    }

    private AppBundleModule() {
    }

    @Binds
    abstract Bundle bundle(AppBundle bundle);
}
