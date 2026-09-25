package de.qspool.clementineremote.backend.mdns;

import org.junit.Test;

import javax.jmdns.ServiceInfo;

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
}
