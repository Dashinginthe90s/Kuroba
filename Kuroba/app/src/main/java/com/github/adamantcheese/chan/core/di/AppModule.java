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
package com.github.adamantcheese.chan.core.di;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.database.DatabaseSiteManager;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.adamantcheese.chan.ui.captcha.CaptchaHolder;
import com.github.adamantcheese.chan.ui.settings.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager;
import com.google.gson.Gson;

import org.codejargon.feather.Provides;

import java.io.File;

import javax.inject.Singleton;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getMaxScreenSize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getMinScreenSize;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ReplaceBadSymbols;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ThrowAnException;

public class AppModule {
    public static final String DI_TAG = "Dependency Injection";

    @Provides
    @Singleton
    public DatabaseHelper provideDatabaseHelper() {
        Logger.d(AppModule.DI_TAG, "Database helper");
        return new DatabaseHelper();
    }

    @Provides
    @Singleton
    public DatabaseLoadableManager provideDatabaseLoadableManager(
            DatabaseHelper helper, SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Database loadable manager");
        return new DatabaseLoadableManager(helper, siteRepository);
    }

    @Provides
    @Singleton
    public DatabasePinManager provideDatabasePinManager(
            DatabaseHelper helper, DatabaseLoadableManager databaseLoadableManager
    ) {
        Logger.d(AppModule.DI_TAG, "Database pin manager");
        return new DatabasePinManager(helper, databaseLoadableManager);
    }

    @Provides
    @Singleton
    public DatabaseSavedReplyManager provideDatabaseSavedReplyManager(DatabaseHelper helper) {
        Logger.d(AppModule.DI_TAG, "Database saved reply manager");
        return new DatabaseSavedReplyManager(helper);
    }

    @Provides
    @Singleton
    public DatabaseFilterManager provideDatabaseFilterManager(DatabaseHelper helper) {
        Logger.d(AppModule.DI_TAG, "Database filter manager");
        return new DatabaseFilterManager(helper);
    }

    @Provides
    @Singleton
    public DatabaseBoardManager provideDatabaseBoardManager(DatabaseHelper helper) {
        Logger.d(AppModule.DI_TAG, "Database board manager");
        return new DatabaseBoardManager(helper);
    }

    @Provides
    @Singleton
    public DatabaseSiteManager provideDatabaseSiteManager(DatabaseHelper helper) {
        Logger.d(AppModule.DI_TAG, "Database site manager");
        return new DatabaseSiteManager(helper);
    }

    @Provides
    @Singleton
    public DatabaseHideManager provideDatabaseHideManager(DatabaseHelper helper) {
        Logger.d(AppModule.DI_TAG, "Database hide manager");
        return new DatabaseHideManager(helper);
    }

    @Provides
    @Singleton
    public SiteResolver provideSiteResolver(SiteRepository siteRepository) {
        Logger.d(AppModule.DI_TAG, "Site resolver");
        return new SiteResolver(siteRepository);
    }

    @Provides
    @Singleton
    public ThemeHelper provideThemeHelper() {
        Logger.d(DI_TAG, "Theme helper");
        return new ThemeHelper();
    }

    @Provides
    @Singleton
    public ImageSaver provideImageSaver(FileManager fileManager) {
        Logger.d(DI_TAG, "Image saver");
        return new ImageSaver(fileManager);
    }

    @Provides
    @Singleton
    public CaptchaHolder provideCaptchaHolder() {
        Logger.d(DI_TAG, "Captcha holder");
        return new CaptchaHolder();
    }

    @Provides
    @Singleton
    public Gson provideGson() {
        Logger.d(AppModule.DI_TAG, "Gson module");
        return new Gson();
    }

    @Provides
    @Singleton
    public FileManager provideFileManager() {
        DirectoryManager directoryManager = new DirectoryManager(getAppContext());

        BadPathSymbolResolutionStrategy resolutionStrategy =
                BuildConfig.DEV_BUILD ? ThrowAnException : ReplaceBadSymbols;

        FileManager fileManager = new FileManager(getAppContext(), resolutionStrategy, directoryManager);
        fileManager.registerBaseDir(SavedFilesBaseDirectory.class, new SavedFilesBaseDirectory());

        return fileManager;
    }

    @Provides
    @Singleton
    public FileChooser provideFileChooser() {
        return new FileChooser(getAppContext());
    }

    public static File getCacheDir() {
        //TODO maybe it's best to just return the internal cache dir

        // See also res/xml/filepaths.xml for the fileprovider.
        if (getAppContext().getExternalCacheDir() != null) {
            return getAppContext().getExternalCacheDir();
        } else {
            return getAppContext().getCacheDir();
        }
    }

    @Provides
    @Singleton
    public Android10GesturesExclusionZonesHolder provideAndroid10GesturesHolder(Gson gson) {
        Logger.d(DI_TAG, "Android10GesturesExclusionZonesHolder");

        return new Android10GesturesExclusionZonesHolder(gson, getMinScreenSize(), getMaxScreenSize());
    }
}
