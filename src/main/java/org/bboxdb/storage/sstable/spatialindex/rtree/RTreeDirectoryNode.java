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
package org.bboxdb.storage.sstable.spatialindex.rtree;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.sstable.spatialindex.BoundingBoxEntity;
import org.bboxdb.storage.sstable.spatialindex.SpatialIndexEntry;
import org.bboxdb.util.io.DataEncoderHelper;

import com.google.common.io.ByteStreams;

public class RTreeDirectoryNode implements BoundingBoxEntity {
	
	/**
	 * The directory node childs
	 */
	protected final List<RTreeDirectoryNode> directoryNodeChilds;
	
	/**
	 * The leaf node childs
	 */
	protected final List<SpatialIndexEntry> indexEntries;
	
	/**
	 * The root node 'root' reference
	 */
	protected final static RTreeDirectoryNode PARENT_ROOT = null;
	
	/**
	 * The bounding box of the node
	 */
	protected BoundingBox boundingBox;
	
	/**
	 * The parent node
	 */
	protected RTreeDirectoryNode parentNode;

	/**
	 * The id of the node
	 */
	protected final int nodeId;
	
	public RTreeDirectoryNode(final int nodeId) {
		this.parentNode = PARENT_ROOT;
		this.nodeId = nodeId;
		this.directoryNodeChilds = new ArrayList<>();
		this.indexEntries = new ArrayList<>();
	}

	/**
	 * Return the bounding box of the node
	 * @return
	 */
	@Override
	public BoundingBox getBoundingBox() {
		return boundingBox;
	}
	
	/**
	 * Get the parent node
	 * @return
	 */
	public RTreeDirectoryNode getParentNode() {
		return parentNode;
	}
	
	/**
	 * Set a new parent node
	 * @param parentNode
	 */
	public void setParentNode(final RTreeDirectoryNode parentNode) {
		this.parentNode = parentNode;
	}

	@Override
	public String toString() {
		return "RTreeDirectoryNode [boundingBox=" + boundingBox + ", nodeId=" + nodeId + "]";
	}

	/**
	 * Add a directory node as child
	 * @param rTreeDirectoryNode
	 */
	public void addDirectoryNodeChild(final RTreeDirectoryNode rTreeDirectoryNode) {
		
		// We can carry directory nodes or index entries
		assert (indexEntries.isEmpty());
		
		directoryNodeChilds.add(rTreeDirectoryNode);
	}
	
	/**
	 * Remove child directory node
	 * @param rTreeDirectoryNode
	 * @return
	 */
	public boolean removeDirectoryNodeChild(final RTreeDirectoryNode rTreeDirectoryNode) {
		return directoryNodeChilds.remove(rTreeDirectoryNode);
	}
	
	/**
	 * Remove child leaf node 
	 * @param rTreeLeafNode
	 * @return
	 */
	public boolean removeIndexEntry(final BoundingBoxEntity entry) {
		
		// We can carry directory nodes or index entries
		assert (directoryNodeChilds.isEmpty());
		
		return indexEntries.remove(entry);
	}
	
	/**
	 * Recalculate the bounding box of all entries
	 */
	public void updateBoundingBox() {
		final List<BoundingBox> boundingBoxes = getAllChildBoundingBoxes();
		
		// Calculate bounding box
		this.boundingBox = BoundingBox.getCoveringBox(boundingBoxes);
	}

	/**
	 * Get the bounding boxes of all childs
	 * @return
	 */
	public List<BoundingBox> getAllChildBoundingBoxes() {

		// Get all Bounding boxes
		return Stream.concat(directoryNodeChilds.stream(), indexEntries.stream())
				.map(b -> b.getBoundingBox())
				.collect(Collectors.toList());
	}
	
	/**
	 * Find the best node for insert 
	 * @param entryBox
	 * @return
	 */
	protected RTreeDirectoryNode findBestNodeForInsert(final BoundingBox entryBox) {
		RTreeDirectoryNode bestNode = null;
		double bestEnlargement = -1;
		
		for(final RTreeDirectoryNode node : directoryNodeChilds) {
			final BoundingBox nodeBoundingBox = node.getBoundingBox();
			final double nodeEnlargement = nodeBoundingBox.calculateEnlargement(entryBox);

			if(bestNode == null) {
				bestNode = node;
				bestEnlargement = nodeEnlargement;
				continue;
			}
			
			if(nodeEnlargement < bestEnlargement) {
				bestNode = node;
				bestEnlargement = nodeEnlargement;
				continue;
			}
			
			if(nodeEnlargement == bestEnlargement) {
				if(bestNode.getSize() > node.getSize()) {
					bestNode = node;
					bestEnlargement = nodeEnlargement;
					continue;
				}
			}
		}
		
		return bestNode;
	}

