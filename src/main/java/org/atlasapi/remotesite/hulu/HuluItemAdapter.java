/* Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.remotesite.hulu;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.query.uri.canonical.Canonicaliser;
import org.atlasapi.remotesite.HttpClients;
import org.atlasapi.remotesite.SiteSpecificAdapter;
import org.atlasapi.remotesite.html.HtmlNavigator;

import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.http.HttpException;
import com.metabroadcast.common.http.HttpResponse;
import com.metabroadcast.common.http.SimpleHttpClient;

public class HuluItemAdapter implements SiteSpecificAdapter<Episode> {

    static final Log LOG = LogFactory.getLog(HuluItemAdapter.class);

    public static final String BASE_URI = "http://www.hulu.com/watch/";
    private static final Pattern ALIAS_PATTERN = Pattern.compile("(" + BASE_URI + "\\d+)\\/?.*");
    private static final String NOT_FOUND_MESSAGE = "The page you were looking for doesn't exist (404 error)";

    private final SimpleHttpClient httpClient;
    private final HuluItemContentExtractor extractor;
    // If you don't set the brand adapter, it won't try to hydrate them, which
    // is important for stopping everything from spiraling out of control
    private SiteSpecificAdapter<Brand> brandAdapter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ContentWriter contentStore;

    public HuluItemAdapter() {
        this(HttpClients.screenScrapingClient(), new HuluItemContentExtractor());
    }

    public HuluItemAdapter(SimpleHttpClient httpClient, HuluItemContentExtractor extractor) {
        this.httpClient = httpClient;
        this.extractor = extractor;
    }

    @Override
    public Episode fetch(String uri) {
        LOG.info("Retrieving hulu episode: " + uri);
        Maybe<String> content = getContent(uri);
        if (content.hasValue()) {
            HtmlNavigator navigator = new HtmlNavigator(content.requireValue());

            Episode episode = extractor.extract(navigator);

            if (episode.getBrand() != null && episode.getBrand().getCanonicalUri() != null && contentStore != null && brandAdapter != null) {
                executor.execute(new BrandHydratingJob(episode.getBrand().getCanonicalUri()));
            }

            return episode;
        }
        
        return null;
    }

    @Override
    public boolean canFetch(String uri) {
        return ALIAS_PATTERN.matcher(uri).matches();
    }
    
    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }

    class BrandHydratingJob implements Runnable {

        private final String uri;

        public BrandHydratingJob(String uri) {
            this.uri = uri;
        }

        public void run() {
            try {
                Brand brand = brandAdapter.fetch(uri);
                contentStore.createOrUpdatePlaylist(brand, true);
            } catch (Exception e) {
                LOG.warn("Unable to retrieve hulu brand :" + uri + " while hydrating item", e);
            }
        }
    }

    public static class HuluItemCanonicaliser implements Canonicaliser {
        @Override
        public String canonicalise(String uri) {
            Matcher matcher = ALIAS_PATTERN.matcher(uri);
            if (matcher.matches()) {
                return matcher.group(1);
            }
            return null;
        }
    }

    public void setContentStore(ContentWriter contentStore) {
        this.contentStore = contentStore;
    }

    public void setBrandAdapter(SiteSpecificAdapter<Brand> brandAdapter) {
        this.brandAdapter = brandAdapter;
    }

    private Maybe<String> getContent(String uri) {
        for (int i = 0; i < 5; i++) {
            try {
                HttpResponse response = httpClient.get(uri);
                String content = response.body();
                if (response.statusCode() == 200 || (response.wasNotFound() && !content.contains(NOT_FOUND_MESSAGE))) {
                	return Maybe.just(content);
                }
            } catch (HttpException e) {
                LOG.warn("Error retrieving hulu item: " + uri + " attempt " + i + " with message: " + e.getMessage());
            }
        }
        return Maybe.nothing();
    }
}
