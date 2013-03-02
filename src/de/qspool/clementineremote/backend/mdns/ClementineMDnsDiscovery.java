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

import java.io.IOException;
import java.net.Inet4Address;
import java.util.LinkedList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.elements.ServiceFound;

import android.annotation.TargetApi;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ClementineMDnsDiscovery {
	private JmDNS mJmDNS;
	MulticastLock mMulticastLock;
	
	private final String mDnsType = "_clementine._tcp.local.";
	private ServiceListener mListener;
	private LinkedList<ServiceInfo> mServices;

	private Handler mConnectActivityHandler;
	
	public ClementineMDnsDiscovery(Handler handler) {
		mConnectActivityHandler = handler;
		
		mServices = new LinkedList<ServiceInfo>();
	}
	
	public void discoverServices() {
		WifiManager wifi = (WifiManager)
		              App.mApp.getSystemService(android.content.Context.WIFI_SERVICE);
		mMulticastLock = wifi.createMulticastLock("Clementine Lock");
		mMulticastLock.setReferenceCounted(true);
		mMulticastLock.acquire();
		jmDnsListener();
	}
	
	public void stopServiceDiscovery() {
		try {
			mJmDNS.removeServiceListener(mDnsType, mListener);
			mJmDNS.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (mMulticastLock != null) mMulticastLock.release();
	}
	
	public LinkedList<ServiceInfo> getServices() {
		return mServices;
	}
	
	public LinkedList<String> getHosts() {
		LinkedList<String> hosts = new LinkedList<String>();
		for (ServiceInfo info : mServices) {
			hosts.add(info.getName());
		}
		return hosts;
	}
	
	public void jmDnsListener() {
		try {
			mJmDNS = JmDNS.create();
			mJmDNS.addServiceListener(mDnsType, mListener = new ServiceListener() {

				@Override
				public void serviceAdded(ServiceEvent event) {
					mJmDNS.requestServiceInfo(event.getType(), event.getName(), 1);
				}

				@Override
				public void serviceRemoved(ServiceEvent arg0) {
				}

				@Override
				public void serviceResolved(ServiceEvent serviceEvent) {
					ServiceInfo info = serviceEvent.getInfo();
					Inet4Address inet4[] = info.getInet4Addresses();
	            	if (inet4.length > 0) {
		                mServices.add(info);
		                
		                // Send a message to the connect activity
		                if (mConnectActivityHandler != null) {
			                Message msg = Message.obtain();
			                msg.obj = new ServiceFound();
			                mConnectActivityHandler.sendMessage(msg);
		                }
	            	}
				}
				
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}