/*******************************************************************************
 *
 *    Copyright (C) 2015-2016
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
package org.bboxdb.distribution.mode;

import java.util.Collection;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.membership.DistributedInstance;
import org.bboxdb.distribution.nameprefix.NameprefixInstanceManager;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNodeNames;
import org.bboxdb.storage.entity.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KDtreeZookeeperAdapter implements Watcher {
	
	/**
	 * The zookeeper client
	 */
	protected final ZookeeperClient zookeeperClient;
	
	/**
	 * The distribution group adapter
	 */
	protected final DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter;
	
	/**
	 * The root node of the K-D-Tree
	 */
	protected final DistributionRegion rootNode;
	
	/**
	 * The mutex for sync operations
	 */
	protected final Object MUXTEX = new Object();
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(KDtreeZookeeperAdapter.class);

	public KDtreeZookeeperAdapter(final ZookeeperClient zookeeperClient,
			final DistributionGroupZookeeperAdapter distributionGroupAdapter, 
			final DistributionRegion rootNode) throws ZookeeperException {
		
		this.zookeeperClient = zookeeperClient;
		this.distributionGroupZookeeperAdapter = distributionGroupAdapter;
		this.rootNode = rootNode;

		final String path = distributionGroupZookeeperAdapter.getDistributionGroupPath(rootNode.getDistributionGroupName().getFullname());

		readDistributionGroupRecursive(path, rootNode);
	}
	
	/**
	 * Get the root node
	 * @return
	 */
	public DistributionRegion getRootNode() {
		return rootNode;
	}

	@Override
	public void process(final WatchedEvent event) {
		
		// Ignore events like connected and disconnected
		if(event == null || event.getPath() == null) {
			return;
		}

		if(event.getPath().endsWith(ZookeeperNodeNames.NAME_SYSTEMS)) {
			handleSystemNodeUpdateEvent(event);
		} else if(event.getPath().endsWith(ZookeeperNodeNames.NAME_STATE)) {
			handleNodeUpdateEvent(event);
		} else {
			logger.info("Ingoring event for path: {}" , event.getPath());
		}
	}
	
	/**
	 * Handle node updates
	 * @param event
	 */
	protected void handleNodeUpdateEvent(final WatchedEvent event) {
		final String path = event.getPath().replace("/" + ZookeeperNodeNames.NAME_STATE, "");
		final DistributionRegion nodeToUpdate = distributionGroupZookeeperAdapter.getNodeForPath(rootNode, path);
		
		try {
			if(! distributionGroupZookeeperAdapter.isDistributionGroupRegistered(rootNode.getDistributionGroupName().getFullname())) {
				logger.info("Distribution group was unregistered, ignore event");
				return;
			}
			
			readDistributionGroupRecursive(path, nodeToUpdate);
		} catch (ZookeeperException e) {
			logger.warn("Got exception while updating node for: " + path, e);
		}
	}

	/**
	 * Handle system update events
	 * @param event
	 */
	protected void handleSystemNodeUpdateEvent(final WatchedEvent event) {
		final String path = event.getPath().replace("/" + ZookeeperNodeNames.NAME_SYSTEMS, "");
		
		final DistributionRegion nodeToUpdate = distributionGroupZookeeperAdapter.getNodeForPath(rootNode, path);
		
		try {
			updateSystemsForRegion(nodeToUpdate);
		} catch (ZookeeperException e) {
			logger.warn("Got exception while updating systems for: " + path, e);
		}
	}

	/**
	 * Split the node at the given position
	 * @param regionToSplit
	 * @param splitPosition
	 * @throws ZookeeperException
	 */
	public void splitNode(final DistributionRegion regionToSplit, final float splitPosition) throws ZookeeperException {
		logger.debug("Write split at pos {} into zookeeper", splitPosition);
		final String zookeeperPath = distributionGroupZookeeperAdapter.getZookeeperPathForDistributionRegion(regionToSplit);
		
		final String leftPath = zookeeperPath + "/" + ZookeeperNodeNames.NAME_LEFT;
		createNewChild(leftPath);
		
		final String rightPath = zookeeperPath + "/" + ZookeeperNodeNames.NAME_RIGHT;
		createNewChild(rightPath);
		
		// Write split position and update state
		distributionGroupZookeeperAdapter.setSplitPositionForPath(zookeeperPath, splitPosition);
		distributionGroupZookeeperAdapter.setStateForDistributionGroup(zookeeperPath, NodeState.SPLITTING);
		distributionGroupZookeeperAdapter.setStateForDistributionGroup(leftPath, NodeState.ACTIVE);
		distributionGroupZookeeperAdapter.setStateForDistributionGroup(rightPath, NodeState.ACTIVE);
		
		// Wait for zookeeper callback
		while(! isSplitForNodeComplete(regionToSplit)) {
			logger.debug("Wait for zookeeper callback for split for: {}", regionToSplit);
			synchronized (MUXTEX) {
				try {
					MUXTEX.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.warn("Unable to wait for split for); {}", regionToSplit);
				}
			}
		}
	}
	
	/**
	 * Is the split for the given node complete?
	 * @param region
	 * @return
	 */
	protected boolean isSplitForNodeComplete(final DistributionRegion region) {
		if(region.getLeftChild() == null) {
			return false;
		}
		
		if(region.getRightChild() == null) {
			return false;
		}
		
		if(region.getLeftChild().getState() != NodeState.ACTIVE) {
			return false;
		}
		
		if(region.getRightChild().getState() != NodeState.ACTIVE) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Create a new child
	 * @param path
	 * @throws ZookeeperException
	 */
	protected void createNewChild(final String path) throws ZookeeperException {
		logger.debug("Creating: {}", path);

		zookeeperClient.createPersistentNode(path, "".getBytes());
		
		final int namePrefix = distributionGroupZookeeperAdapter.getNextTableIdForDistributionGroup(rootNode.getName());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_NAMEPREFIX, 
				Integer.toString(namePrefix).getBytes());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_SYSTEMS, 
				"".getBytes());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_STATE, 
				NodeState.CREATING.getStringValue().getBytes());

		distributionGroupZookeeperAdapter.setStateForDistributionGroup(path, NodeState.ACTIVE);
	}
	
	/**
	 * Read the distribution group in a recursive way
	 * @param path
	 * @param region
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 * @throws KeeperException 
	 */
	protected void readDistributionGroupRecursive(final String path, final DistributionRegion region) throws ZookeeperException {
			
			logger.debug("Reading path: {}", path);
			
			final int namePrefix = zookeeperClient.getNamePrefixForPath(path);
			region.setNameprefix(namePrefix);

			// Handle systems and mappings
			updateSystemsForRegion(region);
			
			// Handle state
			final NodeState stateForDistributionRegion = distributionGroupZookeeperAdapter.getStateForDistributionRegion(path, this);
			region.setState(stateForDistributionRegion);

			// If the node is not split, stop recursion
			if(distributionGroupZookeeperAdapter.isGroupSplitted(path)) {
				final float splitFloat = distributionGroupZookeeperAdapter.getSplitPositionForPath(path);
				region.setSplit(splitFloat);
				
				readDistributionGroupRecursive(path + "/" + ZookeeperNodeNames.NAME_LEFT, region.getLeftChild());
				readDistributionGroupRecursive(path + "/" + ZookeeperNodeNames.NAME_RIGHT, region.getRightChild());
			}
	
			// Wake up all pending waiters
			synchronized (MUXTEX) {
				MUXTEX.notifyAll();
			}
	}

	/**
	 * Read and update systems for region
	 * @param region
	 * @throws ZookeeperException
	 */
	protected void updateSystemsForRegion(final DistributionRegion region)
			throws ZookeeperException {
		final Collection<DistributedInstance> systemsForDistributionRegion 
			= distributionGroupZookeeperAdapter.getSystemsForDistributionRegion(region, this);
		
		region.setSystems(systemsForDistributionRegion);
		updateLocalMappings(region, systemsForDistributionRegion);
	}
	
	/**
	 * Update the local mappings with the systems for region
	 * @param region
	 * @param systems
	 */
	protected void updateLocalMappings(final DistributionRegion region, 
			final Collection<DistributedInstance> systems) {
		
		if(zookeeperClient.getInstancename() == null) {
			logger.debug("Local instance name is not set, so no local mapping is possible");
			return;
		}
		
		final DistributedInstance localInstance = zookeeperClient.getInstancename();
		
		// Add the mapping to the nameprefix mapper
		for(final DistributedInstance instance : systems) {
			if(instance.socketAddressEquals(localInstance)) {
				final int nameprefix = region.getNameprefix();
				final BoundingBox converingBox = region.getConveringBox();
				
				logger.info("Add local mapping for: {} / nameprefix {}", region, nameprefix);
				NameprefixInstanceManager.getInstance(region.getDistributionGroupName()).addMapping(nameprefix, converingBox);
			}
		}
	}
}