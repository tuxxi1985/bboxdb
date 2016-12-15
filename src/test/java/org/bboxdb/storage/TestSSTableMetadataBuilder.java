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
package org.bboxdb.storage;

import java.io.File;
import java.io.IOException;

import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.entity.DeletedTuple;
import org.bboxdb.storage.entity.SStableMetaData;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.sstable.SSTableMetadataBuilder;
import org.junit.Assert;
import org.junit.Test;

public class TestSSTableMetadataBuilder {

	/**
	 * Build empty index
	 */
	@Test
	public void testSSTableIndexBuilder1() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(new float[] {}, metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(0, metadata.getTuples());
	}

	/**
	 * Build index with one tuple
	 */
	@Test
	public void testSSTableIndexBuilder2() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		
		ssTableIndexBuilder.addTuple(tuple1);
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(boundingBox1.toFloatArray(), metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(metadata.getOldestTuple(), metadata.getNewestTuple());
		Assert.assertEquals(1, metadata.getTuples());
	}
	
	/**
	 * Build index with two tuples
	 */
	@Test
	public void testSSTableIndexBuilder3() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		final Tuple tuple2 = new Tuple("def", boundingBox1, "".getBytes());

		ssTableIndexBuilder.addTuple(tuple1);
		ssTableIndexBuilder.addTuple(tuple2);
		
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(boundingBox1.toFloatArray(), metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(2, metadata.getTuples());
	}
	
	/**
	 * Build index with two tuples
	 */
	@Test
	public void testSSTableIndexBuilder4() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		final BoundingBox boundingBox2 = new BoundingBox(1f, 3f);
		final Tuple tuple2 = new Tuple("def", boundingBox2, "".getBytes());

		ssTableIndexBuilder.addTuple(tuple1);
		ssTableIndexBuilder.addTuple(tuple2);
		
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(boundingBox2.toFloatArray(), metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(2, metadata.getTuples());
	}
	
	/**
	 * Build index with two tuples
	 */
	@Test
	public void testSSTableIndexBuilder5() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		final BoundingBox boundingBox2 = new BoundingBox(1f, 1.1f);
		final Tuple tuple2 = new Tuple("def", boundingBox2, "".getBytes());

		ssTableIndexBuilder.addTuple(tuple1);
		ssTableIndexBuilder.addTuple(tuple2);
		
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(boundingBox1.toFloatArray(), metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(2, metadata.getTuples());
	}
	
	/**
	 * Build index with two tuples - bounding boxes are differ in the dimension
	 */
	@Test
	public void testSSTableIndexBuilder6() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f, 1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		final BoundingBox boundingBox2 = new BoundingBox(1f, 1.1f);
		final Tuple tuple2 = new Tuple("def", boundingBox2, "".getBytes());

		ssTableIndexBuilder.addTuple(tuple1);
		ssTableIndexBuilder.addTuple(tuple2);
		
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(new float[] {}, metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(2, metadata.getTuples());
	}
	
	
	/**
	 * Build index with two tuples - bounding boxes are differ in the dimension
	 */
	@Test
	public void testSSTableIndexBuilder7() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		addTwoTuples(ssTableIndexBuilder);
		
