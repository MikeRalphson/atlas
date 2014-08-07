package org.atlasapi.remotesite.getty;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.CharStreams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GettyTokenFetcher {

    private static final String GETTY_OAUTH_URL = "https://connect.gettyimages.com/oauth2/token"; 
    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String CLIENT_ID_KEY = "client_id";
    private static final String CLIENT_SECRET_KEY = "client_secret";
    private static final String ACCES_TOKEN_KEY = "access_token";
    
    private final String clientId;
    private final String clientSecret;
    
    public GettyTokenFetcher(String clientId, String clientSecret) {
        this.clientId = checkNotNull(clientId);
        this.clientSecret = checkNotNull(clientSecret);
    }
    
    public String getToken() throws ClientProtocolException, IOException {
        String oauth = oauth();
        return parseToken(oauth);
    }
    
    private String oauth() throws ClientProtocolException, IOException {
        HttpPost post = new HttpPost(GETTY_OAUTH_URL);
        
        Builder<BasicNameValuePair> params = new ImmutableList.Builder<BasicNameValuePair>();
        params.add(new BasicNameValuePair(GRANT_TYPE, CLIENT_CREDENTIALS));
        params.add(new BasicNameValuePair(CLIENT_ID_KEY, clientId));
        params.add(new BasicNameValuePair(CLIENT_SECRET_KEY, clientSecret));
        post.setEntity(new UrlEncodedFormEntity(params.build()));
        
        HttpResponse resp = new DefaultHttpClient().execute(post);
        InputStreamReader reader = new InputStreamReader(resp.getEntity().getContent());
        return CharStreams.toString(reader);
    }
    
    private String parseToken(String content) {
        JsonObject parse = (JsonObject) new JsonParser().parse(content);
        JsonElement element = parse.get(ACCES_TOKEN_KEY);
        return element.getAsString();
    }
    
}
