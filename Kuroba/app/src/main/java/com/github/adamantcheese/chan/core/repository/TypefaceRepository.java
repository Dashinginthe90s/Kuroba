package com.github.adamantcheese.chan.core.repository;

import android.graphics.Typeface;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class TypefaceRepository {
    private final String TAG = "TypefaceRepository";
    private Map<String, Typeface> typefaceMap = new HashMap<>();
    private Map<String, String> attemptedDownloads = new HashMap<>();

    @Inject
    FileCacheV2 fileCache;
    @Inject
    BoardRepository boardRepository;

    public TypefaceRepository() {
        inject(this);
        //default, system available fonts
        Typeface ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        typefaceMap.put("roboto-medium", ROBOTO_MEDIUM);
        typefaceMap.put("roboto-condensed", ROBOTO_CONDENSED);

        //these fonts will be dynamically downloaded and used; UI refreshes will be needed
        addRepositoryTypeface("Talleyrand.ttf");
        addRepositoryTypeface("OPTICubaLibreTwo.otf");
        addRepositoryTypeface("mona.ttf");
    }

    public Typeface getTypeface(String name) {
        Typeface attempt = typefaceMap.get(name.toLowerCase());
        if (attempt == null) {
            if (attemptedDownloads.get(name) != null) {
                addTypeface(name, attemptedDownloads.get(name));
            }
            return typefaceMap.get("roboto-medium");
        }
        return attempt;
    }

    private void addRepositoryTypeface(final String fontName) {
        attemptedDownloads.put(StringUtils.removeExtensionFromFileName(fontName).toLowerCase(),
                BuildConfig.FONT_ENDPOINT + fontName
        );
    }

    public void addTypeface(final String name, final String url) {
        Logger.d(TAG, "Getting font at " + url);
        attemptedDownloads.put(name, url);
        fileCache.enqueueNormalDownloadFileRequest(url, new FileCacheListener() {
            @Override
            public void onSuccess(RawFile file) {
                synchronized (this) {
                    Logger.d(TAG, "Got font " + name);
                    if (!typefaceMap.containsKey(name)) {
                        Typeface downloaded = Typeface.createFromFile(file.getFullPath());
                        typefaceMap.put(name, downloaded);
                        AndroidUtils.postToEventBus(new RefreshUIMessage("updated fonts"));
                    }
                }
                super.onSuccess(file);
            }

            @Override
            public void onNotFound() {
                Logger.d(TAG, "Unable to find font file " + url);
                super.onNotFound();
            }

            @Override
            public void onFail(Exception exception) {
                Logger.d(TAG, "Unable to download font file " + url);
                Logger.d(TAG, "Font will be roboto-medium");
                super.onFail(exception);
            }
        });
    }
}
