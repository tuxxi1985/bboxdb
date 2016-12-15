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
package org.bboxdb;

import java.util.List;
import java.util.Set;

import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.DistributionRegionHelper;
import org.bboxdb.distribution.membership.DistributedInstance;
import org.bboxdb.distribution.mode.NodeState;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.storage.entity.BoundingBox;
import org.junit.Assert;
import org.junit.Test;

public class TestDistributionGroup {

	protected static ZookeeperClient zookeeperClient;

	/**
	 * Create an illegal distribution group
	 */
	@Test(expected=IllegalArgumentException.class)
	public void createInvalidDistributionGroup1() {
		@SuppressWarnings("unused")
		final DistributionRegion distributionRegion = DistributionRegion.createRootElement("foo");
	}
	
	/**
	 * Create an illegal distribution group
	 */
	@Test(expected=IllegalArgumentException.class)
	public void createInvalidDistributionGroup2() {
		@SuppressWarnings("unused")
		final DistributionRegion distributionRegion = DistributionRegion.createRootElement("12_foo_bar");
	}
	
	/**
	 * Test a distribution region with only one level
	 */
	@Test
	public void testLeafNode() {
		final DistributionRegion distributionRegion = DistributionRegion.createRootElement("3_foo");
		Assert.assertTrue(distributionRegion.isLeafRegion());
		Assert.assertEquals(3, distributionRegion.getDimension());
		Assert.assertEquals(0, distributionRegion.getLevel());
		
		Assert.assertEquals(1, distributionRegion.getTotalLevel());
	}
	
	/**
	 * Test a distribution region with two levels level
	 */
	@Test
	public void testTwoLevel() {
		final DistributionRegion distributionRegion = DistributionRegion.createRootElement("3_foo");
		Assert.assertTrue(distributionRegion.isLeafRegion());
		distributionRegion.setSplit(0);
		Assert.assertFalse(distributionRegion.isLeafRegion());

		Assert.assertEquals(distributionRegion, distributionRegion.getLeftChild().getRootRegion());
		Assert.assertEquals(distributionRegion, distributionRegion.getRightChild().getRootRegion());
		
		Assert.assertEquals(distributionRegion.getDimension(), distributionRegion.getLeftChild().getDimension());
		Assert.assertEquals(distributionRegion.getDimension(), distributionRegion.getRightChild().getDimension());
		
		Assert.assertEquals(1, distributionRegion.getLeftChild().getLevel());
		Assert.assertEquals(1, distributionRegion.getRightChild().getLevel());
		
		Assert.assertEquals(2, distributionRegion.getTotalLevel());
	}
	
	/**
	 * Test the split dimension 2d
	 */
	@Test
	public void testSplitDimension1() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setSplit(50);
		final DistributionRegion level1 = level0.getLeftChild();
		level1.setSplit(40);
		final DistributionRegion level2 = level1.getLeftChild();
		level2.setSplit(-30);
		final DistributionRegion level3 = level2.getLeftChild();
		level3.setSplit(30);
		final DistributionRegion level4 = level3.getLeftChild();

