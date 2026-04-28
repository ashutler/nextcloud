/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.operations

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PatchedGetStatusRemoteOperationTest {

    @Test
    fun `vpn with internet without validated is online on m plus`() {
        val connectivityManager = mock<ConnectivityManager>()
        val network = mock<Network>()
        val capabilities = mock<NetworkCapabilities>()

        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(eq(network))).thenReturn(capabilities)
        whenever(capabilities.hasCapability(eq(NetworkCapabilities.NET_CAPABILITY_INTERNET))).thenReturn(true)
        whenever(capabilities.hasTransport(eq(NetworkCapabilities.TRANSPORT_VPN))).thenReturn(true)

        val isOnline = PatchedGetStatusRemoteOperation.isOnline(connectivityManager, Build.VERSION_CODES.M)

        assertTrue(isOnline)
    }

    @Test
    fun `falls back to activeNetworkInfo when capabilities unavailable`() {
        val connectivityManager = mock<ConnectivityManager>()
        val network = mock<Network>()
        val networkInfo = mock<NetworkInfo>()

        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(null)
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnectedOrConnecting).thenReturn(true)

        val isOnline = PatchedGetStatusRemoteOperation.isOnline(connectivityManager, Build.VERSION_CODES.M)

        assertTrue(isOnline)
    }

    @Test
    fun `older api preserves activeNetworkInfo behavior`() {
        val connectivityManager = mock<ConnectivityManager>()
        val networkInfo = mock<NetworkInfo>()

        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnectedOrConnecting).thenReturn(false)

        val isOnline = PatchedGetStatusRemoteOperation.isOnline(connectivityManager, Build.VERSION_CODES.LOLLIPOP)

        assertFalse(isOnline)
    }
}
