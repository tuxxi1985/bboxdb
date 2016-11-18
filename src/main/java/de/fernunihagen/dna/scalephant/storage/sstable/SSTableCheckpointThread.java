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
package de.fernunihagen.dna.scalephant.storage.sstable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.scalephant.ScalephantConfiguration;
import de.fernunihagen.dna.scalephant.ScalephantConfigurationManager;
import de.fernunihagen.dna.scalephant.distribution.DistributionGroupCache;
import de.fernunihagen.dna.scalephant.distribution.DistributionRegion;
import de.fernunihagen.dna.scalephant.distribution.DistributionRegionHelper;
import de.fernunihagen.dna.scalephant.distribution.membership.DistributedInstance;
import de.fernunihagen.dna.scalephant.distribution.zookeeper.ZookeeperClient;
import de.fernunihagen.dna.scalephant.distribution.zookeeper.ZookeeperClientFactory;
import de.fernunihagen.dna.scalephant.distribution.zookeeper.ZookeeperException;
import de.fernunihagen.dna.scalephant.network.client.ScalephantException;
import de.fernunihagen.dna.scalephant.storage.Memtable;
import de.fernunihagen.dna.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.scalephant.util.Stoppable;

public class SSTableCheckpointThread implements Runnable, Stoppable {

	/**
	 * The sstable manager
	 */
	protected final SSTableManager ssTableManager;
	
	/**
	 * The maximal number of seconds for data to stay in memory
	 */
	protected final long maxUncheckpointedMiliseconds;
	
	/**
	 * The run variable
	 */
	protected volatile boolean run;

	/**
	 * The name of the local instance
	 */
	protected DistributedInstance localInstance;
	
	/**
	 * The distribution region of the sstable
	 */
	protected DistributionRegion distributionRegion = null;
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SSTableCheckpointThread.class);

	public SSTableCheckpointThread(final int maxUncheckpointedSeconds, final SSTableManager ssTableManager) {
		this.maxUncheckpointedMiliseconds = TimeUnit.SECONDS.toMillis(maxUncheckpointedSeconds);
		this.ssTableManager = ssTableManager;
		this.run = true;
		
		// Local instance
		final ScalephantConfiguration scalephantConfiguration = ScalephantConfigurationManager.getConfiguration();
		this.localInstance = ZookeeperClientFactory.getLocalInstanceName(scalephantConfiguration);
	
		try {
			final ZookeeperClient zookeeperClient = ZookeeperClientFactory.getZookeeperClientAndInit();
			final DistributionRegion distributionGroupRoot = DistributionGroupCache.getGroupForTableName(ssTableManager.getSSTableName().getFullname(), zookeeperClient);
			distributionRegion = DistributionRegionHelper.getDistributionRegionForNamePrefix(distributionGroupRoot, ssTableManager.getSSTableName().getNameprefix());
		} catch (ZookeeperException | ScalephantException e) {
			logger.warn("Unable to find distribution region: " , e);
		}
	}

	@Override
	public void run() {
		
		while(run) {
			logger.debug("Executing checkpoint thread for: " + ssTableManager.getSSTableName());
			
			createCheckpoint();
		
			try {
				Thread.sleep(SSTableConst.CHECKPOINT_THREAD_DELAY);
			} catch (InterruptedException e) {
				logger.info("Got interrupted exception, stopping thread");
				return;
			}
		}
	}
	
	/**
	 * Decide if a new checkpoint is needed
	 * @return
	 */
	protected boolean isCheckpointNeeded() {
		
		final List<Memtable> memtablesToCheck = new ArrayList<Memtable>();
		memtablesToCheck.add(ssTableManager.getMemtable());
		memtablesToCheck.addAll(ssTableManager.getUnflushedMemtables());
	
		for(final Memtable memtable : memtablesToCheck) {
			long memtableCreated = memtable.getCreatedTimestamp();
	
			// Active memtable is to old
			if(memtableCreated + maxUncheckpointedMiliseconds < System.currentTimeMillis()) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Create a new checkpoint, this means flush all old memtables to disk
	 */
	protected void createCheckpoint() {
		try {
			// Is a new checkpoint needed?
			if(! isCheckpointNeeded()) {
				return;
			}
			
			final Memtable activeMemtable = ssTableManager.getMemtable();
			logger.info("Creating a checkpoint for: " + ssTableManager.getSSTableName());
			ssTableManager.flushMemtable();
			
			final List<Memtable> unflushedMemtables = ssTableManager.getUnflushedMemtables();
			
			// Wait until the active memtable is flushed to disk
			synchronized (unflushedMemtables) {
				while(unflushedMemtables.contains(activeMemtable)) {
					unflushedMemtables.wait();
				}
			}
			
			final long createdTimestamp = activeMemtable.getCreatedTimestamp();
			updateCheckpointDate(createdTimestamp);
			
			logger.info("Create checkpoint DONE for: " + ssTableManager.getSSTableName() + " timestamp " + createdTimestamp);
		} catch (StorageManagerException e) {
			logger.warn("Got an exception while creating checkpoint", e);
		} catch (InterruptedException e) {
			logger.warn("Got an exception while creating checkpoint", e);
		} catch (ZookeeperException e) {
			logger.error("Got an exception while updating checkpoint", e);
		}
	}

	/**
	 * Update the checkpoint date (e.g. propagate checkpoint to zookeeper)
	 * @param createdTimestamp
	 * @throws ZookeeperException 
	 */
	protected void updateCheckpointDate(final long checkpointTimestamp) throws ZookeeperException {
		if(distributionRegion != null) {
			final ZookeeperClient zookeeperClient = ZookeeperClientFactory.getZookeeperClient();
			zookeeperClient.setCheckpointForDistributionRegion(distributionRegion, localInstance, checkpointTimestamp);
		}
	}

	@Override
	public void stop() {
		run = false;
	}

}
