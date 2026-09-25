package de.qspool.clementineremote.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * Network discovery listens on the local network, not on a VPN such as Tailscale or on mobile
 * data, whose addresses the phone may list first.
 */
@RunWith(RobolectricTestRunner.class)
public class LocalNetworkAddressTest {

    private Context mContext;

    private ConnectivityManager mConnectivity;

    private int mNextId = 100;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mConnectivity = mContext.getSystemService(ConnectivityManager.class);
        // Start without Robolectric's default network.
        shadowOf(mConnectivity).clearAllNetworks();
    }

    private void addNetwork(String address, int... transports) throws Exception {
        Network network = ShadowNetwork.newInstance(mNextId++);
        NetworkInfo info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED);
        shadowOf(mConnectivity).addNetwork(network, info);

        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        for (int transport : transports) {
            shadowOf(capabilities).addTransportType(transport);
        }
        shadowOf(mConnectivity).setNetworkCapabilities(network, capabilities);

        LinkProperties link = new LinkProperties();
        ReflectionHelpers.callInstanceMethod(link, "addLinkAddress",
                ClassParameter.from(LinkAddress.class, ReflectionHelpers.callConstructor(
                        LinkAddress.class,
                        ClassParameter.from(InetAddress.class, InetAddress.getByName(address)),
                        ClassParameter.from(int.class, 24))));
        shadowOf(mConnectivity).setLinkProperties(network, link);
    }

    private String localAddress() {
        java.net.Inet4Address address = Utilities.getLocalNetworkIp4Address(mContext);
        return address == null ? null : address.getHostAddress();
    }

    @Test
    public void skipsVpnAndMobileData() throws Exception {
        // As on a phone running Tailscale with mobile data on: both listed before Wi-Fi, and
        // the VPN also reporting the Wi-Fi it runs over.
        addNetwork("100.68.237.114", NetworkCapabilities.TRANSPORT_VPN,
                NetworkCapabilities.TRANSPORT_WIFI);
        addNetwork("10.20.30.40", NetworkCapabilities.TRANSPORT_CELLULAR);
        addNetwork("192.168.86.207", NetworkCapabilities.TRANSPORT_WIFI);

        assertEquals("192.168.86.207", localAddress());
    }

    @Test
    public void usesEthernet() throws Exception {
        addNetwork("10.0.0.5", NetworkCapabilities.TRANSPORT_ETHERNET);
        assertEquals("10.0.0.5", localAddress());
    }

    @Test
    public void noLocalNetwork() throws Exception {
        addNetwork("100.68.237.114", NetworkCapabilities.TRANSPORT_VPN);
        addNetwork("10.20.30.40", NetworkCapabilities.TRANSPORT_CELLULAR);
        assertNull(localAddress());
    }
}
