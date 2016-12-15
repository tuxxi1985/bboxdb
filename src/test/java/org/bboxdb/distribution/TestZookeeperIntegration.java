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
package org.bboxdb.distribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bboxdb.BBoxDBConfiguration;
import org.bboxdb.BBoxDBConfigurationManager;
import org.bboxdb.distribution.membership.DistributedInstance;
import org.bboxdb.distribution.mode.DistributionGroupZookeeperAdapter;
import org.bboxdb.distribution.mode.KDtreeZookeeperAdapter;
import org.bboxdb.distribution.mode.NodeState;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestZookeeperIntegration {

	/**
	 * The zookeeper client
	 */
	protected static ZookeeperClient zookeeperClient;
	
	protected static DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter;
	
	/**
	 * The name of the test region
	 */
	protected static final String TEST_GROUP = "4_abc";
	
	@BeforeClass
	public static void before() {
		final BBoxDBConfiguration scalephantConfiguration 
			= BBoxDBConfigurationManager.getConfiguration();
	
		final Collection<String> zookeepernodes = scalephantConfiguration.getZookeepernodes();
		final String clustername = scalephantConfiguration.getClustername();

		System.out.println("Zookeeper nodes are: " + zookeepernodes);
		System.out.println("Zookeeper cluster is: " + clustername);
	
		zookeeperClient = new ZookeeperClient(zookeepernodes, clustername);
		zookeeperClient.init();
		
		distributionGroupZookeeperAdapter = new DistributionGroupZookeeperAdapter(zookeeperClient);
	}
	
	@AfterClass
	public static void after() {
		zookeeperClient.shutdown();
	}

	/**
	 * Test the id generation
	 * @throws ZookeeperException
	 */
	@Test
	public void testTableIdGenerator() throws ZookeeperException {
		final List<Integer> ids = new ArrayList<Integer>();
		
		for(int i = 0; i < 10; i++) {
			int nextId = distributionGroupZookeeperAdapter.getNextTableIdForDistributionGroup("mygroup1");
			System.out.println("The next id is: " + nextId);
			
			Assert.assertFalse(ids.contains(nextId));
			ids.add(nextId);
		}
	}
	
	/**
	 * Test the creation and the deletion of a distribution group
	 * @throws ZookeeperException
	 */
	@Test
	public void testDistributionGroupCreateDelete() throws ZookeeperException {
		
		// Create new group
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		final List<DistributionGroupName> groups = distributionGroupZookeeperAdapter.getDistributionGroups();
		System.out.println(groups);
		boolean found = false;
		for(final DistributionGroupName group : groups) {
			if(group.getFullname().equals(TEST_GROUP)) {
				found = true;
			}
		}
		
		Assert.assertTrue(found);
		Assert.assertTrue(distributionGroupZookeeperAdapter.isDistributionGroupRegistered(TEST_GROUP));
		
		// Delete group
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		final List<DistributionGroupName> groups2 = distributionGroupZookeeperAdapter.getDistributionGroups();
		found = false;
		for(final DistributionGroupName group : groups2) {
			if(group.getFullname().equals(TEST_GROUP)) {
				found = true;
			}
		}
		
		Assert.assertFalse(found);
		Assert.assertFalse(distributionGroupZookeeperAdapter.isDistributionGroupRegistered(TEST_GROUP));
	}
	
	/**
	 * Test the replication factor of a distribution group
	 * @throws ZookeeperException 
	 */
	@Test
	public void testDistributionGroupReplicationFactor() throws ZookeeperException {
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		Assert.assertEquals(3, distributionGroupZookeeperAdapter.getReplicationFactorForDistributionGroup(TEST_GROUP));
	}
	
	/**
	 * Test the split of a distribution region
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testDistributionRegionSplit() throws ZookeeperException, InterruptedException {
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		// Split and update
		final KDtreeZookeeperAdapter distributionGroupAdapter = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup = distributionGroupAdapter.getRootNode();
		Assert.assertEquals(TEST_GROUP, distributionGroup.getName());
		
		Assert.assertEquals(NodeState.ACTIVE, distributionGroupZookeeperAdapter.getStateForDistributionRegion(distributionGroup, null));
		distributionGroupAdapter.splitNode(distributionGroup, 10);
		
		Thread.sleep(1000);
		Assert.assertEquals(10.0, distributionGroup.getSplit(), 0.0001);
		Assert.assertEquals(NodeState.SPLITTING, distributionGroupZookeeperAdapter.getStateForDistributionRegion(distributionGroup, null));

		// Reread group from zookeeper
		final DistributionRegion newDistributionGroup = distributionGroupAdapter.getRootNode();
		Assert.assertEquals(10.0, newDistributionGroup.getSplit(), 0.0001);
	}
	
	/**
	 * Test the distribution of changes in the zookeeper structure (reading data from the second object)
	 * @throws ZookeeperException
	 * @throws InterruptedException
	 */
	@Test
	public void testDistributionRegionSplitWithZookeeperPropergate() throws ZookeeperException, InterruptedException {
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final KDtreeZookeeperAdapter adapter1 = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup1 = adapter1.getRootNode();
		
		final KDtreeZookeeperAdapter adapter2 = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup2 = adapter2.getRootNode();

		// Update object 1
		adapter1.splitNode(distributionGroup1, 10);
		
		// Sleep 2 seconds to wait for the update
		Thread.sleep(2000);

		// Read update from the second object
		Assert.assertEquals(10.0, distributionGroup2.getSplit(), 0.0001);
	}
	
	/**
	 * Test the distribution of changes in the zookeeper structure (reading data from the second object)
	 * @throws ZookeeperException
	 * @throws InterruptedException
	 */
	@Test
	public void testDistributionRegionSplitWithZookeeperPropergate2() throws ZookeeperException, InterruptedException {
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final KDtreeZookeeperAdapter adapter1 = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup1 = adapter1.getRootNode();
		
		final KDtreeZookeeperAdapter adapter2 = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup2 = adapter2.getRootNode();

		Assert.assertEquals(0, distributionGroup1.getLevel());
		
		// Update object 1
		adapter1.splitNode(distributionGroup1, 10);
		final DistributionRegion leftChild = distributionGroup1.getLeftChild();
		Assert.assertEquals(1, leftChild.getLevel());
		Assert.assertEquals(1, leftChild.getSplitDimension());
		adapter1.splitNode(leftChild, 50);

		// Sleep 2 seconds to wait for the update
		Thread.sleep(2000);

		// Read update from the second object
		Assert.assertEquals(10.0, distributionGroup2.getSplit(), 0.0001);
		Assert.assertEquals(50.0, distributionGroup2.getLeftChild().getSplit(), 0.0001);
	}
	
	/**
	 * Test the system register and unregister methods
	 * @throws ZookeeperException 
	 */
	@Test
	public void testSystemRegisterAndUnregister() throws ZookeeperException {
		final DistributedInstance systemName = new DistributedInstance("192.168.1.10:5050");
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final DistributionRegion region = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP).getRootNode();
		final Collection<DistributedInstance> systems1 = distributionGroupZookeeperAdapter.getSystemsForDistributionRegion(region, null);
		Assert.assertEquals(0, systems1.size());
		
		// Add a system
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region, systemName);
		final Collection<DistributedInstance> systems2 = distributionGroupZookeeperAdapter.getSystemsForDistributionRegion(region, null);
		Assert.assertEquals(1, systems2.size());
		Assert.assertTrue(systems2.contains(systemName));
		
		distributionGroupZookeeperAdapter.deleteSystemFromDistributionRegion(region, systemName);
		final Collection<DistributedInstance> systems3 = distributionGroupZookeeperAdapter.getSystemsForDistributionRegion(region, null);
		Assert.assertEquals(0, systems3.size());
	}
	
	/**
	 * Test the set and get checkpoint methods
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testSystemCheckpoint1() throws ZookeeperException, InterruptedException {
		final DistributedInstance systemName1 = new DistributedInstance("192.168.1.10:5050");
		final DistributedInstance systemName2 = new DistributedInstance("192.168.1.20:5050");

		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final DistributionRegion region = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP).getRootNode();

		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region, systemName1);
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region, systemName2);

		final long checkpoint1 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region, systemName1);
		Assert.assertEquals(-1, checkpoint1);

		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region, systemName1, 5000);
		final long checkpoint2 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region, systemName1);
		Assert.assertEquals(5000, checkpoint2);
		
		// System 2
		final long checkpoint3 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region, systemName2);
		Assert.assertEquals(-1, checkpoint3);

		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region, systemName2, 9000);
		final long checkpoint4 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region, systemName2);
		Assert.assertEquals(9000, checkpoint4);
		
		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region, systemName2, 9001);
		final long checkpoint5 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region, systemName2);
		Assert.assertEquals(9001, checkpoint5);
	}
	
	/**
	 * Test the set and get checkpoint methods
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testSystemCheckpoint2() throws ZookeeperException, InterruptedException {
		final DistributedInstance systemName1 = new DistributedInstance("192.168.1.10:5050");
		final DistributedInstance systemName2 = new DistributedInstance("192.168.1.20:5050");

		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final KDtreeZookeeperAdapter distributionGroupAdapter = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion region = distributionGroupAdapter.getRootNode();
		distributionGroupAdapter.splitNode(region, 50);
		
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region.getLeftChild(), systemName1);
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region.getLeftChild(), systemName2);
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region.getRightChild(), systemName1);
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region.getRightChild(), systemName2);
		
		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region.getLeftChild(), systemName1, 1);
		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region.getLeftChild(), systemName2, 2);
		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region.getRightChild(), systemName1, 3);
		distributionGroupZookeeperAdapter.setCheckpointForDistributionRegion(region.getRightChild(), systemName2, 4);

		final long checkpoint1 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region.getLeftChild(), systemName1);
		final long checkpoint2 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region.getLeftChild(), systemName2);
		final long checkpoint3 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region.getRightChild(), systemName1);
		final long checkpoint4 = distributionGroupZookeeperAdapter.getCheckpointForDistributionRegion(region.getRightChild(), systemName2);

		Assert.assertEquals(1, checkpoint1);
		Assert.assertEquals(2, checkpoint2);
		Assert.assertEquals(3, checkpoint3);
		Assert.assertEquals(4, checkpoint4);
	}
	
	/**
	 * Test the systems field
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testSystems() throws ZookeeperException, InterruptedException {
		final DistributedInstance systemName = new DistributedInstance("192.168.1.10:5050");
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final DistributionRegion region = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP).getRootNode();
		distributionGroupZookeeperAdapter.addSystemToDistributionRegion(region, systemName);
		
		// Sleep 2 seconds to wait for the update
		Thread.sleep(2000);

		Assert.assertEquals(1, region.getSystems().size());
		Assert.assertTrue(region.getSystems().contains(systemName));
	}

	/**
	 * Test the generation of the nameprefix
	 * @throws ZookeeperException
	 * @throws InterruptedException
	 */
	@Test
	public void testNameprefix1() throws ZookeeperException, InterruptedException {
		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final DistributionRegion region = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP).getRootNode();
		Assert.assertEquals(0, region.getNameprefix());
	}
	
	/**
	 * Test the generation of the nameprefix
	 * @throws ZookeeperException
	 * @throws InterruptedException
	 */
	@Test
	public void testNameprefix2() throws ZookeeperException, InterruptedException {
 		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final KDtreeZookeeperAdapter distributionGroupAdapter = distributionGroupZookeeperAdapter.readDistributionGroup(TEST_GROUP);
		final DistributionRegion region = distributionGroupAdapter.getRootNode();
		distributionGroupAdapter.splitNode(region, 10);
		
		final DistributionRegion leftChild = region.getLeftChild();
		final DistributionRegion rightChild = region.getRightChild();
		
		Assert.assertEquals(0, region.getNameprefix());
		Assert.assertEquals(1, leftChild.getNameprefix());
		Assert.assertEquals(2, rightChild.getNameprefix());
	}
	
	/**
	 * Test the path decoding and encoding
	 */
	@Test
	public void testPathDecodeAndEncode() {

		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setNameprefix(1);
		level0.setSplit(50);

		// Level 1
		final DistributionRegion level1l = level0.getLeftChild();
		level1l.setSplit(40);
		final DistributionRegion level1r = level0.getRightChild();
		level1r.setSplit(50);

		// Level 2
		final DistributionRegion level2ll = level1l.getLeftChild();
		level2ll.setSplit(30);
		final DistributionRegion level2rl = level1r.getLeftChild();
		level2rl.setSplit(60);
		final DistributionRegion level2lr = level1l.getRightChild();
		level2lr.setSplit(30);
		final DistributionRegion level2rr = level1r.getRightChild();
		level2rr.setSplit(60);

		// Level 3
		final DistributionRegion level3lll = level2ll.getLeftChild();
		level3lll.setSplit(35);

		final DistributionGroupZookeeperAdapter zookeeperAdapter = new DistributionGroupZookeeperAdapter(zookeeperClient);
		
		final String path0 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level0);
		final String path1 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level1l);
		final String path2 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level1r);
		final String path3 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level2ll);
		final String path4 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level2rl);
		final String path5 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level2lr);
		final String path6 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level2rr);
		final String path7 = zookeeperAdapter.getZookeeperPathForDistributionRegion(level3lll);
		
		Assert.assertEquals(level0, distributionGroupZookeeperAdapter.getNodeForPath(level0, path0));
		Assert.assertEquals(level1l, distributionGroupZookeeperAdapter.getNodeForPath(level0, path1));
		Assert.assertEquals(level1r, distributionGroupZookeeperAdapter.getNodeForPath(level0, path2));
		Assert.assertEquals(level2ll, distributionGroupZookeeperAdapter.getNodeForPath(level0, path3));
		Assert.assertEquals(level2rl, distributionGroupZookeeperAdapter.getNodeForPath(level0, path4));
		Assert.assertEquals(level2lr, distributionGroupZookeeperAdapter.getNodeForPath(level0, path5));
		Assert.assertEquals(level2rr, distributionGroupZookeeperAdapter.getNodeForPath(level0, path6));
		Assert.assertEquals(level3lll, distributionGroupZookeeperAdapter.getNodeForPath(level0, path7));

	}
}