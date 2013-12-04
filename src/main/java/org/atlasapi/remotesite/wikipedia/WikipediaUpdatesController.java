package org.atlasapi.remotesite.wikipedia;

import javax.servlet.http.HttpServletResponse;

import org.atlasapi.remotesite.wikipedia.film.FilmArticleTitleSource;
import org.atlasapi.remotesite.wikipedia.television.TvBrandArticleTitleSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.common.collect.ImmutableList;

@Controller
public class WikipediaUpdatesController {
    private final WikipediaModule module;
    
    public WikipediaUpdatesController(WikipediaModule module) {
        this.module = module;
    }
    
    @RequestMapping(value="/system/update/wikipedia/film", method=RequestMethod.POST)
    public void updateFilm(HttpServletResponse response, @RequestParam("name") final String articleName) {
        module.filmsUpdaterForTitles(new FilmArticleTitleSource() {
            @Override
            public Iterable<String> getAllFilmArticleTitles() {
                return ImmutableList.of(articleName);
            }
        }).run();
    }
    
    @RequestMapping(value="/system/update/wikipedia/tvBrand", method=RequestMethod.POST)
    public void updateTvBrand(HttpServletResponse response, @RequestParam("name") final String articleName) {
        module.tvBrandsUpdaterForTitles(new TvBrandArticleTitleSource() {
            @Override
            public Iterable<String> getAllTvBrandArticleTitles() {
                return ImmutableList.of(articleName);
            }
        }).run();
    }

}
