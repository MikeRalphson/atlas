/* Copyright 2010 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.remotesite;

import java.util.Collection;
import java.util.List;

import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.system.Fetcher;
import org.atlasapi.remotesite.archiveorg.ArchiveOrgAdapterModule;
import org.atlasapi.remotesite.bbc.BbcPodcastAdapter;
import org.atlasapi.remotesite.bliptv.BlipTvAdapter;
import org.atlasapi.remotesite.dailymotion.DailyMotionItemAdapter;
import org.atlasapi.remotesite.hbo.HboAdapterModule;
import org.atlasapi.remotesite.health.RemoteSiteHealthModule;
import org.atlasapi.remotesite.hulu.HuluAdapterModule;
import org.atlasapi.remotesite.itunes.ItunesAdapterModule;
import org.atlasapi.remotesite.itv.ItvAdapterModule;
import org.atlasapi.remotesite.msnvideo.MsnVideoAdapterModule;
import org.atlasapi.remotesite.oembed.OembedXmlAdapter;
import org.atlasapi.remotesite.synd.OpmlAdapter;
import org.atlasapi.remotesite.ted.TedTalkAdapter;
import org.atlasapi.remotesite.vimeo.VimeoAdapter;
import org.atlasapi.remotesite.youtube.YouTubeAdapterModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.google.common.collect.Lists;

@Configuration
@Import({RemoteSiteHealthModule.class, ItvAdapterModule.class, ArchiveOrgAdapterModule.class, HuluAdapterModule.class, HboAdapterModule.class, ItunesAdapterModule.class, MsnVideoAdapterModule.class, YouTubeAdapterModule.class})
public class RemoteSiteModule {

	private @Autowired AdapterLog log;
	
	private @Autowired Collection<SiteSpecificAdapter<? extends Identified>> remoteAdapters;
	
	public @Bean Fetcher<Identified> remoteFetcher() {
		
		 PerSiteAdapterDispatcher dispatcher = new PerSiteAdapterDispatcher(log);
		 
		 List<SiteSpecificAdapter<? extends Identified>> adapters = Lists.newArrayList();
		 
		 adapters.addAll(remoteAdapters);
		 
		 adapters.add(new TedTalkAdapter());
		 adapters.add(new DailyMotionItemAdapter());
		 adapters.add(new BlipTvAdapter());

		 adapters.add(new BbcPodcastAdapter());
		 
		 adapters.add(new VimeoAdapter());
		 
		 OembedXmlAdapter flickrAdapter = new OembedXmlAdapter();
		 flickrAdapter.setAcceptedUriPattern("http://www.flickr.com/photos/[^/]+/[\\d]+");
		 flickrAdapter.setOembedEndpoint("http://www.flickr.com/services/oembed/");
		 flickrAdapter.setPublisher(Publisher.FLICKR);
		 
		 adapters.add(flickrAdapter);
		 adapters.add(new OpmlAdapter(dispatcher));
		 
		 dispatcher.setAdapters(adapters);
		 return dispatcher;
	}
	
	public @Bean ContentWriters contentWriters() {
		return new ContentWriters();
	}
}
