/*******************************************************************************
 *
 *    Copyright (C) 2015-2017 the BBoxDB project
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.distribution.membership;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BBoxDBInstanceManager {

	/**
	 * The event listener
	 */
	protected final List<BiConsumer<DistributedInstanceEvent, BBoxDBInstance>> listener 
		= new CopyOnWriteArrayList<>();
	
	/**
	 * The active BBoxDB instances
	 */
	protected final Map<InetSocketAddress, BBoxDBInstance> instances = new HashMap<>();
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(BBoxDBInstanceManager.class);

	/**
	 * The instance
	 */
	protected static BBoxDBInstanceManager instance;
	
	/**
	 * Get the instance
	 * @return
	 */
	public static synchronized BBoxDBInstanceManager getInstance() {
		if(instance == null) {
			instance = new BBoxDBInstanceManager();
		}
		
		return instance;
	}
	
	/**
	 * Private constructor to prevent instantiation
	 */
	private BBoxDBInstanceManager() {
	}
	
	/**
	 * Singletons are not clonable
	 */
	@Override
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException("Unable to clone a singleton");
	}
	
	/**
	 * Update the instance list, called from zookeeper client
	 * @param newInstances
	 */
	public void updateInstanceList(final Set<BBoxDBInstance> newInstances) {
		
		// Are members removed?
		final List<InetSocketAddress> deletedInstances = new ArrayList<>(instances.size());
		deletedInstances.addAll(instances.keySet());
		
		// Remove still existing instances from 'to delete list'
		for(final BBoxDBInstance newInstance : newInstances) {
			deletedInstances.remove(newInstance.getInetSocketAddress());
		}
		
		for(final InetSocketAddress inetSocketAddress : deletedInstances) {
			final BBoxDBInstance deletedInstance = instances.remove(inetSocketAddress);
			sendEvent(DistributedInstanceEvent.DELETED, deletedInstance);
		}

		for(final BBoxDBInstance instance : newInstances) {
			
			final InetSocketAddress inetSocketAddress = instance.getInetSocketAddress();
			
			// Any new member?
			if( ! instances.containsKey(inetSocketAddress)) {
				instances.put(inetSocketAddress, instance);
				sendEvent(DistributedInstanceEvent.ADD, instance);
			} else {
				// Changed member?
				if(! instances.get(inetSocketAddress).equals(instance)) {
					instances.put(inetSocketAddress, instance);
					sendEvent(DistributedInstanceEvent.CHANGED, instance);
				}
			}
		}
	}
	
	/**
	 * Send a event to all the listeners
	 * 
	 * @param event
	 */
	protected void sendEvent(final DistributedInstanceEvent event, final BBoxDBInstance instance) {
		logger.debug("Sending event: {}", event);
		listener.forEach((l) -> l.accept(event, instance));
	}
	
	/**
	 * Register a callback listener
	 * @param callback
	 */
	public void registerListener(final BiConsumer<DistributedInstanceEvent, BBoxDBInstance> callback) {
		listener.add(callback);
	}
	
	/**
	 * Remove a callback listener
	 * @param callback
	 */
	public void removeListener(final BiConsumer<DistributedInstanceEvent, BBoxDBInstance> callback) {
		listener.remove(callback);
	}
	
	/**
	 * Remove all listener
	 */
	public void removeAllListener() {
		listener.clear();
	}
	
	/**
	 * Get the list of the instances
	 * @return
	 */
	public List<BBoxDBInstance> getInstances() {
		return new ArrayList<BBoxDBInstance>(instances.values());
	}
	
	/**
	 * Disconnect from zookeeper, all instances are unregistered
	 */
	public void zookeeperDisconnect() {
		logger.debug("Zookeeper disconnected, sending delete events for all instances");
		instances.values().forEach((i) -> sendEvent(DistributedInstanceEvent.DELETED, i));
		instances.clear();
	}
}
