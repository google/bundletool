package com.android.tools.build.bundletool.splitters;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ModuleSplit;

/**
 * Copies baseline.prof and baseline.profm from bundle metadata to apk in assets/dexopt directory.
 * <p>
 * Support both files placement options:
 * <ul>
 *     <li>BUNDLE-METADATA/assets/dexopt</li>
 *     <li>BUNDLE-METADATA/com.android.tools.build.profiles</li>
 * </ul>
 */
public class BaselineProfileInjector {

    private final AppBundle appBundle;

    public BaselineProfileInjector(AppBundle appBundle) {
        this.appBundle = appBundle;
    }

    public ModuleSplit inject(ModuleSplit split) {
        ModuleSplit.Builder splitBuilder = split.toBuilder();
        if (shouldAddBaselineProfileToSplit(split)) {
            appBundle
                .getBundleMetadata()
                .getBaselineProfiles()
                .forEach(file -> file.ifPresent(splitBuilder::addEntry));
        }
        return splitBuilder.build();
    }

    private boolean shouldAddBaselineProfileToSplit(ModuleSplit split) {
        if (split.getSplitType() == ModuleSplit.SplitType.STANDALONE) {
            return true;
        }
        return split.isMasterSplit() && split.isBaseModuleSplit();
    }

}
