package com.oci.example.cloudtodolist.provider;


import android.accounts.Account;
import com.oci.example.cloudtodolist.client.HttpRestClient;

/**
 * Interface that provides the system a means to request a sync operation
 * from a content provider that is providing data from a remote data source,
 * via a REST web service. The content provider interface allows access to the
 * data in conditions that may or may not have network access.
 */
public interface RestDataProvider {

    /**
     * Represents a sync operation result. This is the main
     * vehicle for the RestServiceProvider to communicate the state
     * and result of a sync operation.
     */
    class SyncResult {
        public boolean fullSyncRequested = false;
        public long numDeletes = 0;
        public long numInserts = 0;
        public long numUpdates = 0;
        public long numEntries = 0;

        public long numUpstreamDeletes = 0;
        public long numUpstreamInserts = 0;
        public long numUpstreamUpdates = 0;

        public long numResponseExceptions = 0;
        public long numRequestExceptions = 0;
        public long numIoExceptions = 0;
        public long numAuthenticationErrors = 0;
        public boolean invalidCredentials = false;

        public boolean updated() {
            return numDeletes > 0
                    || numInserts > 0
                    || numUpdates > 0;
        }

        public boolean networkError() {
            return numIoExceptions > 0;
        }

        public boolean serverError() {
            return numResponseExceptions > 0;
        }

        public boolean authenticationError() {
            return numAuthenticationErrors > 0;
        }


    }

    /**
     * Requests that the REST content provider perform a sync operation with the
     * REST webservice. The provider should use the supplied HttpRestClient to perform
     * the sync. There is no return value as it is completely up to the Content Provider
     * to deal with errors and to notify the user of updates and/or errors.
     * <p/>
     * <p>Since this method will be performing 1 or more network operations, it should be
     * called from a SERVICE on a background thread. It is up to the caller to invoke this
     * method in the proper context. It is up to the calling context to properly schedule
     * the sync operations.</p>
     * <p/>
     * <p>Implementation note: This interface requires direct access to the provider and
     * is not available through the provider client via the system's content resolver. A
     * direct reference to the provider is available via the getLocalContentProvider() method
     * of the provider client. This will only work, however, if the caller is executing in
     * the same process. </p>
     *
     * @param client  the client to use for the requested sync operation
     * @param refresh flag that indicates whether a full refresh is desired as apposed to an
     *                updated. This is client specific as there may be no differentiation.
     */
    public SyncResult onPerformSync(HttpRestClient client, Account account, boolean refresh);

}