		final SStableMetaData metadata = ssTableIndexBuilder.getMetaData();
		Assert.assertArrayEquals(new float[] {1f, 2f, 1f, 5f}, metadata.getBoundingBoxData(), 0.001f);
		Assert.assertEquals(2, metadata.getTuples());
	}
	
	
	/**
	 * Build index with multiple tuples - check timestamps
	 */
	@Test
	public void testSSTableIndexBuilder8() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		final Tuple tuple1 = new Tuple("0", BoundingBox.EMPTY_BOX, "".getBytes(), 6);
		ssTableIndexBuilder.addTuple(tuple1);
		Assert.assertEquals(6, ssTableIndexBuilder.getMetaData().getNewestTuple());
		Assert.assertEquals(6, ssTableIndexBuilder.getMetaData().getOldestTuple());
		
		final Tuple tuple2 = new Tuple("0", BoundingBox.EMPTY_BOX, "".getBytes(), 7);
		ssTableIndexBuilder.addTuple(tuple2);
		Assert.assertEquals(7, ssTableIndexBuilder.getMetaData().getNewestTuple());
		Assert.assertEquals(6, ssTableIndexBuilder.getMetaData().getOldestTuple());
		
		final Tuple tuple3 = new Tuple("0", BoundingBox.EMPTY_BOX, "".getBytes(), 2);
		ssTableIndexBuilder.addTuple(tuple3);
		Assert.assertEquals(7, ssTableIndexBuilder.getMetaData().getNewestTuple());
		Assert.assertEquals(2, ssTableIndexBuilder.getMetaData().getOldestTuple());
		
		final Tuple tuple4 = new DeletedTuple("0", 22);
		ssTableIndexBuilder.addTuple(tuple4);
		Assert.assertEquals(22, ssTableIndexBuilder.getMetaData().getNewestTuple());
		Assert.assertEquals(2, ssTableIndexBuilder.getMetaData().getOldestTuple());
	}
	
	/**
	 * Dump the index to yaml
	 */
	@Test
	public void testDumpToYaml1() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		final String yamlData = ssTableIndexBuilder.getMetaData().exportToYaml();
		Assert.assertTrue(yamlData.length() > 10);
	}
	
	/**
	 * Dump the index to yaml
	 */
	@Test
	public void testDumpToYaml2() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f, 1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		ssTableIndexBuilder.addTuple(tuple1);

		final String yamlData = ssTableIndexBuilder.getMetaData().exportToYaml();
		Assert.assertTrue(yamlData.length() > 10);
	}
	
	/**
	 * Dump the index to yaml and reread the data
	 */
	@Test
	public void testDumpAndReadFromYamlString1() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f, 1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		ssTableIndexBuilder.addTuple(tuple1);

		final SStableMetaData metaData = ssTableIndexBuilder.getMetaData();
		final String yamlData = metaData.exportToYaml();
				
		final SStableMetaData metaDataRead = SStableMetaData.importFromYaml(yamlData);
		Assert.assertEquals(metaData, metaDataRead);
	}
	
	/**
	 * Dump the index to yaml and reread the data
	 */
	@Test
	public void testDumpAndReadFromYamlStrig2() {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		addTwoTuples(ssTableIndexBuilder);

		final SStableMetaData metaData = ssTableIndexBuilder.getMetaData();
		final String yamlData = metaData.exportToYaml();
			
		final SStableMetaData metaDataRead = SStableMetaData.importFromYaml(yamlData);
		Assert.assertEquals(metaData, metaDataRead);
	}
	
	/**
	 * Dump the index to yaml file and reread the data - zero tuples
	 * @throws IOException 
	 */
	@Test
	public void testDumpAndReadFromYamlFile1() throws IOException {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
				
		final File tmpFile = File.createTempFile("test", ".tmp");

		final SStableMetaData metaData = ssTableIndexBuilder.getMetaData();
		metaData.exportToYamlFile(tmpFile);
			
		final SStableMetaData metaDataRead = SStableMetaData.importFromYamlFile(tmpFile);
		Assert.assertEquals(metaData, metaDataRead);
		tmpFile.delete();
	}

	
	/**
	 * Dump the index to yaml file and reread the data - two tuple
	 * @throws IOException 
	 */
	@Test
	public void testDumpAndReadFromYamlFile2() throws IOException {
		final SSTableMetadataBuilder ssTableIndexBuilder = new SSTableMetadataBuilder();
		
		addTwoTuples(ssTableIndexBuilder);
		
		final File tmpFile = File.createTempFile("test", ".tmp");

		final SStableMetaData metaData = ssTableIndexBuilder.getMetaData();
		metaData.exportToYamlFile(tmpFile);
			
		final SStableMetaData metaDataRead = SStableMetaData.importFromYamlFile(tmpFile);
		Assert.assertEquals(metaData, metaDataRead);
		tmpFile.delete();
	}

	/**
	 * Add two tuples to the index builder
	 * @param ssTableIndexBuilder
	 */
	protected void addTwoTuples(final SSTableMetadataBuilder ssTableIndexBuilder) {
		final BoundingBox boundingBox1 = new BoundingBox(1f, 2f, 1f, 2f);
		final Tuple tuple1 = new Tuple("abc", boundingBox1, "".getBytes());
		final BoundingBox boundingBox2 = new BoundingBox(1f, 1.1f, 1f, 5f);
		final Tuple tuple2 = new Tuple("def", boundingBox2, "".getBytes());
		
		ssTableIndexBuilder.addTuple(tuple1);
		ssTableIndexBuilder.addTuple(tuple2);
	}
	
}