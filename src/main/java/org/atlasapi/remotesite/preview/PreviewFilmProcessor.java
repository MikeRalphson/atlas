package org.atlasapi.remotesite.preview;

import java.util.List;
import java.util.Set;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Nodes;

import org.atlasapi.media.TransportSubType;
import org.atlasapi.media.TransportType;
import org.atlasapi.media.entity.Actor;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.ContentWriter;
import org.joda.time.Duration;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.metabroadcast.common.media.MimeType;
import com.metabroadcast.common.text.MoreStrings;

public class PreviewFilmProcessor {
    
    private static final String USER_ID_REPLACEMENT = "%PREVIEW_KEY%";
    
    private final ContentWriter contentWriter;

    public PreviewFilmProcessor(ContentWriter contentWriter) {
        this.contentWriter = contentWriter;
    }
    
    public void process(Element movieElement) {
        
        String id = movieElement.getAttributeValue("movie_id");
        Item item = new Item(getFilmUri(id), getFilmCurie(id), Publisher.PREVIEW_NETWORKS);
        
        String originalTitle = get(movieElement, "original_title");
        Integer duration = getInt(movieElement, "movie_duration");
        
        String website = get(movieElement, "official_website");
        String imdbLink = "http://imdb.com/title/" + movieElement.getAttributeValue("imdb_id");
        item.addAlias(imdbLink);
        if (!Strings.isNullOrEmpty(website)) {
            item.addAlias(website);
        }

        List<CrewMember> crewMembers = getCrewMembers(movieElement);
        item.setPeople(crewMembers);
        
        Element regionElement = movieElement.getFirstChildElement("regions").getFirstChildElement("region");
        
        Set<String> categories = getCategories(regionElement);
        item.setGenres(categories);
        
        setImages(item, regionElement);
        
        Element productElement = regionElement.getFirstChildElement("products").getFirstChildElement("product");
        
        item.setTitle(get(productElement, "product_title"));
        item.setDescription(get(productElement, "description"));
        
        Version version = new Version();
        version.setDuration(Duration.standardMinutes(duration));
        item.addVersion(version);
        
        item.setClips(getClips(productElement, id));
        
        contentWriter.createOrUpdate(item);
    }
    
    private List<Clip> getClips(Element productElement, String movieId) {
        List<Clip> clips = Lists.newArrayList();
        Elements clipElements = productElement.getFirstChildElement("clips").getChildElements("clip");
        
        for (int i = 0; i < clipElements.size(); i++) {
            Element clipElement = clipElements.get(i);
            String id = clipElement.getAttributeValue("clip_id");
            String clipTypeId = clipElement.getAttributeValue("clip_type_id");
            
            Clip clip = new Clip(getClipUri(id), getClipCurie(id), Publisher.PREVIEW_NETWORKS);
            String clipName = clipElement.getAttributeValue("name");
            clip.setTitle(clipName);
            
            Version version = new Version();
            Encoding embedEncoding = new Encoding();
            Location embedLocation = new Location();
            embedLocation.setTransportType(TransportType.EMBED);
            embedLocation.setEmbedCode(getEmbedCode(movieId, clipTypeId, "cinema"));
            embedEncoding.addAvailableAt(embedLocation);
            
            Elements fileElements = clipElement.getFirstChildElement("files").getChildElements("file");
            for (int j = 0; j < fileElements.size(); j++) {
                Element fileElement = fileElements.get(j);
                
                Encoding encoding = new Encoding();
                encoding.setVideoHorizontalSize(getInt(fileElement, "width"));
                String frameRate = fileElement.getAttributeValue("fr");
                if (!Strings.isNullOrEmpty(frameRate)) {
                    encoding.setVideoFrameRate(Float.parseFloat(frameRate));
                }
                String audioBitRate = fileElement.getAttributeValue("abr");
                if (!Strings.isNullOrEmpty(audioBitRate)) {
                    encoding.setAudioBitRate(Integer.parseInt(audioBitRate));
                }
                String videoBitRate = fileElement.getAttributeValue("vbr");
                if (!Strings.isNullOrEmpty(audioBitRate)) {
                    encoding.setAudioBitRate(Integer.parseInt(videoBitRate));
                }
                encoding.setDataSize(Long.parseLong(get(fileElement, "size")));
                setCodecs(encoding, fileElement);
                
                Location downloadLocation = new Location();
                downloadLocation.setUri(getDownloadUri(fileElement));
                downloadLocation.setTransportType(TransportType.DOWNLOAD);
                downloadLocation.setTransportSubType(TransportSubType.HTTP);
                encoding.addAvailableAt(downloadLocation);
                version.addManifestedAs(encoding);
            }
            
            clips.add(clip);
        }
        return clips;
    }
    
    private String getDownloadUri(Element fileElement) {
        String fullUri = get(fileElement, "url");
        
        return fullUri.substring(0, fullUri.indexOf("?"));
    }
    
