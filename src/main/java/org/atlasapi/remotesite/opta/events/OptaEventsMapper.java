package org.atlasapi.remotesite.opta.events;

import java.util.Map;
import java.util.Set;

import org.atlasapi.remotesite.events.EventsFieldMapper;
import org.atlasapi.remotesite.opta.events.model.OptaSportType;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;


public class OptaEventsMapper implements EventsFieldMapper<OptaSportType> {
    
    private static final Map<OptaSportType, Duration> DURATION_MAPPING = ImmutableMap.of(
            OptaSportType.RUGBY, Duration.standardMinutes(100),
            OptaSportType.FOOTBALL_GERMAN_BUNDESLIGA, Duration.standardMinutes(110),
            OptaSportType.FOOTBALL_SCOTTISH_PREMIER_LEAGUE, Duration.standardMinutes(110),
            OptaSportType.FOOTBALL_PREMIER_LEAGUE, Duration.standardMinutes(110)
    );
    private static final Map<OptaSportType, DateTimeZone> TIMEZONE_MAPPING = ImmutableMap.of(
            OptaSportType.RUGBY, DateTimeZone.forID("Europe/London"),
            OptaSportType.FOOTBALL_GERMAN_BUNDESLIGA, DateTimeZone.forID("Europe/Berlin"),
            OptaSportType.FOOTBALL_SCOTTISH_PREMIER_LEAGUE, DateTimeZone.forID("Europe/London"),
            OptaSportType.FOOTBALL_PREMIER_LEAGUE, DateTimeZone.forID("Europe/London")
    );
    private static final Map<String, String> VENUE_LOOKUP = ImmutableMap.<String, String>builder()
            .put("Recreation Ground", "http://dbpedia.org/resources/Recreation_Ground_(Bath)")
            .put("Adams Park", "http://dbpedia.org/resources/Adams_Park")
            .put("stadium:mk", "http://dbpedia.org/resources/Stadium:mk")
            .put("The Stoop", "http://dbpedia.org/resources/The_Stoop")
            .put("Wembley Stadium", "http://dbpedia.org/resources/Wembley_stadium")
            .put("Welford Road", "http://dbpedia.org/resources/Welford_Road_Stadium")
            .put("Twickenham", "http://dbpedia.org/resources/Twickenham_Stadium")
            .put("Kassam Stadium", "http://dbpedia.org/resources/Kassam_Stadium")
            .put("Kingston Park", "http://dbpedia.org/resources/Kingston_Park_(stadium)")
            .put("Allianz Park", "http://dbpedia.org/resources/Barnet_Copthall")
            .put("Kingsholm", "http://dbpedia.org/resources/Kingsholm_Stadium")
            .put("Madejski Stadium", "http://dbpedia.org/resources/Madejski_Stadium")
            .put("AJ Bell Stadium", "http://dbpedia.org/resources/Salford_City_Stadium")
            .put("Sandy Park", "http://dbpedia.org/resources/Sandy_Park")
            .put("Franklin's Gardens", "http://dbpedia.org/resources/Franklin%27s_Gardens")
            .put("Allianz Arena", "http://dbpedia.org/resources/Allianz_Arena")
            .put("BayArena", "http://dbpedia.org/resources/BayArena")
            .put("Benteler-Arena", "http://dbpedia.org/resources/Benteler_Arena")
            .put("Borussia-Park", "http://dbpedia.org/resources/Borussia-Park")
            .put("Celtic Park", "http://dbpedia.org/resources/Celtic_Park")
            .put("Coface Arena", "http://dbpedia.org/resources/Coface_Arena")
            .put("Commerzbank Arena", "http://dbpedia.org/resources/Commerzbank-Arena")
            .put("Dens Park", "http://dbpedia.org/resources/Dens_Park")
            .put("Fir Park", "http://dbpedia.org/resources/Fir_Park")
            .put("Firhill Stadium", "http://dbpedia.org/resources/Firhill_Stadium")
            .put("HDI-Arena", "http://dbpedia.org/resources/HDI-Arena")
            .put("Imtech Arena", "http://dbpedia.org/resources/Volksparkstadion")
            .put("MAGE SOLAR Stadion", "http://dbpedia.org/resources/Mage_Solar_Stadion")
            .put("McDiarmid Park", "http://dbpedia.org/resources/McDiarmid_Park")
            .put("Mercedes-Benz Arena", "http://dbpedia.org/resources/Mercedes-Benz_Arena_(Stuttgart)")
            .put("New Douglas Park", "http://dbpedia.org/resources/New_Douglas_Park")
            .put("Olympiastadion", "http://dbpedia.org/resources/Olympic_Stadium_(Berlin)")
            .put("Pittodrie", "http://dbpedia.org/resources/Pittodrie_Stadium")
            .put("RheinEnergieStadion", "http://dbpedia.org/resources/RheinEnergieStadion")
            .put("Rugby Park", "http://dbpedia.org/resources/Rugby_Park")
            .put("SGL Arena", "http://dbpedia.org/resources/SGL_arena")
            .put("Signal Iduna Park", "http://dbpedia.org/resources/Signal_Iduna_Park")
            .put("St Mirren Park", "http://dbpedia.org/resources/St._Mirren_Park")
            .put("Tannadice Park", "http://dbpedia.org/resources/Tannadice_Park")
            .put("Tulloch Caledonian Stadium", "http://dbpedia.org/resources/Caledonian_Stadium")
            .put("VELTINS-Arena", "http://dbpedia.org/resources/Veltins-Arena")
            .put("Victoria Park, Dingwall", "http://dbpedia.org/resources/Victoria_Park,_Dingwall")
            .put("Volkswagen Arena", "http://dbpedia.org/resources/Volkswagen_Arena")
            .put("WIRSOL Rhein-Neckar-Arena", "http://dbpedia.org/resources/Rhein-Neckar_Arena")
            .put("Weserstadion", "http://dbpedia.org/resources/Weserstadion")
            .put("Anfield", "http://dbpedia.org/resources/Anfield") 
            .put("Boleyn Ground", "http://dbpedia.org/resources/Boleyn_Ground") 
            .put("Britannia Stadium", "http://dbpedia.org/resources/Britannia_Stadium") 
            .put("Emirates Stadium", "http://dbpedia.org/resources/Emirates_Stadium") 
            .put("Etihad Stadium", "http://dbpedia.org/resources/City_of_Manchester_Stadium") 
            .put("Goodison Park", "http://dbpedia.org/resources/Goodison_Park") 
            .put("King Power Stadium", "http://dbpedia.org/resources/King_Power_Stadium") 
            .put("Liberty Stadium", "http://dbpedia.org/resources/Liberty_Stadium") 
            .put("Loftus Road Stadium", "http://dbpedia.org/resources/Loftus_Road") 
            .put("Old Trafford", "http://dbpedia.org/resources/Old_Trafford") 
            .put("Selhurst Park", "http://dbpedia.org/resources/Selhurst_Park") 
            .put("St. James' Park", "http://dbpedia.org/resources/St_James%27_Park") 
            .put("St. Mary's Stadium", "http://dbpedia.org/resources/St_Mary%27s_Stadium") 
            .put("Stadium of Light", "http://dbpedia.org/resources/Stadium_of_Light") 
            .put("Stamford Bridge", "http://dbpedia.org/resources/Stamford_Bridge_(stadium)") 
            .put("The Hawthorns", "http://dbpedia.org/resources/The_Hawthorns") 
            .put("The KC Stadium", "http://dbpedia.org/resources/KC_Stadium") 
            .put("Turf Moor", "http://dbpedia.org/resources/Turf_Moor") 
            .put("Villa Park", "http://dbpedia.org/resources/Villa_Park") 
            .put("White Hart Lane", "http://dbpedia.org/resources/White_Hart_Lane") 
            .build();
    private static final Map<OptaSportType, Map<String, String>> EVENT_GROUPS_LOOKUP = ImmutableMap.<OptaSportType, Map<String, String>>builder()
            .put(OptaSportType.RUGBY, ImmutableMap.of(
                    "English Premiership (rugby union)", "http://dbpedia.org/resources/English_Premiership_(rugby_union)", 
                    "Rugby Football", "http://dbpedia.org/resources/Rugby_football"
            ))
            .put(OptaSportType.FOOTBALL_SCOTTISH_PREMIER_LEAGUE, ImmutableMap.of(
                    "Football", "http://dbpedia.org/resources/Football", 
                    "Association Football", "http://dbpedia.org/resources/Association_football", 
                    "Scottish Premier League", "http://dbpedia.org/resources/Scottish_Premier_League"
            ))
            .put(OptaSportType.FOOTBALL_GERMAN_BUNDESLIGA, ImmutableMap.of(
                    "Football", "http://dbpedia.org/resources/Football", 
                    "Association Football", "http://dbpedia.org/resources/Association_football", 
                    "German Bundesliga", "http://dbpedia.org/resources/German_Bundesliga"
            ))
            .put(OptaSportType.FOOTBALL_PREMIER_LEAGUE, ImmutableMap.of(
                    "Football", "http://dbpedia.org/resources/Football", 
                    "Association Football", "http://dbpedia.org/resources/Association_football", 
                    "Premier League", "http://dbpedia.org/resources/Premier_League"
            ))
            .build();
    private static final Map<OptaSportType, Set<String>> IGNORED_LOCATIONS_LOOKUP = ImmutableMap.<OptaSportType, Set<String>>builder()
            .put(OptaSportType.RUGBY, ImmutableSet.<String>of())
            .put(OptaSportType.FOOTBALL_GERMAN_BUNDESLIGA, ImmutableSet.<String>of())
            .put(OptaSportType.FOOTBALL_PREMIER_LEAGUE, ImmutableSet.<String>of())
            .put(OptaSportType.FOOTBALL_SCOTTISH_PREMIER_LEAGUE, ImmutableSet.<String>of())
            .build();
    private static final Set<String> IGNORED_TEAM_NAMES = ImmutableSet.of(
            "TBC"
    );
    
