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
package com.github.adamantcheese.chan.core.manager;

import android.content.res.AssetManager;
import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.FoolFuukaArchive;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class ArchivesManager
        implements NetUtils.JsonResult<List<ExternalSiteArchive>>, NetUtils.JsonParser<List<ExternalSiteArchive>> {
    private List<ExternalSiteArchive> archivesList;

    private Map<String, Class<? extends ExternalSiteArchive>> jsonMapping = new HashMap<>();

    public ArchivesManager() {
        // setup mappings (nothing for fuuka, doesn't have an API)
        jsonMapping.put("foolfuuka", FoolFuukaArchive.class);
        jsonMapping.put("fuuka", null);

        //setup the archives list from the internal file, populated when you build the application
        AssetManager assetManager = getAppContext().getAssets();
        try {
            // archives.json should only contain FoolFuuka archives, as no other proper archiving software with an API seems to exist
            try (JsonReader reader = new JsonReader(new InputStreamReader(assetManager.open("archives.json")))) {
                archivesList = parse(reader);
            }
        } catch (Exception e) {
            Logger.d(this, "Unable to load/parse internal archives list");
        }

        // fresh copy request, in case of updates
        NetUtils.makeJsonRequest(HttpUrl.get("https://4chenz.github.io/archives.json/archives.json"), this, this);
    }

    public List<ExternalSiteArchive> archivesForBoard(Board b) {
        List<ExternalSiteArchive> result = new ArrayList<>();
        if (archivesList == null || !(b.site instanceof Chan4)) return result; //4chan only
        for (ExternalSiteArchive a : archivesList) {
            for (String code : a.boardCodes) {
                if (code.equals(b.code)) {
                    result.add(a);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<ExternalSiteArchive> parse(JsonReader reader)
            throws Exception {
        List<ExternalSiteArchive> archives = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            String name = "";
            String domain = "";
            String software = "";
            List<String> boardCodes = Collections.emptyList();
            boolean search = false;
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "name":
                        name = reader.nextString();
                        break;
                    case "domain":
                        domain = reader.nextString();
                        break;
                    case "software":
                        software = reader.nextString();
                        break;
                    case "boards":
                        boardCodes = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            boardCodes.add(reader.nextString());
                        }
                        reader.endArray();
                        break;
                    case "search":
                        search = true;
                        reader.skipValue();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            Class archiveClass = jsonMapping.get(software);
            if (archiveClass != null) {
                archives.add((ExternalSiteArchive) archiveClass.getConstructor(String.class,
                        String.class,
                        List.class,
                        boolean.class
                )
                        .newInstance(domain, name, boardCodes, search));
            }
        }
        reader.endArray();
        return archives;
    }

    @Override
    public void onJsonFailure(Exception e) {}

    @Override
    public void onJsonSuccess(List<ExternalSiteArchive> result) {
        archivesList = result;
    }
}
