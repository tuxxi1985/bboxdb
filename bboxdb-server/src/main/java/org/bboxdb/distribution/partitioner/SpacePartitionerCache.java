/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
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
package org.bboxdb.distribution.partitioner;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.bboxdb.distribution.region.DistributionRegionCallback;
import org.bboxdb.distribution.region.DistributionRegionIdMapper;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.network.client.BBoxDBException;

public class SpacePartitionerCache {
	
	/**
	 * Mapping between the string group and the group object
	 */
	protected final static Map<String, SpacePartitioner> groupGroupMap;
	
	/**
	 * The region mapper
	 */
	private final static Map<String, DistributionRegionIdMapper> distributionRegionIdMapper;
	
	/**
	 * The callbacks
	 */
	private final static Map<String, Set<DistributionRegionCallback>> callbacks;

	static {
		groupGroupMap = new HashMap<>();
		distributionRegionIdMapper = new HashMap<>();
		callbacks = new HashMap<>();
	}
	
	/**
	 * Get the distribution region for the given group name
	 * @param groupName
	 * @return
	 * @throws BBoxDBException 
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public static synchronized SpacePartitioner getSpacePartitionerForGroupName(final String groupName) throws BBoxDBException  {
		
		try {
			if(! groupGroupMap.containsKey(groupName)) {
				final ZookeeperClient zookeeperClient = ZookeeperClientFactory.getZookeeperClient();
				
				// Create callback list
				final Set<DistributionRegionCallback> callback = new CopyOnWriteArraySet<>();
				callbacks.put(groupName, callback);
				
				// Create region id mapper
				final DistributionRegionIdMapper mapper = new DistributionRegionIdMapper();
				distributionRegionIdMapper.put(groupName, mapper);

				final DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter 
					= new DistributionGroupZookeeperAdapter(zookeeperClient);
				
				final SpacePartitioner adapter = distributionGroupZookeeperAdapter.getSpaceparitioner(groupName, 
						callback, mapper);
				
				groupGroupMap.put(groupName, adapter);
			}
			
			return groupGroupMap.get(groupName);
		} catch (ZookeeperException | ZookeeperNotFoundException e) {
			throw new BBoxDBException(e);
		}
	}
}
