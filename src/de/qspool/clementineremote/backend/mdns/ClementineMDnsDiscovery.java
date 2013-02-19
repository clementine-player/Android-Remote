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

import java.net.Inet6Address;
import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.elements.ServiceFound;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ClementineMDnsDiscovery {
	NsdManager mNsdManager;
	NsdManager.ResolveListener mResolveListener;
	NsdServiceInfo mService;
	NsdManager.DiscoveryListener mDiscoveryListener;
	
	private static String SERVICE_TYPE = "_clementine._tcp.";
	
	private LinkedList<NsdServiceInfo> mServices;

	private Handler mConnectActivityHandler;
	
	public ClementineMDnsDiscovery(Handler handler) {
		mConnectActivityHandler = handler;
		
		mServices = new LinkedList<NsdServiceInfo>();
		initializeResolveListener();
		initializeDiscoveryListener();
	}
	
	public void discoverServices() {
		mNsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
	}
	
	public void stopServiceDiscovery() {
		mNsdManager.stopServiceDiscovery(mDiscoveryListener);
	}
	
	public LinkedList<NsdServiceInfo> getServices() {
		return mServices;
	}
	
	public LinkedList<String> getHosts() {
		LinkedList<String> hosts = new LinkedList<String>();
		for (NsdServiceInfo info : mServices) {
			// Replace \\032 for space, somehow the resolveService
			// method replaces all spaces
			hosts.add(info.getServiceName().replace("\\\\032", " "));
		}
		return hosts;
	}

	private void initializeDiscoveryListener() {
		mNsdManager = (NsdManager) App.mApp.getSystemService(Context.NSD_SERVICE);
		
	    // Instantiate a new DiscoveryListener
	    mDiscoveryListener = new NsdManager.DiscoveryListener() {

	        //  Called as soon as service discovery begins.
	        @Override
	        public void onDiscoveryStarted(String regType) {
	        }

	        @Override
	        public void onServiceFound(NsdServiceInfo service) {
	            // A service was found!  Do something with it.
	            if (service.getServiceType().equals(SERVICE_TYPE)
	             && service.getServiceName().contains("Clementine")) {
	                mNsdManager.resolveService(service, mResolveListener);
	            }
	        }

	        @Override
	        public void onServiceLost(NsdServiceInfo service) {
	            mServices.remove(service);
	        }

	        @Override
	        public void onDiscoveryStopped(String serviceType) {
	        	mServices.clear();
	        }

	        @Override
	        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
	        	mServices.clear();
	            mNsdManager.stopServiceDiscovery(this);
	        }

	        @Override
	        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
	        	mServices.clear();
	            mNsdManager.stopServiceDiscovery(this);
	        }

	    };
	}
	
	private void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
            	// ipv6 not yet supported
            	if (!(serviceInfo.getHost() instanceof Inet6Address)) {
	                mServices.add(serviceInfo);
	                
	                // Send a message to the connect activity
	                Message msg = Message.obtain();
	                msg.obj = new ServiceFound();
	                mConnectActivityHandler.sendMessage(msg);
            	}
            }
        };
    }
}
