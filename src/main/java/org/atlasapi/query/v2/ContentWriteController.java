package org.atlasapi.query.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atlasapi.application.query.ApiKeyNotFoundException;
import org.atlasapi.application.query.ApplicationConfigurationFetcher;
import org.atlasapi.application.query.InvalidIpForApiKeyException;
import org.atlasapi.application.query.RevokedApiKeyException;
import org.atlasapi.application.v3.ApplicationConfiguration;
import org.atlasapi.input.ModelReader;
import org.atlasapi.input.ModelTransformer;
import org.atlasapi.input.ReadException;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Song;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.entity.simple.Description;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.http.HttpStatusCode;

public class ContentWriteController {
    
    //TODO: replace with proper merge strategies.
    private static final boolean MERGE = true;
    private static final boolean OVERWRITE = false;

    private static final Logger log = LoggerFactory.getLogger(ContentWriteController.class);

    private final ApplicationConfigurationFetcher appConfigFetcher;
    private final ContentResolver resolver;
    private final ContentWriter writer;
    private final ModelReader reader;

    private ModelTransformer<Description, Content> transformer;

    public ContentWriteController(ApplicationConfigurationFetcher appConfigFetcher, ContentResolver resolver, ContentWriter writer, ModelReader reader, ModelTransformer<Description, Content> transformer) {
        this.appConfigFetcher = appConfigFetcher;
        this.resolver = resolver;
        this.writer = writer;
        this.reader = reader;
        this.transformer = transformer;
    }
    
    @RequestMapping(value="/3.0/content.json", method = RequestMethod.POST)
    public Void postContent(HttpServletRequest req, HttpServletResponse resp) {
        return deserializeAndUpdateContent(req, resp, MERGE);
    }

    @RequestMapping(value="/3.0/content.json", method = RequestMethod.PUT)
    public Void putContent(HttpServletRequest req, HttpServletResponse resp) {
        return deserializeAndUpdateContent(req, resp, OVERWRITE);
    }

    private Void deserializeAndUpdateContent(HttpServletRequest req, HttpServletResponse resp,
            boolean merge) {
        Maybe<ApplicationConfiguration> possibleConfig;
        try {
            possibleConfig = appConfigFetcher.configurationFor(req);
        } catch (ApiKeyNotFoundException | RevokedApiKeyException | InvalidIpForApiKeyException ex) {
            return error(resp, HttpStatusCode.FORBIDDEN.code());
        }
        
        if (possibleConfig.isNothing()) {
            return error(resp, HttpStatus.UNAUTHORIZED.value());
        }
        
        Content content;
        try {
            content = complexify(deserialize(new InputStreamReader(req.getInputStream())));
        } catch (IOException ioe) {
            log.error("Error reading input for request " + req.getRequestURL(), ioe);
            return error(resp, HttpStatusCode.SERVER_ERROR.code());
        } catch (Exception e) {
            log.error("Error reading input for request " + req.getRequestURL(), e);
            return error(resp, HttpStatusCode.BAD_REQUEST.code());
        }
        
        if (!possibleConfig.requireValue().canWrite(content.getPublisher())) {
            return error(resp, HttpStatusCode.FORBIDDEN.code());
        }
        
        if (Strings.isNullOrEmpty(content.getCanonicalUri())) {
            return error(resp, HttpStatusCode.BAD_REQUEST.code());
        }
        
        try {
            content = merge(resolveExisting(content), content, merge);
            if (content instanceof Item) {
                writer.createOrUpdate((Item) content);
            } else {
                writer.createOrUpdate((Container) content);
            }
        } catch (Exception e) {
            log.error("Error reading input for request " + req.getRequestURL(), e);
            return error(resp, HttpStatusCode.SERVER_ERROR.code());
        }
        
        resp.setStatus(HttpStatusCode.OK.code());
        resp.setContentLength(0);
        return null;
    }
    
    private Content merge(Maybe<Identified> possibleExisting, Content update, boolean merge) {
        if (possibleExisting.isNothing()) {
            return update;
        }
        Identified existing = possibleExisting.requireValue();
        if (existing instanceof Content) {
            return merge((Content) existing, update, merge);
        }
        throw new IllegalStateException("Entity for "+update.getCanonicalUri()+" not Content");
    }

    private Content merge(Content existing, Content update, boolean merge) {
        existing.setEquivalentTo(merge ? merge(existing.getEquivalentTo(), update.getEquivalentTo()) : update.getEquivalentTo());
        existing.setLastUpdated(update.getLastUpdated());
        existing.setTitle(update.getTitle());
        existing.setDescription(update.getDescription());
        existing.setImage(update.getImage());
        existing.setThumbnail(update.getThumbnail());
        existing.setMediaType(update.getMediaType());
        existing.setSpecialization(update.getSpecialization());
        existing.setRelatedLinks(merge ? merge(existing.getRelatedLinks(), update.getRelatedLinks()) : update.getRelatedLinks());
        existing.setTopicRefs(merge ? merge(existing.getTopicRefs(), update.getTopicRefs()) : update.getTopicRefs());
        existing.setPeople(merge ? merge(existing.people(), update.people()) : update.people());
        existing.setKeyPhrases(update.getKeyPhrases());
        existing.setClips(merge ? merge(existing.getClips(), update.getClips()) : update.getClips());

        if (existing instanceof Item && update instanceof Item) {
            return mergeItems((Item)existing, (Item) update);
        }
        return existing;
    }

    private Item mergeItems(Item existing, Item update) {
        if (!update.getVersions().isEmpty()) {
            Version existingVersion = Iterables.getFirst(existing.getVersions(), new Version());
            Version postedVersion = Iterables.getOnlyElement(update.getVersions());
            mergeVersions(existingVersion, postedVersion);
        }
        if (existing instanceof Song && update instanceof Song) {
            return mergeSongs((Song)existing, (Song)update);
        }
        return existing;
    }

    private void mergeVersions(Version existing, Version update) {
        existing.setManifestedAs(update.getManifestedAs());
        existing.setBroadcasts(update.getBroadcasts());
    }

    private Song mergeSongs(Song existing, Song update) {
        existing.setIsrc(update.getIsrc());
        existing.setDuration(update.getDuration());
        return existing;
    }

    private <T> Set<T> merge(Set<T> existing, Set<T> posted) {
        return ImmutableSet.copyOf(Iterables.concat(posted, existing));
    }

    private <T> List<T> merge(List<T> existing, List<T> posted) {
        return ImmutableSet.copyOf(Iterables.concat(posted, existing)).asList();
    }

    private Maybe<Identified> resolveExisting(Content content) {
        ImmutableSet<String> uris = ImmutableSet.of(content.getCanonicalUri());
        ResolvedContent resolved = resolver.findByCanonicalUris(uris);
        return resolved.get(content.getCanonicalUri());
    }

    private Content complexify(Description inputContent) {
        return transformer.transform(inputContent);
    }

    private Description deserialize(Reader input) throws IOException, ReadException {
        return reader.read(new BufferedReader(input), Description.class);
    }
    
    private Void error(HttpServletResponse response, int code) {
        response.setStatus(code);
        response.setContentLength(0);
        return null;
    }
    
}
