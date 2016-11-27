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
package de.fernunihagen.dna.scalephant.storage.queryprocessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.scalephant.storage.ReadOnlyTupleStorage;
import de.fernunihagen.dna.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.scalephant.storage.entity.Tuple;
import de.fernunihagen.dna.scalephant.storage.sstable.SSTableManager;
import de.fernunihagen.dna.scalephant.storage.sstable.TupleHelper;
import de.fernunihagen.dna.scalephant.storage.sstable.reader.SSTableFacade;

public class SSTableQueryProcessor {

	/**
	 * The predicate to evaluate
	 */
	protected final Predicate predicate;
	
	/**
	 * The sstable manager
	 */
	protected final SSTableManager ssTableManager;
	
	/**
	 * The list of acquired facades
	 */
	protected final List<SSTableFacade> aquiredFacades;
	
	/**
	 * Is the iterator ready?
	 */
	protected boolean ready;
	
	/**
	 * The seen tuples<Key, Timestamp> map
	 */
	protected final Map<String, Long> seenTuples;
	
	/**
	 * The unprocessed storages
	 */
	protected final List<ReadOnlyTupleStorage> unprocessedStorages;

	/**
	 * The Logger
	 */
	protected static final Logger logger = LoggerFactory.getLogger(SSTableQueryProcessor.class);
	
	
	public SSTableQueryProcessor(final Predicate predicate, final SSTableManager ssTableManager) {
		this.predicate = predicate;
		this.ssTableManager = ssTableManager;
		this.aquiredFacades = new ArrayList<SSTableFacade>();
		this.ready = false;
		this.seenTuples = new HashMap<String, Long>();
		this.unprocessedStorages = new LinkedList<ReadOnlyTupleStorage>();
	}
	
	public CloseableIterator<Tuple> iterator() {
		
		aquireTables();
		
		return new CloseableIterator<Tuple>() {

			/**
			 * The active iterator
			 */
			protected Iterator<Tuple> activeIterator = null;
			
			/**
			 * The active storage
			 */
			protected ReadOnlyTupleStorage activeStorage = null;
			
			/**
			 * The next precomputed tuple
			 */
			protected Tuple nextTuple;
			
			protected void setupNewIterator() {
				activeIterator = null;
				activeStorage = null;

				// Find next iterator 
				while(! unprocessedStorages.isEmpty()) {
					activeStorage = unprocessedStorages.remove(0);
					activeIterator = activeStorage.getMatchingTuples(predicate);
					
					if(activeIterator.hasNext()) {
						return;
					}
				}
			}
			
			protected void setupNextTuple() throws StorageManagerException {
				if(ready == false) {
					throw new IllegalStateException("Iterator is not ready");
				}

				nextTuple = null;
				
				while(nextTuple == null) {
					if(activeIterator == null || ! activeIterator.hasNext()) {
						setupNewIterator();
					}
					
					// All iterators are exhausted
					if(activeIterator == null) {
						return;
					}
					
					final Tuple possibleTuple = activeIterator.next();
										
					if(seenTuples.containsKey(possibleTuple.getKey())) {
						final long oldTimestamp = seenTuples.get(possibleTuple.getKey());
						if(oldTimestamp < possibleTuple.getTimestamp()) {
							logger.warn("Got newer tuple {} than {}", possibleTuple, oldTimestamp);
							seenTuples.put(possibleTuple.getKey(), possibleTuple.getTimestamp());
						}
					} else {
						// Set nextTuple != null to exit the loop
						nextTuple = getMostRecentVersionForTuple(possibleTuple);
						seenTuples.put(possibleTuple.getKey(), possibleTuple.getTimestamp());
					}
				}
			}
			
			/**
			 * Get the most recent version of the tuple
			 * @param tuple
			 * @return
			 * @throws StorageManagerException 
			 */
			public Tuple getMostRecentVersionForTuple(final Tuple tuple) throws StorageManagerException {
				
				// Get the most recent version of the tuple
				// e.g. Memtables can contain multiple versions of the key
				// The iterator can return an outdated version
				Tuple resultTuple = activeStorage.get(tuple.getKey());
				
				for(final ReadOnlyTupleStorage readOnlyTupleStorage : unprocessedStorages) {
					if(readOnlyTupleStorage.getNewestTupleTimestamp() > resultTuple.getTimestamp()) {
						final Tuple possibleTuple = readOnlyTupleStorage.get(tuple.getKey());
						resultTuple = TupleHelper.returnMostRecentTuple(resultTuple, possibleTuple);
					}
				}
				
				return resultTuple;
			}
			
			@Override
			public boolean hasNext() {
				try {
					if(nextTuple == null) {
						setupNextTuple();
					}
				} catch (StorageManagerException e) {
					logger.error("Got an exception while locating next tuple", e);
				}
				
				return nextTuple != null;
			}

			@Override
			public Tuple next() {
				
				if(ready == false) {
					throw new IllegalStateException("Iterator is not ready");
				}
				
				if(nextTuple == null) {
					throw new IllegalStateException("Next tuple is null, did you really call hasNext() before?");
				}
				
				final Tuple resultTuple = nextTuple;
				nextTuple = null;
				return resultTuple;
			}

			@Override
			public void close() throws Exception {
				releaseTables();
			}
			
		};
	}

	/**
	 * Try to acquire all needed tables
	 */
	protected void aquireTables() {
		final int retrys = 10;
		
		ready = false;
		
		for(int execution = 0; execution < retrys; execution++) {
			
			// Release the previous acquired tables
			releaseTables();
			
			aquiredFacades.clear();
			boolean allTablesAquired = true;
			
			for(final SSTableFacade facade : ssTableManager.getSstableFacades()) {
				boolean canBeUsed = facade.acquire();
				
				if(! canBeUsed ) {
					allTablesAquired = false;
					break;
				}
				
				aquiredFacades.add(facade);
			}
			
			if(allTablesAquired == true) {
				prepareUnprocessedStorage();
				ready = true;
				return;
			}
		}
		 
		logger.warn("Unable to aquire all sstables with {} retries", retrys);
	}

	/**
	 * Prepare the unprocessed storage list
	 */
	void prepareUnprocessedStorage() {
		unprocessedStorages.add(ssTableManager.getMemtable());
		unprocessedStorages.addAll(ssTableManager.getUnflushedMemtables());
		unprocessedStorages.addAll(aquiredFacades);
		
		// Sort tables regarding the newest tuple timestamp 
		// The newest storage should be on top of the list
		unprocessedStorages.sort((storage1, storage2) 
				-> Long.compare(storage2.getNewestTupleTimestamp(), 
						        storage1.getNewestTupleTimestamp()));
	}
	
	/**
	 * Release all acquired tables
	 */
	protected void releaseTables() {
		ready = false;
		for(final SSTableFacade facade : aquiredFacades) {
			facade.release();
		}
	}
}