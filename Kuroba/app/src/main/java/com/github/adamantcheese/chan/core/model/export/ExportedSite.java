/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.model.export;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ExportedSite {
    @SerializedName("site_id")
    private final int siteId;
    @SerializedName("configuration")
    @Nullable
    private final String configuration;
    @SerializedName("order")
    private final int order;
    @SerializedName("user_settings")
    @NonNull
    private String userSettings;
    @SerializedName("exported_pins")
    private final List<ExportedPin> exportedPins;
    @SerializedName("class_id")
    private int classId;

    public ExportedSite(
            int siteId,
            @NonNull String configuration,
            int order,
            @NonNull String userSettings,
            int classId,
            List<ExportedPin> exportedPins
    ) {
        this.siteId = siteId;
        this.configuration = configuration;
        this.order = order;
        this.userSettings = userSettings;
        this.classId = classId;
        this.exportedPins = exportedPins;
    }

    public int getSiteId() {
        return siteId;
    }

    @Nullable
    public String getConfiguration() {
        return configuration;
    }

    public int getOrder() {
        return order;
    }

    @NonNull
    public String getUserSettings() {
        return userSettings;
    }

    public int getClassId() {
        return classId;
    }

    public void setClassId(int classId) {
        this.classId = classId;
    }

    public void setUserSettings(String settingsJson) {
        userSettings = settingsJson;
    }

    public List<ExportedPin> getExportedPins() {
        return exportedPins;
    }
}
