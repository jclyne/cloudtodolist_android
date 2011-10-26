package com.oci.example.cloudtodolist.client;


import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URISyntaxException;

public interface HttpRestAuthenticator {

    public void login(DefaultHttpClient client, String scheme, String authority)
            throws AuthenticationException, URISyntaxException, IOException;

    public void addAuthenticationInfoToRequest(HttpRequestBase request);
}
