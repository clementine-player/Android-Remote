/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.backend.mdns;

import android.annotation.TargetApi;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.elements.ServiceFound;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineMDnsDiscovery {

    private static final String TAG = "ClementineMDnsDiscovery";

    // Guards mStopped, mSessionGeneration, mJmDnsInstances, mListeners, and
    // mMulticastLock across start/stop threads without holding it over I/O.
    private final Object mLock = new Object();

    private final List<JmDNS> mJmDnsInstances = new ArrayList<>();

    private final Map<JmDNS, ServiceListener> mListeners = new HashMap<>();

    private MulticastLock mMulticastLock;

    private boolean mStopped = false;

    // Incremented on every start and stop so an in-flight start thread cleans
    // up its own JmDNS sockets and lock if superseded or stopped mid-setup.
    private int mSessionGeneration = 0;

    private final String mDnsType = "_clementine._tcp.local.";

    /** Written by jmDNS threads and read on the UI thread. */
    private final List<ServiceInfo> mServices = new CopyOnWriteArrayList<>();

    private Handler mConnectActivityHandler;

    public ClementineMDnsDiscovery(Handler handler) {
        mConnectActivityHandler = handler;
    }

    /**
     * Discover services on the network
     */
    public void discoverServices() {
        stopServiceDiscovery();
        final int generation;
        synchronized (mLock) {
            mStopped = false;
            generation = ++mSessionGeneration;
        }
        Thread startThread = new Thread(new Runnable() {
            @Override
            public void run() {
                MulticastLock acquiredLock = null;
                try {
                    WifiManager wifi = (WifiManager) App.getApp().getApplicationContext()
                            .getSystemService(android.content.Context.WIFI_SERVICE);
                    if (wifi != null) {
                        MulticastLock lock = wifi.createMulticastLock("Clementine Lock");
                        // Non-reference-counted so stopServiceDiscovery() or
                        // double-stop never throws MulticastLock under-locked.
                        lock.setReferenceCounted(false);
                        synchronized (mLock) {
                            if (mStopped || generation != mSessionGeneration) {
                                return;
                            }
                            lock.acquire();
                            mMulticastLock = lock;
                            acquiredLock = lock;
                        }
                    }
                    jmDnsListener(generation);
                } finally {
                    synchronized (mLock) {
                        // Release our lock if stopped/superseded mid-startup or
                        // if no interface opened a JmDNS socket (e.g. off Wi-Fi).
                        boolean nothingStarted = mJmDnsInstances.isEmpty();
                        if ((mStopped || generation != mSessionGeneration || nothingStarted)
                                && acquiredLock != null) {
                            if (mMulticastLock == acquiredLock) {
                                mMulticastLock = null;
                            }
                            releaseLockQuietly(acquiredLock);
                        }
                    }
                }
            }
        }, "ClementineMdnsStart");
        startThread.start();
    }

    /**
     * Stop network discovery safely and idempotently.
     */
    public void stopServiceDiscovery() {
        final List<JmDNS> toClose;
        final Map<JmDNS, ServiceListener> listenersToRemove;
        final MulticastLock lockToRelease;
        synchronized (mLock) {
            // Drain instances and lock atomically under mLock so repeated calls
            // are idempotent and JmDNS.close() runs off-thread without holding it.
            mStopped = true;
            mSessionGeneration++;
            toClose = new ArrayList<>(mJmDnsInstances);
            listenersToRemove = new HashMap<>(mListeners);
            mJmDnsInstances.clear();
            mListeners.clear();
            lockToRelease = mMulticastLock;
            mMulticastLock = null;
        }
        if (toClose.isEmpty() && lockToRelease == null) {
            return;
        }
        Thread stopThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (JmDNS jmdns : toClose) {
                    try {
                        ServiceListener listener = listenersToRemove.get(jmdns);
                        if (listener != null) {
                            jmdns.removeServiceListener(mDnsType, listener);
                        }
                        jmdns.close();
                    } catch (IOException | RuntimeException e) {
                        Log.w(TAG, "Error closing JmDNS", e);
                    }
                }
                releaseLockQuietly(lockToRelease);
            }
        }, "ClementineMdnsStop");
        stopThread.start();
    }

    private static void releaseLockQuietly(MulticastLock lock) {
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeld()) {
                lock.release();
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Get the services on the network
     *
     * @return A list of ServiceInfo with the services.
     */
    public List<ServiceInfo> getServices() {
        return mServices;
    }

    /**
     * Keeps each Clementine once, by name: jmDNS resolves a service again as more of its records
     * (A, AAAA, SRV) arrive. Avoid letting an IPv6-only resolution drop known IPv4 addresses, while
     * still applying port or TXT updates if the service changed.
     */
    synchronized void addService(ServiceInfo info) {
        ServiceInfo resolved = info;
        for (ServiceInfo existing : mServices) {
            if (existing.getName().equals(info.getName())) {
                Inet4Address[] existingIpv4 = existing.getInet4Addresses();
                if (existingIpv4.length > 0 && info.getInet4Addresses().length == 0) {
                    // Nothing changed on an IPv6-only re-resolve; keep existing
                    // entry untouched so mServices does not churn unnecessarily.
                    if (existing.getPort() == info.getPort()
                            && Arrays.equals(existing.getTextBytes(), info.getTextBytes())) {
                        return;
                    }
                    // Preserve known IPv4 addresses while taking the updated
                    // port, TXT records, and IPv6 addresses from the new info.
                    resolved = new MergedServiceInfo(info, existingIpv4);
                }
                break;
            }
        }
        removeService(resolved.getName());
        mServices.add(resolved);
    }

    @SuppressWarnings("deprecation")
    private static final class MergedServiceInfo extends ServiceInfo {
        private final ServiceInfo mDelegate;
        private final Inet4Address[] mIpv4;

        MergedServiceInfo(ServiceInfo delegate, Inet4Address[] ipv4) {
            mDelegate = delegate;
            mIpv4 = Arrays.copyOf(ipv4, ipv4.length);
        }

        @Override
        public boolean hasData() {
            return mDelegate.hasData() || mIpv4.length > 0;
        }

        @Override
        public String getType() {
            return mDelegate.getType();
        }

        @Override
        public String getTypeWithSubtype() {
            return mDelegate.getTypeWithSubtype();
        }

        @Override
        public String getName() {
            return mDelegate.getName();
        }

        @Override
        public String getKey() {
            return mDelegate.getKey();
        }

        @Override
        public String getQualifiedName() {
            return mDelegate.getQualifiedName();
        }

        @Override
        public String getServer() {
            return mDelegate.getServer();
        }

        @Override
        public String getHostAddress() {
            return mIpv4.length > 0 ? mIpv4[0].getHostAddress() : mDelegate.getHostAddress();
        }

        @Override
        public String[] getHostAddresses() {
            InetAddress[] addresses = getInetAddresses();
            String[] hosts = new String[addresses.length];
            for (int i = 0; i < addresses.length; i++) {
                hosts[i] = addresses[i].getHostAddress();
            }
            return hosts;
        }

        @Override
        public InetAddress getAddress() {
            return getInetAddress();
        }

        @Override
        public InetAddress getInetAddress() {
            return mIpv4.length > 0 ? mIpv4[0] : mDelegate.getInetAddress();
        }

        @Override
        public Inet4Address getInet4Address() {
            return mIpv4.length > 0 ? mIpv4[0] : null;
        }

        @Override
        public Inet6Address getInet6Address() {
            return mDelegate.getInet6Address();
        }

        @Override
        public InetAddress[] getInetAddresses() {
            Inet6Address[] ipv6 = mDelegate.getInet6Addresses();
            InetAddress[] combined = new InetAddress[mIpv4.length + ipv6.length];
            System.arraycopy(mIpv4, 0, combined, 0, mIpv4.length);
            System.arraycopy(ipv6, 0, combined, mIpv4.length, ipv6.length);
            return combined;
        }

        @Override
        public Inet4Address[] getInet4Addresses() {
            return Arrays.copyOf(mIpv4, mIpv4.length);
        }

        @Override
        public Inet6Address[] getInet6Addresses() {
            return mDelegate.getInet6Addresses();
        }

        @Override
        public int getPort() {
            return mDelegate.getPort();
        }

        @Override
        public int getPriority() {
            return mDelegate.getPriority();
        }

        @Override
        public int getWeight() {
            return mDelegate.getWeight();
        }

        @Override
        public byte[] getTextBytes() {
            return mDelegate.getTextBytes();
        }

        @Override
        public String getTextString() {
            return mDelegate.getTextString();
        }

        @Override
        public String getURL() {
            return mDelegate.getURL();
        }

        @Override
        public String[] getURLs() {
            return mDelegate.getURLs();
        }

        @Override
        public String getURL(String protocol) {
            return mDelegate.getURL(protocol);
        }

        @Override
        public String[] getURLs(String protocol) {
            return mDelegate.getURLs(protocol);
        }

        @Override
        public byte[] getPropertyBytes(String name) {
            return mDelegate.getPropertyBytes(name);
        }

        @Override
        public String getPropertyString(String name) {
            return mDelegate.getPropertyString(name);
        }

        @Override
        public Enumeration<String> getPropertyNames() {
            return mDelegate.getPropertyNames();
        }

        @Override
        public String getNiceTextString() {
            return mDelegate.getNiceTextString();
        }

        @Override
        public void setText(byte[] text) throws IllegalStateException {
            mDelegate.setText(text);
        }

        @Override
        public void setText(Map<String, ?> props) throws IllegalStateException {
            mDelegate.setText(props);
        }

        @Override
        public boolean isPersistent() {
            return mDelegate.isPersistent();
        }

        @Override
        public String getDomain() {
            return mDelegate.getDomain();
        }

        @Override
        public String getProtocol() {
            return mDelegate.getProtocol();
        }

        @Override
        public String getApplication() {
            return mDelegate.getApplication();
        }

        @Override
        public String getSubtype() {
            return mDelegate.getSubtype();
        }

        @Override
        public Map<Fields, String> getQualifiedNameMap() {
            return mDelegate.getQualifiedNameMap();
        }

        @Override
        public boolean hasSameAddresses(ServiceInfo other) {
            if (other == null) {
                return false;
            }
            Set<InetAddress> ours = new LinkedHashSet<>();
            Collections.addAll(ours, getInetAddresses());
            Set<InetAddress> theirs = new LinkedHashSet<>();
            Collections.addAll(theirs, other.getInetAddresses());
            return ours.equals(theirs);
        }

        @Override
        public ServiceInfo clone() {
            return new MergedServiceInfo(mDelegate.clone(), mIpv4);
        }
    }

    synchronized void removeService(String name) {
        for (ServiceInfo service : mServices) {
            if (service.getName().equals(name)) {
                mServices.remove(service);
            }
        }
    }

    /**
     * Get the Hosts on the network
     *
     * @return A list of Hosts found
     */
    public List<String> getHosts() {
        List<String> hosts = new ArrayList<String>();
        for (ServiceInfo info : mServices) {
            hosts.add(info.getName());
        }
        return hosts;
    }

    private void jmDnsListener(int generation) {
        List<InetAddress> addresses = Utilities.getLocalNetworkAddresses(App.getApp());
        if (addresses.isEmpty()) {
            Log.i(TAG, "Not on a local network, so not looking for Clementine");
            return;
        }
        for (InetAddress ip : addresses) {
            synchronized (mLock) {
                if (mStopped || generation != mSessionGeneration) {
                    return;
                }
            }
            JmDNS created = null;
            try {
                Log.d(TAG, "Looking for Clementine from " + ip.getHostAddress());
                final JmDNS jmdns = JmDNS.create(ip);
                created = jmdns;
                ServiceListener listener = new ServiceListener() {

                    @Override
                    public void serviceAdded(ServiceEvent serviceEvent) {
                        jmdns.requestServiceInfo(serviceEvent.getType(), serviceEvent.getName(), 250);
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent serviceEvent) {
                        removeService(serviceEvent.getName());
                        // Send a message to the connect activity
                        if (mConnectActivityHandler != null) {
                            Message msg = Message.obtain();
                            msg.obj = new ServiceFound();
                            mConnectActivityHandler.sendMessage(msg);
                        }
                    }

                    @Override
                    public void serviceResolved(ServiceEvent serviceEvent) {
                        ServiceInfo info = serviceEvent.getInfo();
                        // Accept IPv4 or IPv6 records so IPv6-only servers work.
                        InetAddress[] resolved = info.getInetAddresses();
                        if (resolved != null && resolved.length > 0) {
                            addService(info);

                            // Send a message to the connect activity
                            if (mConnectActivityHandler != null) {
                                Message msg = Message.obtain();
                                msg.obj = new ServiceFound();
                                mConnectActivityHandler.sendMessage(msg);
                            }
                        }
                    }
                };
                synchronized (mLock) {
                    // Stop could race while JmDNS.create() blocked; close the
                    // orphan instance instead of publishing it into the lists.
                    if (mStopped || generation != mSessionGeneration) {
                        try {
                            jmdns.close();
                        } catch (IOException ignored) {
                        }
                        return;
                    }
                    mJmDnsInstances.add(jmdns);
                    mListeners.put(jmdns, listener);
                }
                jmdns.addServiceListener(mDnsType, listener);
            } catch (Exception e) {
                Log.w(TAG, "Network discovery failed on " + ip.getHostAddress(), e);
                if (created != null) {
                    synchronized (mLock) {
                        mJmDnsInstances.remove(created);
                        mListeners.remove(created);
                    }
                    try {
                        created.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}