	/**
	 * Get all entries for a given region
	 * @param boundingBox
	 * @return
	 */
	public List<SpatialIndexEntry> getEntriesForRegion(final BoundingBox boundingBox) {
		
		assert(boundingBox != null) : "Query bounding box has to be != null";
		assert(indexEntries != null) : "Index entries has to be != null";
		assert(directoryNodeChilds != null) : "Directory node childs has to be != null";
		
		try {
		final List<SpatialIndexEntry> nodeMatches = indexEntries
			.stream()
			.filter(c -> c.getBoundingBox().overlaps(boundingBox))
			.collect(Collectors.toList());
		
		final List<SpatialIndexEntry> childMatches = directoryNodeChilds
			.stream()
			.filter(c -> c.getBoundingBox().overlaps(boundingBox))
			.map(c -> c.getEntriesForRegion(boundingBox))
			.flatMap(List::stream)
			.collect(Collectors.toList());

		return Stream.concat(nodeMatches.stream(), childMatches.stream())
				.collect(Collectors.toList());
		} catch(NullPointerException e) {
			System.out.println(e);
			System.out.println(directoryNodeChilds);
			return null;
		}
	}
	
	/**
	 * Test the bounding box covering (usefull for test purposes)
	 */
	public void testCovering() {
		
		for(final SpatialIndexEntry entry : indexEntries) {
			if(! boundingBox.isCovering(entry.getBoundingBox())) {
				System.err.println("Error 1");
			}
			
			if(! boundingBox.overlaps(entry.getBoundingBox())) {
				System.err.println("Error 2");
			}
		}
		
		for(final RTreeDirectoryNode entry : directoryNodeChilds) {
			
			assert boundingBox != null;
			assert entry.getBoundingBox() != null : "Null BBox: " + entry;

			if(! boundingBox.isCovering(entry.getBoundingBox())) {
				System.err.println("Error 3a: " + boundingBox + " does not cover" + entry.getBoundingBox());
				entry.updateBoundingBox();
				updateBoundingBox();
				
				if(! boundingBox.isCovering(entry.getBoundingBox())) {

					System.err.println("Error 3b: " + boundingBox + " does not cover" + entry.getBoundingBox());

					System.err.println(getAllChildBoundingBoxes());
				}
			}
			
			if(! boundingBox.overlaps(entry.getBoundingBox())) {
				System.err.println("Error 4");
			}
			
			entry.testCovering();
		}
	}
	
	/**
	 * Get the size of the node
	 * @return
	 */
	public int getSize() {
		return indexEntries.size() + directoryNodeChilds.size();
	}
	
	/**
	 * Is this a leaf node
	 */
	public boolean isLeafNode() {
		return directoryNodeChilds.isEmpty();
	}
	
	/**
	 * Get the directory node childs
	 * @return
	 */
	public List<RTreeDirectoryNode> getDirectoryNodeChilds() {
		return directoryNodeChilds;
	}
	
	/**
	 * Get the index entries
	 * @return
	 */
	public List<SpatialIndexEntry> getIndexEntries() {
		return indexEntries;
	}
	
	/**
	 * Read the node from the stream
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	public static RTreeDirectoryNode readFromStream(final InputStream inputStream, final int maxNodeSize) 
			throws IOException {
		
		final int nodeId = DataEncoderHelper.readIntFromStream(inputStream);
		final RTreeDirectoryNode resultNode = new RTreeDirectoryNode(nodeId);

		// Read entry entries
		for(int i = 0; i < maxNodeSize; i++) {
			final byte[] followingByte = new byte[1];
			ByteStreams.readFully(inputStream, followingByte, 0, followingByte.length);
			
			if(followingByte[0] == RTreeSpatialIndexBuilder.MAGIC_CHILD_NODE_FOLLOWING) {
				final SpatialIndexEntry spatialIndexEntry = SpatialIndexEntry.readFromStream(inputStream);
				resultNode.indexEntries.add(spatialIndexEntry);
			} else if(followingByte[0] != RTreeSpatialIndexBuilder.MAGIC_CHILD_NODE_NOT_EXISTING) {
				throw new IllegalArgumentException("Unknown node type following: " + followingByte[0]);
			}				
		}
		
		// Read directory nodes
		for(int i = 0; i < maxNodeSize; i++) {
			final byte[] followingByte = new byte[1];
			ByteStreams.readFully(inputStream, followingByte, 0, followingByte.length);
			
			if(followingByte[0] == RTreeSpatialIndexBuilder.MAGIC_CHILD_NODE_FOLLOWING) {
				final RTreeDirectoryNode rTreeDirectoryNode = RTreeDirectoryNode.readFromStream(
						inputStream, maxNodeSize);
				resultNode.directoryNodeChilds.add(rTreeDirectoryNode);
			} else if(followingByte[0] != RTreeSpatialIndexBuilder.MAGIC_CHILD_NODE_NOT_EXISTING) {
				throw new IllegalArgumentException("Unknown node type following: " + followingByte[0]);
			}
		}
		
		resultNode.updateBoundingBox();
		
		return resultNode;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((boundingBox == null) ? 0 : boundingBox.hashCode());
		result = prime * result + nodeId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RTreeDirectoryNode other = (RTreeDirectoryNode) obj;
		if (boundingBox == null) {
			if (other.boundingBox != null)
				return false;
		} else if (!boundingBox.equals(other.boundingBox))
			return false;
		if (nodeId != other.nodeId)
			return false;
		return true;
	}
	
	/**
	 * Get the node id
	 * @return
	 */
	public int getNodeId() {
		return nodeId;
	}

}