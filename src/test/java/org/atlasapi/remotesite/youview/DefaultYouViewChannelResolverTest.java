package org.atlasapi.remotesite.youview;

import java.util.Set;

import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.channel.ChannelResolver;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class DefaultYouViewChannelResolverTest {

    private static final Set<String> ALIAS_PREFIXES =
            ImmutableSet.of("http://youview.com/service/");
    
    private static final Channel BBC_ONE = new Channel(
            Publisher.METABROADCAST,
            "BBC One",
            "bbcone",
            null,
            MediaType.VIDEO,
            "http://www.bbc.co.uk/bbcone"
    );
    private static final Channel BBC_TWO = new Channel(
            Publisher.METABROADCAST,
            "BBC Two",
            "bbctwo",
            null,
            MediaType.VIDEO,
            "http://www.bbc.co.uk/bbctwo"
    );

    private final ChannelResolver channelResolver = mock(ChannelResolver.class);
    
    @Test
    public void testResolvesByServiceId() {
        when(channelResolver.forAliases("http://youview.com/service/"))
            .thenReturn(ImmutableMap.of("http://youview.com/service/123", BBC_ONE));
        
        DefaultYouViewChannelResolver yvChannelResolver =
                DefaultYouViewChannelResolver.create(channelResolver, ALIAS_PREFIXES);
        
        assertThat(yvChannelResolver.getChannel(123).get(), is(BBC_ONE));
    }
    
    @Test
    public void testOverrides() {
        when(channelResolver.forAliases("http://youview.com/service/"))
            .thenReturn(ImmutableMap.of("http://youview.com/service/123", BBC_ONE));
        when(channelResolver.forAliases("http://overrides.youview.com/service/"))
            .thenReturn(ImmutableMap.of("http://overrides.youview.com/service/456", BBC_ONE));
    
        
        DefaultYouViewChannelResolver yvChannelResolver =
                DefaultYouViewChannelResolver.create(channelResolver, ALIAS_PREFIXES);
        
        assertThat(yvChannelResolver.getChannel(456).get(), is(BBC_ONE));
        assertFalse("Shouldn't be able to look up by overridden service ID", 
                    yvChannelResolver.getChannel(123).isPresent());
        
        assertThat(yvChannelResolver.getChannelServiceAlias(456), 
                is("http://youview.com/service/456"));
    }
    
    @Test
    public void testOverridesWithoutPrimaryId() {
        when(channelResolver.forAliases("http://overrides.youview.com/service/"))
            .thenReturn(ImmutableMap.of("http://overrides.youview.com/service/456", BBC_ONE));

        DefaultYouViewChannelResolver yvChannelResolver =
                DefaultYouViewChannelResolver.create(channelResolver, ALIAS_PREFIXES);
    
        assertThat(yvChannelResolver.getChannel(456).get(), is(BBC_ONE));
    }

    @Test
    public void overrideAliasTakesPrecedence() {
        when(channelResolver.forAliases("http://youview.com/service/")).thenReturn(
                ImmutableMap.of("http://youview.com/service/123", BBC_ONE));
        when(channelResolver.forAliases("http://overrides.youview.com/service/")).thenReturn(
                ImmutableMap.of("http://overrides.youview.com/service/123", BBC_TWO));


        DefaultYouViewChannelResolver yvChannelResolver =
                DefaultYouViewChannelResolver.create(channelResolver, ALIAS_PREFIXES);

        assertThat(yvChannelResolver.getChannel(123).get(), is(BBC_TWO));
    }

    @Test
    public void gettingMultipleChannelsOnlyResolvesOnceFromStore() {
        when(channelResolver.forAliases("http://youview.com/service/"))
                .thenReturn(ImmutableMap.of(
                        "http://youview.com/service/123", BBC_ONE,
                        "http://youview.com/service/456", BBC_TWO
                ));

        DefaultYouViewChannelResolver yvChannelResolver =
                DefaultYouViewChannelResolver.create(channelResolver, ALIAS_PREFIXES);

        yvChannelResolver.getChannel(123);
        yvChannelResolver.getAllChannels();
        yvChannelResolver.getChannel(456);
        yvChannelResolver.getAllChannels();

        // We access the resolver twice on every single resolution. Once for regular
        // and once for override channels
        verify(channelResolver, times(2)).forAliases(anyString());
    }
}
