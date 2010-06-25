/* Copyright 2009 British Broadcasting Corporation
   Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.uriplay.remotesite.wikipedia;

import static org.uriplay.media.vocabulary.DBPO.FILM;
import static org.uriplay.media.vocabulary.DBPO.PERSON;
import static org.uriplay.media.vocabulary.DBPO.TELEVISION_EPISODE;
import static org.uriplay.media.vocabulary.DBPO.TELEVISION_SHOW;
import static org.uriplay.remotesite.wikipedia.WikipediaSparqlSource.CONTAINED_IN_ID;
import static org.uriplay.remotesite.wikipedia.WikipediaSparqlSource.DESCRIPTION_ID;
import static org.uriplay.remotesite.wikipedia.WikipediaSparqlSource.ITEM_ID;
import static org.uriplay.remotesite.wikipedia.WikipediaSparqlSource.SAMEAS_ID;
import static org.uriplay.remotesite.wikipedia.WikipediaSparqlSource.TITLE_ID;

import java.util.Set;

import org.uriplay.media.entity.Brand;
import org.uriplay.media.entity.Description;
import org.uriplay.media.entity.Episode;
import org.uriplay.media.entity.Item;
import org.uriplay.media.entity.Playlist;
import org.uriplay.remotesite.ContentExtractor;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.core.ResultBinding;

/**
 * {@link BeanGraphExtractor} that processes a source obtained by performing
 * Sparql queries against Dbpedia and produces an appropriate
 * {@link Representation}.
 * 
 * @author Robert Chatley (robert@metabroadcast.com)
 * @author Lee Denison (lee@metabroadcast.com)
 */
public class WikipediaSparqlGraphExtractor implements ContentExtractor<WikipediaSparqlSource, Description> {

	public Description extract(WikipediaSparqlSource source) {

		String rootUri = source.getCanonicalWikipediaUri();
		
		Description rootDescription = rootDescription(source);
		if (rootDescription == null) {
			return null;
		}
		rootDescription.setCanonicalUri(rootUri);

		ResultSet rootProperties = source.getRootProperties();
		while (rootProperties.hasNext()) {
			ResultBinding resultBinding = (ResultBinding) rootProperties.next();
			setBeanProperties(resultBinding, rootDescription);
		}

		ResultSet childProperties = source.getChildProperties();
		if (childProperties != null) {
			
			ResultSet childTypeProperties = source.getChildTypeProperties();
			while (childTypeProperties.hasNext()) {
				ResultBinding resultBinding = (ResultBinding) childTypeProperties.next();

				Description child = beanFor(resultBinding, source);
				
				if (child != null) {
					child.setCanonicalUri(resultBinding.getResource(ITEM_ID).getURI());
					setBeanProperties(resultBinding, child);
					if (child instanceof Item) {
						((Playlist) rootDescription).addItem((Item) child);
					} else if (child instanceof Playlist) {
						((Playlist) rootDescription).addPlaylist((Playlist) child);
					}
				}
			}
		}
		if (rootDescription instanceof Item) {
			((Item) rootDescription).setContainedInUris(containedInUris(source));
		} 
		if (rootDescription instanceof Playlist) {
			((Playlist) rootDescription).setContainedInUris(containedInUris(source));
		}
		rootDescription.addAlias(source.getCanonicalDbpediaUri());
		return rootDescription;
	}

	private Set<String> containedInUris(WikipediaSparqlSource source) {
		ResultSet containedInProperties = source.getContainedInProperties();
		Set<String> containedIn = Sets.newHashSet();
		if (containedInProperties != null) {
			while (containedInProperties.hasNext()) {
				ResultBinding resultBinding = (ResultBinding) containedInProperties.next();
				String containedInUri = resultBinding.getResource(CONTAINED_IN_ID).getURI();
				containedIn.add(containedInUri);
			}
			return containedIn;
		}
		return Sets.newHashSet();
	}

	private Description rootDescription(WikipediaSparqlSource source) {
		Set<String> rootTypes = source.getRootTypes();
		for (String type : rootTypes) {
			Description description = beanFor(type);
			if (description != null) {
				return description;
			}
		}
		return null;
	}

	private void setBeanProperties(ResultBinding resultBinding, Description root) {

		Literal title = resultBinding.getLiteral(TITLE_ID);
		if (title != null) {
			if (root instanceof Playlist) {
				((Playlist) root).setTitle(title.getValue().toString());
			} 
			if (root instanceof Item) {
				((Item) root).setTitle(title.getValue().toString());
			}
		}

		Literal description = resultBinding.getLiteral(DESCRIPTION_ID);
		if (description != null) {
			if (root instanceof Playlist) {
				((Playlist) root).setDescription(description.getValue().toString());
			} 
			if (root instanceof Item) {
				((Item) root).setDescription(description.getValue().toString());
			}
		}
		
		Resource sameAs = resultBinding.getResource(SAMEAS_ID);
		if (sameAs != null) {
			root.addAlias(sameAs.getURI());
		}
	}

	private Description beanFor(ResultBinding resultBinding,WikipediaSparqlSource source) {
		String articleType = source.determineItemType(resultBinding);
		return beanFor(articleType);
	}
	
	private Description beanFor(String articleType) {

		if (TELEVISION_EPISODE.equals(articleType)) {
			return new Episode();
		} else if (TELEVISION_SHOW.equals(articleType)) {
			return new Brand();
		} else if (PERSON.equals(articleType)) {
			return new Playlist();
		} else if (FILM.equals(articleType)) {
			return new Item();
		}
		return null;
	}
}