		Assert.assertEquals(0, level0.getSplitDimension());
		Assert.assertEquals(1, level1.getSplitDimension());
		Assert.assertEquals(0, level2.getSplitDimension());
		Assert.assertEquals(1, level3.getSplitDimension());
		Assert.assertEquals(0, level4.getSplitDimension());
	}
	
	/**
	 * Test the split dimension 3d
	 */
	@Test
	public void testSplitDimension2() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("3_foo");
		level0.setSplit(50);
		final DistributionRegion level1 = level0.getLeftChild();
		level1.setSplit(40);
		final DistributionRegion level2 = level1.getLeftChild();
		level2.setSplit(30);
		final DistributionRegion level3 = level2.getLeftChild();
		level3.setSplit(30);
		final DistributionRegion level4 = level3.getLeftChild();

		Assert.assertEquals(0, level0.getSplitDimension());
		Assert.assertEquals(1, level1.getSplitDimension());
		Assert.assertEquals(2, level2.getSplitDimension());
		Assert.assertEquals(0, level3.getSplitDimension());
		Assert.assertEquals(1, level4.getSplitDimension());
		
		Assert.assertEquals(5, level0.getTotalLevel());
		Assert.assertEquals(5, level1.getTotalLevel());
		Assert.assertEquals(5, level2.getTotalLevel());
		Assert.assertEquals(5, level3.getTotalLevel());
		Assert.assertEquals(5, level4.getTotalLevel());
	}
	
	/**
	 * Test isLeftChild and isRightChild method
	 */
	@Test
	public void testLeftOrRightChild() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("3_foo");
		Assert.assertFalse(level0.isLeftChild());
		Assert.assertFalse(level0.isRightChild());
		
		level0.setSplit(50);
				
		Assert.assertTrue(level0.getLeftChild().isLeftChild());
		Assert.assertTrue(level0.getRightChild().isRightChild());
		Assert.assertFalse(level0.getRightChild().isLeftChild());
		Assert.assertFalse(level0.getLeftChild().isRightChild());
	}

	/**
	 * Test the find systems method
	 */
	@Test
	public void testFindSystems() {
		final DistributedInstance SYSTEM_A = new DistributedInstance("192.168.1.200:5050");
		final DistributedInstance SYSTEM_B = new DistributedInstance("192.168.1.201:5050");
		
		final DistributionRegion level0 = DistributionRegion.createRootElement("1_foo");
		level0.setSplit(50);

		level0.getLeftChild().addSystem(SYSTEM_A);
		level0.getRightChild().addSystem(SYSTEM_B);
		
		level0.setState(NodeState.SPLITTED);
		level0.getLeftChild().setState(NodeState.ACTIVE);
		level0.getRightChild().setState(NodeState.ACTIVE);
		
		Assert.assertFalse(level0.getSystemsForBoundingBox(new BoundingBox(100f, 110f)).contains(SYSTEM_A));
		Assert.assertTrue(level0.getSystemsForBoundingBox(new BoundingBox(0f, 10f)).contains(SYSTEM_A));
		
		Assert.assertTrue(level0.getSystemsForBoundingBox(new BoundingBox(0f, 10f)).contains(SYSTEM_A));
		Assert.assertFalse(level0.getSystemsForBoundingBox(new BoundingBox(100f, 110f)).contains(SYSTEM_A));
		
		Assert.assertTrue(level0.getSystemsForBoundingBox(new BoundingBox(0f, 100f)).contains(SYSTEM_A));
		Assert.assertTrue(level0.getSystemsForBoundingBox(new BoundingBox(0f, 100f)).contains(SYSTEM_B));
	}
	
	/**
	 * Test name prefix search
	 */
	@Test
	public void testNameprefixSearch() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setNameprefix(1);
		level0.setSplit(50);
		level0.getLeftChild().setNameprefix(2);
		level0.getRightChild().setNameprefix(3);
		
		final DistributionRegion level1 = level0.getLeftChild();
		level1.setSplit(40);
		level1.getLeftChild().setNameprefix(4);
		level1.getRightChild().setNameprefix(5);
		
		final DistributionRegion level2 = level1.getLeftChild();
		level2.setSplit(30);
		level2.getLeftChild().setNameprefix(6);
		level2.getRightChild().setNameprefix(7);
		
		final DistributionRegion level3 = level2.getLeftChild();
		level3.setSplit(35);
		level3.getLeftChild().setNameprefix(8);
		level3.getRightChild().setNameprefix(9);

		Assert.assertTrue(DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 4711) == null);
		
		Assert.assertEquals(level0, DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 1));
		Assert.assertEquals(level1.getLeftChild(), DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 4));
		Assert.assertEquals(level1.getRightChild(), DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 5));
		Assert.assertEquals(level3.getLeftChild(), DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 8));
		Assert.assertEquals(level3.getRightChild(), DistributionRegionHelper.getDistributionRegionForNamePrefix(level0, 9));
		
		Assert.assertEquals(null, DistributionRegionHelper.getDistributionRegionForNamePrefix(level3, 1));
	}
	
	
	/**
	 * Get the systems for a distribution group
	 */
	@Test
	public void testGetSystemsForDistributionGroup1() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setSplit(50);
		level0.getLeftChild().setNameprefix(2);
		level0.getRightChild().setNameprefix(3);
		
		level0.setState(NodeState.SPLITTED);
		level0.getLeftChild().setState(NodeState.ACTIVE);
		level0.getRightChild().setState(NodeState.ACTIVE);
		
		level0.addSystem(new DistributedInstance("node1:123"));
		level0.getLeftChild().addSystem(new DistributedInstance("node2:123"));
		level0.getRightChild().addSystem(new DistributedInstance("node3:123"));
		
		final List<DistributedInstance> systems = level0.getSystemsForBoundingBox(BoundingBox.EMPTY_BOX);
		Assert.assertEquals(2, systems.size());
	}
	
	/**
	 * Get the systems for a distribution group
	 */
	@Test
	public void testGetSystemsForDistributionGroup2() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setSplit(50);
		level0.getLeftChild().setNameprefix(2);
		level0.getRightChild().setNameprefix(3);
		
		level0.setState(NodeState.SPLITTED);
		level0.getLeftChild().setState(NodeState.ACTIVE);
		level0.getRightChild().setState(NodeState.ACTIVE);
		
		level0.addSystem(new DistributedInstance("node1:123"));
		level0.getLeftChild().addSystem(new DistributedInstance("node2:123"));
		level0.getRightChild().addSystem(new DistributedInstance("node2:123"));
		
		final List<DistributedInstance> systems = level0.getSystemsForBoundingBox(BoundingBox.EMPTY_BOX);
		Assert.assertEquals(1, systems.size());
	}
	
	/**
	 * Get the distribution groups for a distribution group
	 */
	@Test
	public void testGetDistributionGroupsForDistributionGroup() {
		final DistributionRegion level0 = DistributionRegion.createRootElement("2_foo");
		level0.setSplit(50);
		level0.getLeftChild().setNameprefix(2);
		level0.getRightChild().setNameprefix(3);
		
		level0.addSystem(new DistributedInstance("node1:123"));
		level0.getLeftChild().addSystem(new DistributedInstance("node2:123"));
		level0.getRightChild().addSystem(new DistributedInstance("node2:123"));
		
		final Set<DistributionRegion> regions = level0.getDistributionRegionsForBoundingBox(BoundingBox.EMPTY_BOX);
		Assert.assertEquals(3, regions.size());
	}
	
}