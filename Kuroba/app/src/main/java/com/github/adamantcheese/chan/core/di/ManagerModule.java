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

import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.di.NetModule.OkHttpClientWithUtils;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import org.codejargon.feather.Provides;

import java.io.File;

import javax.inject.Singleton;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;

public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public BoardManager provideBoardManager(BoardRepository boardRepository) {
        Logger.d(AppModule.DI_TAG, "Board manager");
        return new BoardManager(boardRepository);
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(DatabaseFilterManager databaseFilterManager) {
        Logger.d(AppModule.DI_TAG, "Filter engine");
        return new FilterEngine(databaseFilterManager);
    }

    @Provides
    @Singleton
    public ChanLoaderManager provideChanLoaderFactory() {
        Logger.d(AppModule.DI_TAG, "Chan loader factory");
        return new ChanLoaderManager();
    }

    @Provides
    @Singleton
    public WatchManager provideWatchManager(
            DatabasePinManager databasePinManager,
            ChanLoaderManager chanLoaderManager,
            WakeManager wakeManager,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Watch manager");
        return new WatchManager(databasePinManager, chanLoaderManager, wakeManager);
    }

    @Provides
    @Singleton
    public WakeManager provideWakeManager() {
        Logger.d(AppModule.DI_TAG, "Wake manager");
        return new WakeManager();
    }

    @Provides
    @Singleton
    public FilterWatchManager provideFilterWatchManager(
            WakeManager wakeManager,
            BoardRepository boardRepository,
            FilterEngine filterEngine,
            WatchManager watchManager,
            Gson gson,
            ChanLoaderManager chanLoaderManager
    ) {
        Logger.d(AppModule.DI_TAG, "Filter watch manager");
        return new FilterWatchManager(wakeManager,
                boardRepository,
                filterEngine,
                watchManager,
                gson,
                chanLoaderManager
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager() {
        Logger.d(AppModule.DI_TAG, "Archives manager (4chan only)");
        return new ArchivesManager();
    }

    @Provides
    @Singleton
    public ReportManager provideReportManager(Gson gson, OkHttpClientWithUtils clientWithUtils) {
        Logger.d(AppModule.DI_TAG, "Report manager");
        File cacheDir = getCacheDir();

        return new ReportManager(gson, new File(cacheDir, CRASH_LOGS_DIR_NAME), clientWithUtils);
    }
}
