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

package org.uriplay.remotesite.hulu;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.uriplay.media.entity.Brand;
import org.uriplay.media.entity.Episode;
import org.uriplay.media.entity.Item;
import org.uriplay.persistence.system.RequestTimer;
import org.uriplay.query.uri.canonical.Canonicaliser;
import org.uriplay.remotesite.ContentExtractor;
import org.uriplay.remotesite.FetchException;
import org.uriplay.remotesite.HttpClients;
import org.uriplay.remotesite.SiteSpecificAdapter;
import org.uriplay.remotesite.html.HtmlNavigator;

import com.google.soy.common.collect.Lists;
import com.metabroadcast.common.http.HttpException;
import com.metabroadcast.common.http.SimpleHttpClient;

public class HuluBrandAdapter implements SiteSpecificAdapter<Brand> {

    public static final String BASE_URI = "http://www.hulu.com/";
    private static final Pattern ALIAS_PATTERN = Pattern.compile("(" + BASE_URI + "[a-z\\-]+).*");
    private final SimpleHttpClient httpClient;
    private final ContentExtractor<HtmlNavigator, Brand> extractor;
    private SiteSpecificAdapter<Episode> episodeAdapter;

    public HuluBrandAdapter() {
        this(HttpClients.webserviceClient(), new HuluBrandContentExtractor());
    }

    public HuluBrandAdapter(SimpleHttpClient httpClient, ContentExtractor<HtmlNavigator, Brand> extractor) {
        this.httpClient = httpClient;
        this.extractor = extractor;
    }

    @Override
    public Brand fetch(String uri, RequestTimer timer) {
        try {
            String content = httpClient.get(uri);
            HtmlNavigator navigator = new HtmlNavigator(content);

            Brand brand = extractor.extract(navigator);
            List<Item> episodes = Lists.newArrayList();

            if (episodeAdapter != null) {
                for (Item item : brand.getItems()) {
                    Episode episode = episodeAdapter.fetch(item.getCanonicalUri(), null);
                    episodes.add(episode);
                }
                brand.setItems(episodes);
            }

            return brand;
        } catch (HttpException e) {
            throw new FetchException("Unable to retrieve from Hulu", e);
        }
    }

    @Override
    public boolean canFetch(String uri) {
        return Pattern.compile(BASE_URI + "[a-z\\-]+").matcher(uri).matches();
    }

    public static class HuluBrandCanonicaliser implements Canonicaliser {
        @Override
        public String canonicalise(String uri) {
            Matcher matcher = ALIAS_PATTERN.matcher(uri);
            if (matcher.matches()) {
                String canonical = matcher.group(1);
                if (!canonical.endsWith("/watch") && !canonical.endsWith("/feeds")) {
                    return canonical;
                }
            }
            return null;
        }
    }

    public void setEpisodeAdapter(SiteSpecificAdapter<Episode> episodeAdapter) {
        this.episodeAdapter = episodeAdapter;
    }
}
