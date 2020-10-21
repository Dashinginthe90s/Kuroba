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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource;
import com.github.adamantcheese.chan.core.net.DnsSelector;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import org.codejargon.feather.Provides;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;

import okhttp3.Dispatcher;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.IPV4_ONLY;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.SYSTEM;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static okhttp3.Protocol.HTTP_1_1;
import static okhttp3.Protocol.HTTP_2;

public class NetModule {
    public static final String USER_AGENT = getApplicationLabel() + "/" + BuildConfig.VERSION_NAME;
    private static final String FILE_CACHE_DIR = "filecache";
    private static final String FILE_CHUNKS_CACHE_DIR = "file_chunks_cache";

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler(FileManager fileManager) {
        Logger.d(AppModule.DI_TAG, "Cache handler");

        File cacheDir = getCacheDir();
        RawFile cacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CACHE_DIR));
        RawFile chunksCacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CHUNKS_CACHE_DIR));

        return new CacheHandler(fileManager, cacheDirFile, chunksCacheDirFile);
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            FileManager fileManager,
            CacheHandler cacheHandler,
            SiteResolver siteResolver,
            OkHttpClientWithUtils okHttpClient
    ) {
        Logger.d(AppModule.DI_TAG, "File cache V2");
        return new FileCacheV2(fileManager, cacheHandler, siteResolver, okHttpClient);
    }

    @Provides
    @Singleton
    public WebmStreamingSource provideWebmStreamingSource(
            FileManager fileManager, FileCacheV2 fileCacheV2, CacheHandler cacheHandler
    ) {
        Logger.d(AppModule.DI_TAG, "WebmStreamingSource");
        return new WebmStreamingSource(fileManager, fileCacheV2, cacheHandler);
    }

    /**
     * This okHttpClient is for posting, as well as images/file/apk updates/ downloading, prefetching, etc.
     */
    @Provides
    @Singleton
    public OkHttpClientWithUtils provideProxiedOkHttpClient() {
        Logger.d(AppModule.DI_TAG, "Proxied OkHTTP client");
        return new OkHttpClientWithUtils(new OkHttpClient.Builder().protocols(getOkHttpProtocols())
                .dns(getOkHttpDnsSelector()));
    }

    /**
     * This okHttpClient is for local threads downloading.
     */
    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClientForThreadSaveManager() {
        Logger.d(AppModule.DI_TAG, "ThreadSaver OkHttp client");

        return new OkHttpClient().newBuilder()
                .connectTimeout(30, SECONDS)
                .writeTimeout(30, SECONDS)
                .readTimeout(30, SECONDS)
                .protocols(getOkHttpProtocols())
                .dns(getOkHttpDnsSelector())
                .build();
    }

    private Dns getOkHttpDnsSelector() {
        DnsSelector selector = new DnsSelector(ChanSettings.okHttpAllowIpv6.get() ? SYSTEM : IPV4_ONLY);
        Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode." + selector.mode.name());
        return selector;
    }

    @NonNull
    private List<Protocol> getOkHttpProtocols() {
        if (ChanSettings.okHttpAllowHttp2.get()) {
            Logger.d(AppModule.DI_TAG, "Using HTTP_2 and HTTP_1_1");
            return Arrays.asList(HTTP_2, HTTP_1_1);
        }

        Logger.d(AppModule.DI_TAG, "Using HTTP_1_1");
        return Collections.singletonList(HTTP_1_1);
    }

    // Basically the same as OkHttpClient, but has extra methods for constructing a proxied client for a specific call
    // and constructing a non-concurrent client for some calls
    public static class OkHttpClientWithUtils
            extends OkHttpClient {
        private Dispatcher bitmapDispatcher = new Dispatcher();

        //This constructs your base client, which is used for pretty much everything
        public OkHttpClientWithUtils(Builder builder) {
            super(builder);
            // Bitmap requests from NetUtils.makeBitmapRequest() should be sequential-ish (ie not concurrent) in order to
            // prevent excessive main thread lag when they are completed and the image is rendered; ie animate calls all
            // run on the main/UI thread and a lot of them running simultaneously cause it to lag immensely. Since this
            // _should_ only be used for thumbnails or other small size images, this is fine as requests will be parsed
            // and completed quickly and the animation should be finished quickly as well.
            // This roughly mirrors how the old Volley implementation worked, with 4 workers by default.
            bitmapDispatcher.setMaxRequests(4);
        }

        //This adds a proxy to the base client
        public OkHttpClient getProxiedClient() {
            return newBuilder().proxy(ChanSettings.getProxy()).build();
        }

        //This changes the dispatcher of the base client to the one with a max of 4 concurrent requests
        public OkHttpClient getBitmapClient() {
            return newBuilder().dispatcher(bitmapDispatcher).build();
        }
    }
}
