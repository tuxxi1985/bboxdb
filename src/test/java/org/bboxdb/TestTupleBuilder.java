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
package org.bboxdb;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.tools.converter.tuple.TupleBuilder;
import org.bboxdb.tools.converter.tuple.TupleBuilderFactory;
import org.junit.Assert;
import org.junit.Test;


public class TestTupleBuilder {

	/**
	 * Test the geo json tuple builder
	 */
	@Test
	public void testGeoJsonTupleBuilder() {
		final String testLine = "{\"geometry\":{\"coordinates\":[52.4688608,13.3327994],\"type\":\"Point\"},\"id\":271247324,\"type\":\"Feature\",\"properties\":{\"natural\":\"tree\",\"leaf_cycle\":\"deciduous\",\"name\":\"Kaisereiche\",\"leaf_type\":\"broadleaved\",\"wikipedia\":\"de:Kaisereiche (Berlin)\"}}";
	
		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.GEOJSON);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
		
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		final BoundingBox expectedBox = new BoundingBox(52.4688608, 52.4688608, 13.3327994, 13.3327994);
		Assert.assertEquals(expectedBox, tuple.getBoundingBox());
	}

	/**
	 * Test the yellow taxi range tuple builder
	 * @throws ParseException 
	 */
	@Test
	public void testYellowTaxiRangeTupleBuilder() throws ParseException {
		final String testLine = "2,2016-01-01 00:00:00,2016-01-01 00:00:00,2,1.10,-73.990371704101563,40.734695434570313,1,N,-73.981842041015625,40.732406616210937,2,7.5,0.5,0.5,0,0,0.3,8.8";
	
		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.YELLOWTAXI_RANGE);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
				
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		
		final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-mm-dd HH:mm:ss");
		final Date dateLow = dateParser.parse("2016-01-01 00:00:00");
		final Date dateHigh = dateParser.parse("2016-01-01 00:00:00");

		final BoundingBox exptectedBox = new BoundingBox(-73.990371704101563, 40.734695434570313, 
				-73.981842041015625, 40.732406616210937, 
				(double) dateLow.getTime(), (double) dateHigh.getTime());
		
		Assert.assertEquals(exptectedBox, tuple.getBoundingBox());
	}
	
	/**
	 * Test the yellow taxi range tuple builder
	 * @throws ParseException 
	 */
	@Test
	public void testYellowTaxiPointTupleBuilder() throws ParseException {
		final String testLine = "2,2016-01-01 00:00:00,2016-01-01 00:00:00,2,1.10,-73.990371704101563,40.734695434570313,1,N,-73.981842041015625,40.732406616210937,2,7.5,0.5,0.5,0,0,0.3,8.8";
	
		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.YELLOWTAXI_POINT);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
				
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		
		final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-mm-dd HH:mm:ss");
		final Date dateLow = dateParser.parse("2016-01-01 00:00:00");

		final BoundingBox exptectedBox = new BoundingBox(-73.990371704101563, 40.734695434570313, 
				-73.990371704101563, 40.734695434570313, 
				(double) dateLow.getTime(), (double) dateLow.getTime());
		
		Assert.assertEquals(exptectedBox, tuple.getBoundingBox());
	}
	
	/**
	 * Test the tpch point tuple builder
	 * @throws ParseException 
	 */
	@Test
	public void testTPCHLineitemPointTupleBuilder() throws ParseException {
		final String testLine = "3|29380|1883|4|2|2618.76|0.01|0.06|A|F|1993-12-04|1994-01-07|1994-01-01|NONE|TRUCK|y. fluffily pending d|";

		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.TPCH_LINEITEM_POINT);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
				
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		
		final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-mm-dd");
		final Date date = dateParser.parse("1993-12-04");

		final double doubleTime = (double) date.getTime();
		final BoundingBox exptectedBox = new BoundingBox(doubleTime, doubleTime);
		
		Assert.assertEquals(exptectedBox, tuple.getBoundingBox());
	}
	
	/**
	 * Test the tpch range tuple builder
	 * @throws ParseException 
	 */
	@Test
	public void testTPCHLineitemRangeTupleBuilder() throws ParseException {
		final String testLine = "3|29380|1883|4|2|2618.76|0.01|0.06|A|F|1993-12-04|1994-01-07|1994-01-01|NONE|TRUCK|y. fluffily pending d|";

		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.TPCH_LINEITEM_RANGE);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
				
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		
		final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-mm-dd");
		final Date shipDateTime = dateParser.parse("1993-12-04");
		final Date receiptDateTime = dateParser.parse("1994-01-01");
		
		final double doubleShipDateTime = (double) shipDateTime.getTime();
		final double doublereceiptDateTime = (double) receiptDateTime.getTime();

		final BoundingBox exptectedBox = new BoundingBox(doubleShipDateTime, doublereceiptDateTime);
		
		Assert.assertEquals(exptectedBox, tuple.getBoundingBox());
	}
	
	/**
	 * Test the syntetic tuple builder
	 * @throws ParseException 
	 */
	@Test
	public void testSyntheticTupleBuilder() throws ParseException {
		final String testLine = "51.47015078569419,58.26664175357267,49.11808592466023,52.72529828070016 e1k141dox9rayxo544y9";
		
		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(TupleBuilderFactory.Name.SYNTHETIC);
		
		final Tuple tuple = tupleBuilder.buildTuple("1", testLine);
				
		Assert.assertTrue(tuple != null);
		Assert.assertEquals(Integer.toString(1), tuple.getKey());
		
		final BoundingBox exptectedBox = new BoundingBox(51.47015078569419, 58.26664175357267,
				49.11808592466023, 52.72529828070016);
		
		Assert.assertEquals(exptectedBox, tuple.getBoundingBox());
		Assert.assertEquals("e1k141dox9rayxo544y9", new String(tuple.getDataBytes()));
	}
	
}
