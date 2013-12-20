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
	private MulticastLock mMulticastLock;
	private Thread mThread;
	
	private final String mDnsType = "_clementine._tcp.local.";
	private ServiceListener mListener;
	private LinkedList<ServiceInfo> mServices;

	private Handler mConnectActivityHandler;
	
	public ClementineMDnsDiscovery(Handler handler) {
		mConnectActivityHandler = handler;
		
		mServices = new LinkedList<ServiceInfo>();
	}
	
	/**
	 * Discover services on the network
	 */
	public void discoverServices() {
		mThread = new Thread(new Runnable() {
			@Override
			public void run() {
				WifiManager wifi = (WifiManager)
			              App.mApp.getSystemService(android.content.Context.WIFI_SERVICE);
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
				if (mMulticastLock != null && mMulticastLock.isHeld())
					mMulticastLock.release();
			}
    		
    	});
		mThread.start();
	}
	
	/**
	 * Get the services on the network
	 * @return A LinkedList of ServiceInfo with the services.
	 */
	public LinkedList<ServiceInfo> getServices() {
		return mServices;
	}
	
	/**
	 * Get the Hosts on the network
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
			mJmDNS = JmDNS.create();
			mJmDNS.addServiceListener(mDnsType, mListener = new ServiceListener() {

				@Override
				public void serviceAdded(ServiceEvent serviceEvent) {
					mJmDNS.requestServiceInfo(serviceEvent.getType(), serviceEvent.getName(), 1);
				}

				@Override
				public void serviceRemoved(ServiceEvent serviceEvent) {
					ServiceInfo info = serviceEvent.getInfo();
					mServices.remove(info);
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
		} catch (Exception e) {
			mJmDNS = null;
		}
	}
}