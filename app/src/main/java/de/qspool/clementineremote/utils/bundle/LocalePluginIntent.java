/*
 * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package de.qspool.clementineremote.utils.bundle;

/**
 * Intent contract of the Locale/Tasker plug-in API.
 *
 * Vendored from com.twofortyfouram:android-plugin-api-for-locale, which was only
 * ever published to JCenter.
 */
public final class LocalePluginIntent {

    public static final String ACTION_FIRE_SETTING =
            "com.twofortyfouram.locale.intent.action.FIRE_SETTING";

    public static final String EXTRA_BUNDLE =
            "com.twofortyfouram.locale.intent.extra.BUNDLE";

    public static final String EXTRA_STRING_BLURB =
            "com.twofortyfouram.locale.intent.extra.BLURB";

    private LocalePluginIntent() {
    }
}