    public OptaEventsMapper() {  }

    @Override
    public Duration fetchDuration(OptaSportType sport) {
        return DURATION_MAPPING.get(sport);
    }

    @Override
    public Optional<String> fetchLocationUrl(String location) {
        return Optional.fromNullable(VENUE_LOOKUP.get(location));
    }

    @Override
    public Map<String, String> fetchEventGroupUrls(OptaSportType sport) {
        return EVENT_GROUPS_LOOKUP.get(sport);
    }

    @Override
    public Set<String> fetchIgnoredLocations(OptaSportType sport) {
        return IGNORED_LOCATIONS_LOOKUP.get(sport);
    }

    /**
     * Fetches an appropriate Joda {@link DateTimeZone} for a given sport, returning
     * Optional.absent() if no mapping is found.
     * <p>
     * This method exists because the timezone information in the Opta feeds is either
     * ambiguous ('BST') or non-existent (the non-soccer feeds). Fortunately all sports
     * ingested thus far are each played within a single timezone.
     * @param sport the sport to fetch a timezone for
     */
    public Optional<DateTimeZone> fetchTimeZone(OptaSportType sport) {
        return Optional.fromNullable(TIMEZONE_MAPPING.get(sport));
    }

    @Override
    public Set<String> fetchIgnoredTeams() {
        return IGNORED_TEAM_NAMES;
    }
}
