package de.qspool.clementineremote.backend.mdns;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.ServiceInfoImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Each Clementine on the network is listed once. */
public class ClementineMDnsDiscoveryTest {

    private static final String TYPE = "_clementine._tcp.local.";

    private static ServiceInfo service(String name) {
        return ServiceInfo.create(TYPE, name, 5500, "");
    }

    @Test
    public void resolvingAgainKeepsOneEntry() {
        ClementineMDnsDiscovery discovery = new ClementineMDnsDiscovery(null);
        // jmDNS resolves the same service again as its A, AAAA and SRV records arrive.
        discovery.addService(service("Clementine on zem"));
        discovery.addService(service("Clementine on zem"));
        ServiceInfo latest = service("Clementine on zem");
        discovery.addService(latest);

        assertEquals(1, discovery.getServices().size());
        assertSame(latest, discovery.getServices().get(0));
    }

    @Test
    public void listsEachClementine() {
        ClementineMDnsDiscovery discovery = new ClementineMDnsDiscovery(null);
        discovery.addService(service("Clementine on zem"));
        discovery.addService(service("Clementine on laptop"));
        assertEquals(2, discovery.getServices().size());

        discovery.removeService("Clementine on zem");
        assertEquals(1, discovery.getServices().size());
        assertEquals("Clementine on laptop", discovery.getServices().get(0).getName());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void ipv6OnlyResolutionDoesNotOverwriteIpv4Resolution() throws Exception {
        ClementineMDnsDiscovery discovery = new ClementineMDnsDiscovery(null);
        ServiceInfoImpl ipv4 = (ServiceInfoImpl) ServiceInfo.create(TYPE, "Clementine on zem", 5500, "");
        java.lang.reflect.Method add4 = ServiceInfoImpl.class.getDeclaredMethod("addAddress", Inet4Address.class);
        add4.setAccessible(true);
        add4.invoke(ipv4, (Inet4Address) InetAddress.getByName("192.168.1.42"));

        ServiceInfoImpl ipv6 = (ServiceInfoImpl) ServiceInfo.create(TYPE, "Clementine on zem", 5500, "");
        java.lang.reflect.Method add6 = ServiceInfoImpl.class.getDeclaredMethod("addAddress", Inet6Address.class);
        add6.setAccessible(true);
        add6.invoke(ipv6, (Inet6Address) InetAddress.getByName("fe80::1"));

        discovery.addService(ipv4);
        discovery.addService(ipv6);

        assertEquals(1, discovery.getServices().size());
        assertSame(ipv4, discovery.getServices().get(0));

        // A changed port must update the port while preserving the previously resolved IPv4 address.
        ServiceInfoImpl updatedPortIpv6 = (ServiceInfoImpl) ServiceInfo.create(TYPE, "Clementine on zem", 5501, "");
        add6.invoke(updatedPortIpv6, (Inet6Address) InetAddress.getByName("fe80::1"));
        discovery.addService(updatedPortIpv6);

        assertEquals(1, discovery.getServices().size());
        ServiceInfo updated = discovery.getServices().get(0);
        assertEquals(5501, updated.getPort());
        assertEquals(1, updated.getInet4Addresses().length);
        assertEquals("192.168.1.42", updated.getInet4Addresses()[0].getHostAddress());
        assertEquals("192.168.1.42", updated.getInet4Address().getHostAddress());
        assertEquals(1, updated.getInet6Addresses().length);
        assertEquals(InetAddress.getByName("fe80::1"), updated.getInet6Addresses()[0]);
    }
}
