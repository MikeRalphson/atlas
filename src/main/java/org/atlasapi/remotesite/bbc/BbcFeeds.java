package org.atlasapi.remotesite.bbc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BbcFeeds {

    private static final String PID_PATTERN = "[bp]\\d\\d[a-z0-9]+";
    
    private static final String SLASH_PROGRAMMES_BASE = "http://www.bbc.co.uk/programmes/";
    
    public static final Pattern SLASH_PROGRAMMES_URL_PATTERN = Pattern.compile(Pattern.quote(SLASH_PROGRAMMES_BASE) + PID_PATTERN);
	
	private static final Pattern PID_FINDER = Pattern.compile("(" + PID_PATTERN + ")");
	
	public static String pidFrom(String uri) {
		Matcher matcher = PID_FINDER.matcher(uri);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	public static String slashProgrammesUriForPid(String pid) {
	    if (!isBbcPid(pid)) {
	        throw new IllegalArgumentException("PID " + pid + " did not match the BBC PID pattern " + PID_FINDER);
	    }
		return SLASH_PROGRAMMES_BASE + pid;
	}

    public static boolean isBbcPid(String pid) {
        return PID_FINDER.matcher(pid).matches();
    }

    public static boolean isACanonicalSlashProgrammesUri(String uri) {
        return SLASH_PROGRAMMES_URL_PATTERN.matcher(uri).matches();
    }
    
}
