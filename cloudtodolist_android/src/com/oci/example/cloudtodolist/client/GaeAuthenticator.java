package com.oci.example.cloudtodolist.client;

import android.accounts.*;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implementation of a RestAuthenticator for Google App Engine hosted
 * rest services.
 * Authenticating with GAE follows these steps:
 * <p/>
 * 1. Request an auth token for the specified account using getAuthToken. Construction
 * of the Authenticator requires the account object obtained from the Android AccountManager.
 * Its up to the application to determine which account to use.     The token generation is
 * handled by the Android Google Authenticator, which also stores user credentials.
 * <p/>
 * 2. Make a login request using the auth token. All GAE apps have a login URI, relative to
 * the app's URI, to login to google accounts. Specifying an authtoken in the query string
 * will allow for non-interactive logins and does not require the credentials to be passed
 * to the service.
 * <p/>
 * 3. If the request fails, it could be that the cached auth token is stale or the server
 * not longer supports tokens of that type. The authenticator will call invalidateToken and
 * attempt to get a fresh token from the AccountManager and retry the login operation.
 */
public class GaeAuthenticator implements HttpRestAuthenticator {

    // Log Tag
    private static final String TAG = "GaeAuthenticator";

    // String representing the auth token type for GAE apps
    private static final String authTokenType = "ah";

    // String representing the auth cookie return for GAE apps logins
    private String authCookieName = "ACSID";

    // Login path for GAE app urls
    private static final String ENTRIES_PATH = "/_ah/login";

    // Reference to the system AccountManager
    private final AccountManager accountManager;

    // Account to use for authentication requests
    private final Account account;


    /**
     * Constructor
     *
     * @param context context
     * @param account account to use for authentication
     */
    public GaeAuthenticator(Context context, Account account) {
        this.accountManager = AccountManager.get(context);
        this.account = account;

    }

    /**
     * Handles requests to login to a Rest service.
     * For Google App Engine Apps, the login is via an auth token. The
     * Android Google Authenticator is capable of storing user credentials
     * and requesting an auth token, so this request needs to validate the
     * auth token by logging in and setting a auth cookie for all subsequent
     * requests
     *
     * @param client    HttpClient object to login with
     * @param scheme    scheme portion of the login uri for the GAE app
     * @param authority authority scheme portion of the login uri for the GAE app
     */
    @Override
    public void login(final DefaultHttpClient client, final String scheme, final String authority)
            throws AuthenticationException, IOException, URISyntaxException {

        try {
            String token = getAuthToken();
            if (!loginWithAuthToken(client, token, scheme, authority)) {
                invalidateAuthToken(token);
                token = getAuthToken();
                if (!loginWithAuthToken(client, token, scheme, authority)) {
                    String msg = "login, Invalid Credentials: " + account.name + "(" + account.type + ")";
                    Log.e(TAG, msg);
                    throw new InvalidCredentialsException(msg);
                }
            }

        } catch (OperationCanceledException e) {
            Log.e(TAG, "login, operation cancelled: " + e.toString());
            throw new AuthenticationException("login operation cancelled", e);
        } catch (AuthenticatorException e) {
            Log.e(TAG, "login, authenticator error: " + e.toString());
            throw new AuthenticationException("authenticator error", e);
        } catch (ProtocolException e) {
            Log.e(TAG, "login, authentication error: " + e.toString());
            throw new AuthenticationException("authentication error", e);
        }
    }

    /**
     * Handles authenticating each Rest operation.
     * In the case of Google App Engine, all requests are authenticated
     * with a auth cookie returned from login, so nothing to do here.
     *
     * @param request http request to add authentication info to
     */
    @Override
    public void addAuthenticationInfoToRequest(HttpRequestBase request) {
        // nothing to do, the request is authenticated in the cookie
        // returned from the login
    }


    /**
     * Requests an auth token for the specified authenticator account
     *
     * @return authentication token
     * @throws IOException                usually because of network trouble
     * @throws AuthenticatorException     if the authenticator failed to respond
     * @throws OperationCanceledException if the operation is canceled for any reason,
     *                                    incluidng the user canceling a credential request
     */
    private String getAuthToken() throws IOException, AuthenticatorException, OperationCanceledException {

        AccountManagerFuture<Bundle> result = accountManager.getAuthToken(
                account, authTokenType, true, null, new Handler());

        Bundle bundle = result.getResult();

        return bundle.getString(AccountManager.KEY_AUTHTOKEN);

    }

    /**
     * Invalidates a previously returned auth token.
     * This will instruct the AccountManager to clear the token out of its cache
     * and subsequent calls to getAuthToken will result in a new token being generated
     *
     * @param token stale auth token to invalidate
     */
    private void invalidateAuthToken(String token) {
        accountManager.invalidateAuthToken(authTokenType, token);
    }

    /**
     * This will issue a login request, using a previously issued auth token.
     * All GAE apps have a login URI relative the application URI. This routine will
     * build that URI an attempt to login with the specified auth token. If it succeeds,
     * the http client will have a GAE authentication cookie (ACSID) in its store. All
     * subsequent requests will use the cookie to authenticate the request
     *
     * @param client    HttpClient to use for login request
     * @param authToken token to use for login operation
     * @param scheme    scheme portion of the login uri for the GAE app
     * @param authority authority portion of the login uri for the GAE app
     * @return true if the login succeeded, false if an authentication error occurred and the
     *         token may be stale
     */
    private boolean loginWithAuthToken(DefaultHttpClient client, String authToken, String scheme, String authority)
            throws ProtocolException, URISyntaxException, IOException {

        /**
         * Don't follow redirects, once the login is complete, a redirect to the
         * the "continue" param in the query string, will be issued. Just ignore it
         * and use it as an indication of success
         */
        client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

        HttpGet request = new HttpGet();

        // Build the login URI, for the specified scheme and authority
        request.setURI(new URI(scheme,
                authority,
                ENTRIES_PATH,
                "continue=http://localhost/&auth=" + authToken,
                null)
        );

        HttpResponse response = client.execute(request);
        // Response should be a redirect on success
        if (response.getStatusLine().getStatusCode() == 302) {
            // The secure cookies are specific to the secure connection, and are
            //  prefixed by and S
            if (scheme.equals("https"))
                authCookieName = "S" + authCookieName;
            for (Cookie cookie : client.getCookieStore().getCookies()) {
                if (cookie.getName().equals(authCookieName))
                    return true;
            }
            return false;
        }


        return false;
    }

}
