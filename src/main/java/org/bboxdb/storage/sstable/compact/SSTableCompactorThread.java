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
package org.bboxdb.storage.sstable.compact;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bboxdb.distribution.regionsplit.RegionSplitStrategy;
import org.bboxdb.distribution.regionsplit.RegionSplitStrategyFactory;
import org.bboxdb.storage.ReadOnlyTupleStorage;
import org.bboxdb.storage.StorageManagerException;
import org.bboxdb.storage.entity.SSTableName;
import org.bboxdb.storage.sstable.SSTableManager;
import org.bboxdb.storage.sstable.SSTableWriter;
import org.bboxdb.storage.sstable.reader.SSTableFacade;
import org.bboxdb.storage.sstable.reader.SSTableKeyIndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSTableCompactorThread implements Runnable {
	
	/**
	 * The corresponding SSTable manager
	 */
	protected final SSTableManager sstableManager;
	
	/**
	 * The merge strategy
	 */
	protected final MergeStrategy mergeStragegy;
	
	/**
	 * The name of the thread
	 */
	protected final String threadname;
	
	/**
	 * The region splitter
	 */
	protected RegionSplitStrategy regionSplitter;
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SSTableCompactorThread.class);

	public SSTableCompactorThread(final SSTableManager ssTableManager) {
		this.sstableManager = ssTableManager;
		this.mergeStragegy = new SimpleMergeStrategy();
		this.threadname = sstableManager.getSSTableName().getFullname();
		this.regionSplitter = RegionSplitStrategyFactory.getInstance();
	}

	/**
	 * Compact our SSTables
	 * 
	 */
	@Override
	public void run() {
		logger.info("Starting new compact thread for: {}", threadname);

		try {
			executeThread();
		} catch(Throwable e) {
			logger.error("Got an uncaught exception", e);
		}
		
		logger.info("Compact thread for: {} is done", threadname);
	}

	/**
	 * Execute the compactor thread
	 */
	protected void executeThread() {
		
		initRegionSplitter();
	
		while(sstableManager.isReady()) {

			try {	
				Thread.sleep(mergeStragegy.getCompactorDelay());
				logger.debug("Executing compact thread for: {}", threadname);

				// Create a copy to ensure, that the list of facades don't change
				// during the compact run.
				final List<SSTableFacade> facades = new ArrayList<SSTableFacade>(sstableManager.getTupleStoreInstances().getSstableFacades());
				final MergeTask mergeTask = mergeStragegy.getMergeTask(facades);
					
				try {
					mergeSSTables(mergeTask.getMinorCompactTables(), false);
					mergeSSTables(mergeTask.getMajorCompactTables(), true);				
				} catch (Exception e) {
					logger.error("Error while merging tables", e);
				} 
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} 
		}
		
		logger.info("Compact thread for: {} is done", threadname);
	}

	/**
	 * Init the region spliter, if needed (distributed version if a table)
	 */
	protected void initRegionSplitter() {
		try {
			if(sstableManager.getSSTableName().isDistributedTable()) {
				regionSplitter.initFromSSTablename(sstableManager.getSSTableName());
			}
		} catch (StorageManagerException e) {
			logger.error("Got exception when init region splitter", e);
		}
	}

	/**
	 * Calculate max the number of entries in the output
	 * @param tables
	 * @return
	 */
	public long calculateNumberOfEntries(final List<SSTableFacade> facades) {
		return facades
			.stream()
			.map(SSTableFacade::getSsTableKeyIndexReader)
			.mapToInt(SSTableKeyIndexReader::getNumberOfEntries)
			.sum();
	}

	/**
	 * Merge multipe facades into a new one
	 * @param reader1
	 * @param reader2
	 * @throws StorageManagerException
	 */
	protected void mergeSSTables(final List<SSTableFacade> facades, final boolean majorCompaction) throws StorageManagerException {
	
		if(facades == null || facades.isEmpty()) {
			return;
		}
		
		final String directory = facades.get(0).getDirectory();
		final String name = facades.get(0).getName();
		final SSTableName ssTableName = new SSTableName(name);
		
		final long estimatedMaxNumberOfEntries = calculateNumberOfEntries(facades);
		final int tablenumber = sstableManager.increaseTableNumber();
		final SSTableWriter writer = new SSTableWriter(directory, ssTableName, tablenumber, estimatedMaxNumberOfEntries);
		
		final List<SSTableKeyIndexReader> reader = new ArrayList<SSTableKeyIndexReader>();
		for(final SSTableFacade facade : facades) {
			reader.add(facade.getSsTableKeyIndexReader());
		}
		
		// Log the compact call
		if(logger.isInfoEnabled()) {
			writeMergeLog(facades, tablenumber, majorCompaction);
		}
		
		// Run the compact process
		final SSTableCompactor ssTableCompactor = new SSTableCompactor(reader, writer);
		ssTableCompactor.setMajorCompaction(majorCompaction);
		final boolean compactSuccess = ssTableCompactor.executeCompactation();
		
		if(! compactSuccess) {
			logger.error("Error during compactation");
			return;
		} else {
			final float mergeFactor = (float) ssTableCompactor.getWrittenTuples() / (float) ssTableCompactor.getReadTuples();
			
			logger.info("Compactation done. Read {} tuples, wrote {} tuples (expected {}). Factor {}", 
					ssTableCompactor.getReadTuples(), ssTableCompactor.getWrittenTuples(), 
					estimatedMaxNumberOfEntries, mergeFactor);
		}
		
		registerNewFacadeAndDeleteOldInstances(facades, directory, ssTableName, tablenumber);
		
		if(majorCompaction && ssTableName.isDistributedTable()) {
			testAndPerformTableSplit(ssTableCompactor.getWrittenTuples());
		}
	}

	/**
	 * Test and perform an table split if needed
	 * @param totalWrittenTuples 
	 */
	protected void testAndPerformTableSplit(final int totalWrittenTuples) {
		
		logger.info("Test for table split: {} total tuples {}", threadname, totalWrittenTuples);
				
		if(regionSplitter.isSplitNeeded(totalWrittenTuples)) {
			// Execute the split operation in an own thread, to survive the sstable manager
			// stop call. This will stop (this) compact thread
			final Thread splitThread = new Thread(regionSplitter);
			splitThread.setName("Split thread for: {}" + threadname);
			splitThread.start();
		}
		
	}

	/**
	 * Register a new sstable facade and delete the old ones
	 * @param tables
	 * @param directory
	 * @param name
	 * @param tablenumber
	 * @throws StorageManagerException
	 */
	protected void registerNewFacadeAndDeleteOldInstances(final List<SSTableFacade> tables, final String directory,
			final SSTableName name, final int tablenumber) throws StorageManagerException {
		// Create a new facade and remove the old ones
		final SSTableFacade newFacade = new SSTableFacade(directory, name, tablenumber);
		newFacade.init();
		
		// Register the new sstable reader
		sstableManager.getTupleStoreInstances().replaceCompactedSStables(newFacade, tables);

		// Unregister and delete the files
		for(final ReadOnlyTupleStorage facade : tables) {
			facade.deleteOnClose();
		}
	}

	/***
	 * Write info about the merge run into log
	 * @param facades
	 * @param tablenumber
	 */
	protected void writeMergeLog(final List<SSTableFacade> facades, final int tablenumber, 
			final boolean majorCompaction) {
		
		final String formatedFacades = facades
				.stream()
				.mapToInt(SSTableFacade::getTablebumber)
				.mapToObj(Integer::toString)
				.collect(Collectors.joining(",", "[", "]"));
		
		logger.info("Merging (major: {}) {} into {}", majorCompaction, formatedFacades, tablenumber);
	}
}