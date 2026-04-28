/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.operations;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory;
import com.owncloud.android.lib.common.accounts.AccountUtils;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.status.OwnCloudVersion;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class PatchedGetStatusRemoteOperation extends RemoteOperation {

    private static final int TRY_CONNECTION_TIMEOUT = 50000;
    private static final String TAG = PatchedGetStatusRemoteOperation.class.getSimpleName();
    private static final String NODE_INSTALLED = "installed";
    private static final String NODE_VERSION = "version";
    private static final String NODE_EXTENDED_SUPPORT = "extendedSupport";
    private static final String PROTOCOL_HTTPS = "https://";
    private static final String PROTOCOL_HTTP = "http://";
    private static final int UNTRUSTED_DOMAIN_ERROR_CODE = 15;

    private final Context context;
    private RemoteOperationResult latestResult;

    public PatchedGetStatusRemoteOperation(Context context) {
        this.context = context;
    }

    private boolean tryConnection(OwnCloudClient client) {
        boolean success = false;
        GetMethod getMethod = null;
        String baseUrl = client.getBaseUri().toString();

        try {
            getMethod = new GetMethod(baseUrl + AccountUtils.STATUS_PATH);
            HttpParams params = HttpMethodParams.getDefaultParams();
            params.setParameter(HttpMethodParams.USER_AGENT, OwnCloudClientManagerFactory.getUserAgent());
            getMethod.getParams().setDefaults(params);

            client.setFollowRedirects(false);
            boolean redirectedToHttp = false;
            int status = client.executeMethod(getMethod, TRY_CONNECTION_TIMEOUT, TRY_CONNECTION_TIMEOUT);
            latestResult = new RemoteOperationResult(status == HttpStatus.SC_OK, getMethod);
            String redirectedLocation = latestResult.getRedirectedLocation();

            while (redirectedLocation != null && !redirectedLocation.isEmpty() && !latestResult.isSuccess()) {
                redirectedToHttp |= baseUrl.startsWith(PROTOCOL_HTTPS) && redirectedLocation.startsWith(PROTOCOL_HTTP);
                getMethod.releaseConnection();
                getMethod = new GetMethod(redirectedLocation);
                status = client.executeMethod(getMethod, TRY_CONNECTION_TIMEOUT, TRY_CONNECTION_TIMEOUT);
                latestResult = new RemoteOperationResult(status == HttpStatus.SC_OK, getMethod);
                redirectedLocation = latestResult.getRedirectedLocation();
            }

            String response = getMethod.getResponseBodyAsString();

            if (status == HttpStatus.SC_OK) {
                JSONObject json = new JSONObject(response);

                if (!json.getBoolean(NODE_INSTALLED)) {
                    latestResult = new RemoteOperationResult(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED);
                } else {
                    boolean extendedSupport = json.has(NODE_EXTENDED_SUPPORT) && json.getBoolean(NODE_EXTENDED_SUPPORT);
                    OwnCloudVersion version = new OwnCloudVersion(json.getString(NODE_VERSION));

                    if (!version.isVersionValid()) {
                        latestResult = new RemoteOperationResult(RemoteOperationResult.ResultCode.BAD_OC_VERSION);
                    } else {
                        if (redirectedToHttp) {
                            latestResult =
                                new RemoteOperationResult(
                                    RemoteOperationResult.ResultCode.OK_REDIRECT_TO_NON_SECURE_CONNECTION
                                );
                        } else {
                            latestResult = new RemoteOperationResult(
                                baseUrl.startsWith(PROTOCOL_HTTPS)
                                    ? RemoteOperationResult.ResultCode.OK_SSL
                                    : RemoteOperationResult.ResultCode.OK_NO_SSL
                            );
                        }
                        ArrayList<Object> data = new ArrayList<>();
                        data.add(version);
                        data.add(extendedSupport);
                        latestResult.setData(data);
                        success = true;
                    }
                }
            } else if (status == HttpStatus.SC_BAD_REQUEST) {
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.getInt("code") == UNTRUSTED_DOMAIN_ERROR_CODE) {
                        latestResult = new RemoteOperationResult(RemoteOperationResult.ResultCode.UNTRUSTED_DOMAIN);
                    } else {
                        latestResult = new RemoteOperationResult(false, status, getMethod.getResponseHeaders());
                    }
                } catch (JSONException e) {
                    latestResult = new RemoteOperationResult(false, status, getMethod.getResponseHeaders());
                }
            } else {
                latestResult = new RemoteOperationResult(false, status, getMethod.getResponseHeaders());
            }
        } catch (JSONException e) {
            latestResult = new RemoteOperationResult(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED);
        } catch (Exception e) {
            latestResult = new RemoteOperationResult(e);
        } finally {
            if (getMethod != null) {
                getMethod.releaseConnection();
            }
        }

        if (latestResult.isSuccess()) {
            Log_OC.i(TAG, "Connection check at " + baseUrl + ": " + latestResult.getLogMessage());
        } else if (latestResult.getException() != null) {
            Log_OC.e(
                TAG,
                "Connection check at " + baseUrl + ": " + latestResult.getLogMessage(),
                latestResult.getException()
            );
        } else {
            Log_OC.e(TAG, "Connection check at " + baseUrl + ": " + latestResult.getLogMessage());
        }

        return success;
    }

    static boolean isOnline(ConnectivityManager connectivityManager, int sdkInt) {
        if (connectivityManager == null) {
            return false;
        }

        if (sdkInt >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities != null) {
                    boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    boolean hasVpnTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                    if (hasInternet && hasVpnTransport) {
                        return true;
                    }
                    return hasInternet;
                }
            }
        }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    private boolean isOnline() {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return isOnline(connectivityManager, SDK_INT);
    }

    @Override
    protected RemoteOperationResult run(OwnCloudClient client) {
        if (!isOnline()) {
            return new RemoteOperationResult(RemoteOperationResult.ResultCode.NO_NETWORK_CONNECTION);
        }

        String baseUri = client.getBaseUri().toString();
        if (baseUri.startsWith(PROTOCOL_HTTP) || baseUri.startsWith(PROTOCOL_HTTPS)) {
            tryConnection(client);
        } else {
            client.setBaseUri(Uri.parse(PROTOCOL_HTTPS + baseUri));
            boolean httpsSuccess = tryConnection(client);
            if (!httpsSuccess && !latestResult.isSslRecoverableException()) {
                Log_OC.d(TAG, "establishing secure connection failed, trying non secure connection");
                client.setBaseUri(Uri.parse(PROTOCOL_HTTP + baseUri));
                tryConnection(client);
            }
        }

        return latestResult;
    }
}
