<!--
SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Always-On VPN vs `NO_NETWORK_CONNECTION` analysis

## Login/server reachability path

1. `AuthenticatorActivity` queues `ACTION_GET_SERVER_INFO` without using `ConnectivityService` prechecks.
2. `OperationsService` maps that action to `GetServerInfoOperation`.
3. `GetServerInfoOperation` executes `GetStatusRemoteOperation` and then `DetectAuthenticationMethodOperation`.
4. `AuthenticatorActivity` only renders `NO_NETWORK_CONNECTION`; it does not compute it locally.

## Connectivity validation path used in the app

`ConnectivityServiceImpl.isNetworkAndServerAvailable()` requires `NET_CAPABILITY_INTERNET` on the active network.
If that capability is missing, it returns `false` immediately.

`ConnectivityServiceImpl.isConnected()` explicitly accepts `TRANSPORT_VPN` as connected.

`ConnectivityServiceImpl.isInternetWalled()` only performs active probing when all are true:
- connected,
- Wi-Fi,
- not metered,
- server URL present.

When those eligibility checks fail, it does **not** mark the network as walled unless disconnected.
So a connected VPN/non-Wi-Fi path is generally treated as not walled.

## Key conclusion

For login (`GetServerInfoOperation` / auth flow), there is no app-side rejection of VPN transport
in the shown code. `NO_NETWORK_CONNECTION` for login is most likely produced by lower-level
remote operations (network failure classified in the ownCloud/Nextcloud Android library), not by
`ConnectivityServiceImpl` logic.

### Exact `NO_NETWORK_CONNECTION` conversion in library code

In `GetStatusRemoteOperation` (android-library commit `fc938790f27124d929f6adb6719565f63e514bd5`):
- `run()` returns `NO_NETWORK_CONNECTION` immediately when `isOnline()` is false.
- `isOnline()` checks `ConnectivityManager.getActiveNetworkInfo()?.isConnectedOrConnecting`.
- network/HTTP exceptions in `tryConnection()` are wrapped via `new RemoteOperationResult(e)`.

In `RemoteOperationResult(Exception e)` (same library commit), `NO_NETWORK_CONNECTION` is assigned for:
- `UnknownHostException`
- `ErrnoException` with `errno == ENOTCONN`

Other nearby mappings are not `NO_NETWORK_CONNECTION`:
- `ConnectException` -> `HOST_NOT_AVAILABLE`
- `SocketException` -> `WRONG_CONNECTION`
- `SocketTimeoutException` and `ConnectTimeoutException` -> `TIMEOUT`

This means Always-On VPN lockdown can result in `NO_NETWORK_CONNECTION` either from Android
connectivity state (`isConnectedOrConnecting == false`) or from DNS/resolution-level failures
(`UnknownHostException`) surfaced by the HTTP client.

For features that do call `ConnectivityServiceImpl.isNetworkAndServerAvailable()`, the app assumes
an active network must expose `NET_CAPABILITY_INTERNET`. In strict Always-On VPN setups with no
internet breakout, this capability may be missing or constrained, which causes an immediate
unavailable result before reachability probing.

Therefore:
- the app **does not reject VPN-only transport** in `isConnected()`;
- but some paths **do assume internet capability semantics** (`NET_CAPABILITY_INTERNET`), which
  can reject certain VPN-only network models;
- login-specific `NO_NETWORK_CONNECTION` appears to come from downstream operation failures rather than this service.

## Supporting tests

`ConnectivityServiceTest` confirms:
- VPN + Wi-Fi can still be considered Wi-Fi/connected;
- metered or non-eligible conditions skip reachability probing and may default to "not walled".

## Logging point added in the app

`AuthenticatorActivity.onGetServerInfoFinish` now logs failed server-info checks with:
- result code;
- exception type and stack trace when available.

This makes it easier to distinguish between:
- preflight offline branch (`NO_NETWORK_CONNECTION` with no exception), and
- exception-based failure coming from library networking (`NO_NETWORK_CONNECTION` with original throwable attached).