    private void setCodecs(Encoding encoding, Element fileElement) {
        String format = fileElement.getAttributeValue("format");
        if (format.equalsIgnoreCase("mp4")) {
            encoding.setVideoCoding(MimeType.VIDEO_H264);
            encoding.setAudioCoding(MimeType.AUDIO_AAC);
        }
        else if (format.equalsIgnoreCase("flv")) {
            encoding.setVideoCoding(MimeType.VIDEO_H264);
            encoding.setAudioCoding(MimeType.AUDIO_MP3);
        }
        else if (format.equalsIgnoreCase("wmv")) {
            encoding.setVideoCoding(MimeType.VIDEO_XMSWMV);
            encoding.setAudioCoding(MimeType.AUDIO_XMSWMA);
        }
    }
    
    private String getEmbedCode(String movieId, String clipType, String mediaType) {
        return "<object width=\"600\" height=\"338\" classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" id=\"pnplayer\">" +
        		"<param name=\"movie\" value=\"http://www.player.previewnetworks.com/v4.0/PNPlayer.swf?\"/>" +
        		"<param name=\"FlashVars\" value=\"file=http://uk.player-feed.previewnetworks.com/v3.1/cinema/MovieID/Channel_User_ID/?clip_type=X\" />" +
        		"<param name=\"allowfullscreen\" value=\"true\"/><param name=\"allowscriptaccess\" value=\"always\"/>" +
        		"<embed type=\"application/x-shockwave-flash\" src=\"http://www.player.previewnetworks.com/v4.0/PNPlayer.swf?\" FlashVars=\"file=http://uk.player-feed.previewnetworks.com/v3.1/" + mediaType + "/" + movieId + "/" + USER_ID_REPLACEMENT + "/?clip_type=" + clipType + "\" allowfullscreen=\"true\" allowscriptaccess=\"always\" width=\"600\" height=\"338\">" +
        		"</embed></object>";
    }
    
    private void setImages(Item item, Element regionElement) {
        Nodes nodes = regionElement.getFirstChildElement("pictures").query("picture[@type_name='poster']/url");
        
        if (nodes.size() > 0) {
            item.setThumbnail(nodes.get(0).getValue());
        }
        
        nodes = regionElement.getFirstChildElement("pictures").query("picture[@type_name='poster_large']/url");
        
        if (nodes.size() > 0) {
            item.setImage(nodes.get(0).getValue());
        }
    }
    
    private String getFilmCurie(String id) {
        return "pn:f-" + id;
    }
    
    private String getFilmUri(String id) {
        return "http://previewnetworks.com/film/" + id;
    }
    
    private String getClipCurie(String id) {
        return "pn:c-" + id;
    }
    
    private String getClipUri(String id) {
        return "http://previewnetworks.com/clip/" + id;
    }
    
    private Set<String> getCategories(Element regionElement) {
        Set<String> categories = Sets.newHashSet();
        Elements categoryElements = regionElement.getFirstChildElement("categories").getChildElements("categorie");
        
        for (int i = 0; i < categoryElements.size(); i++) {
            categories.add("http://previewnetworks.com/categories/" + categoryElements.get(i).getValue().toLowerCase());
        }
        
        return categories;
    }
    
    private List<CrewMember> getCrewMembers(Element movieElement) {
        List<CrewMember> people = Lists.newArrayList();
        
        Elements actorElements = movieElement.getFirstChildElement("actors").getChildElements("actor");
        for (int i = 0; i < actorElements.size(); i++) {
            people.add(Actor.actor(actorElements.get(i).getValue(), null, Publisher.PREVIEW_NETWORKS));
        }
        
        Elements directorElements = movieElement.getFirstChildElement("directors").getChildElements("director");
        for (int i = 0; i < directorElements.size(); i++) {
            people.add(CrewMember.crewMember(directorElements.get(i).getValue(), "director", Publisher.PREVIEW_NETWORKS));
        }
        
        Elements writerElements = movieElement.getFirstChildElement("writers").getChildElements("writer");
        for (int i = 0; i < writerElements.size(); i++) {
            people.add(CrewMember.crewMember(writerElements.get(i).getValue(), "writer", Publisher.PREVIEW_NETWORKS));
        }
        
        return people;
    }

    private String get(Element element, String childName) {
        Element childElement = element.getFirstChildElement(childName);
        if (childElement != null) {
            if (!Strings.isNullOrEmpty(childElement.getValue())) {
                return childElement.getValue();
            }
        }
        
        return null;
    }
    
    private Integer getInt(Element element, String childName) {
        String stringValue = get(element, childName);
        
        if (stringValue != null && MoreStrings.containsOnlyAsciiDigits(stringValue)) {
            return Integer.parseInt(stringValue);
        }
        
        return null;
    }
}
