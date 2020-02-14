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

import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.repository.TypefaceRepository;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class RepositoryModule {

    @Provides
    @Singleton
    public ImportExportRepository provideImportExportRepository(
            DatabaseManager databaseManager, DatabaseHelper databaseHelper, Gson gson, FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Import export repository");
        return new ImportExportRepository(databaseManager, databaseHelper, gson, fileManager);
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(DatabaseManager databaseManager) {
        Logger.d(AppModule.DI_TAG, "Site repository");
        return new SiteRepository(databaseManager);
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(DatabaseManager databaseManager, SiteRepository siteRepository) {
        Logger.d(AppModule.DI_TAG, "Board repository");
        return new BoardRepository(databaseManager, siteRepository);
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository() {
        Logger.d(AppModule.DI_TAG, "Last reply repository");
        return new LastReplyRepository();
    }

    @Provides
    @Singleton
    public SavedThreadLoaderRepository provideSavedThreadLoaderRepository(Gson gson, FileManager fileManager) {
        Logger.d(AppModule.DI_TAG, "Saved thread loader repository");
        return new SavedThreadLoaderRepository(gson, fileManager);
    }

    @Provides
    @Singleton
    public TypefaceRepository provideTypefaceRepository() {
        Logger.d(AppModule.DI_TAG, "Typeface repository");
        return new TypefaceRepository();
    }
}
