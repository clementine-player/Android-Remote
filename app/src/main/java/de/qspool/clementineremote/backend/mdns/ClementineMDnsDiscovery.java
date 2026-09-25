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
import java.util.LinkedList;
import java.util.List;
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

    private JmDNS mJmDNS;

    private MulticastLock mMulticastLock;

    private Thread mThread;

    private final String mDnsType = "_clementine._tcp.local.";

    private ServiceListener mListener;

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
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                WifiManager wifi = (WifiManager)
                        App.getApp().getApplicationContext()
                                .getSystemService(android.content.Context.WIFI_SERVICE);
                mMulticastLock = wifi.createMulticastLock("Clementine Lock");
                mMulticastLock.setReferenceCounted(true);
                mMulticastLock.acquire();
                jmDnsListener();
            }

        });
        mThread.start();
    }

    /**
     * Stop network discovery
     */
    public void stopServiceDiscovery() {
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mListener != null) {
                        mJmDNS.removeServiceListener(mDnsType, mListener);
                        mJmDNS.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mMulticastLock != null && mMulticastLock.isHeld()) {
                    mMulticastLock.release();
                }
            }

        });
        mThread.start();
    }

    /**
     * Get the services on the network
     *
     * @return A LinkedList of ServiceInfo with the services.
     */
    public List<ServiceInfo> getServices() {
        return mServices;
    }

    /**
     * Keeps each Clementine once, by name: jmDNS resolves a service again as more of its records
     * (A, AAAA, SRV) arrive.
     */
    synchronized void addService(ServiceInfo info) {
        removeService(info.getName());
        mServices.add(info);
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
     * @return A LinkedList of Hosts found
     */
    public LinkedList<String> getHosts() {
        LinkedList<String> hosts = new LinkedList<String>();
        for (ServiceInfo info : mServices) {
            hosts.add(info.getName());
        }
        return hosts;
    }

    private void jmDnsListener() {
        try {
            Inet4Address ip = Utilities.getLocalNetworkIp4Address(App.getApp());
            if (ip == null) {
                Log.i(TAG, "Not on a local network, so not looking for Clementine");
                return;
            }
            Log.d(TAG, "Looking for Clementine from " + ip.getHostAddress());

            mJmDNS = JmDNS.create(ip);
            mJmDNS.addServiceListener(mDnsType, mListener = new ServiceListener() {

                @Override
                public void serviceAdded(ServiceEvent serviceEvent) {
                    mJmDNS.requestServiceInfo(serviceEvent.getType(), serviceEvent.getName(), 250);
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
                    Inet4Address inet4[] = info.getInet4Addresses();
                    if (inet4.length > 0) {
                        addService(info);

                        // Send a message to the connect activity
                        if (mConnectActivityHandler != null) {
                            Message msg = Message.obtain();
                            msg.obj = new ServiceFound();
                            mConnectActivityHandler.sendMessage(msg);
                        }
                    }
                }

            });
        } catch (Exception e) {
            Log.w(TAG, "Network discovery failed", e);
            mJmDNS = null;
        }
    }
}