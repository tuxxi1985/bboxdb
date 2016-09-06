package de.fernunihagen.dna.scalephant.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;

import de.fernunihagen.dna.scalephant.Const;
import de.fernunihagen.dna.scalephant.distribution.membership.DistributedInstance;
import de.fernunihagen.dna.scalephant.network.client.ClientOperationFuture;
import de.fernunihagen.dna.scalephant.network.client.SequenceNumberGenerator;
import de.fernunihagen.dna.scalephant.network.packages.NetworkRequestPackage;
import de.fernunihagen.dna.scalephant.network.packages.request.CreateDistributionGroupRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.DeleteDistributionGroupRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.DeleteTableRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.DeleteTupleRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.HeloRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.InsertTupleRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.ListTablesRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.QueryBoundingBoxRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.QueryKeyRequest;
import de.fernunihagen.dna.scalephant.network.packages.request.QueryTimeRequest;
import de.fernunihagen.dna.scalephant.network.packages.response.ListTablesResponse;
import de.fernunihagen.dna.scalephant.network.packages.response.SuccessResponse;
import de.fernunihagen.dna.scalephant.network.packages.response.SuccessWithBodyResponse;
import de.fernunihagen.dna.scalephant.network.packages.response.TupleResponse;
import de.fernunihagen.dna.scalephant.network.routing.RoutingHeader;
import de.fernunihagen.dna.scalephant.storage.entity.BoundingBox;
import de.fernunihagen.dna.scalephant.storage.entity.SSTableName;
import de.fernunihagen.dna.scalephant.storage.entity.Tuple;

public class TestNetworkClasses {
	
	/**
	 * The sequence number generator for our packages
	 */
	protected SequenceNumberGenerator sequenceNumberGenerator = new SequenceNumberGenerator();
	
	/**
	 * Convert a network package into a byte array
	 * @param networkPackage
	 * @param sequenceNumber
	 * @return
	 * @throws IOException 
	 */
	protected byte[] networkPackageToByte(final NetworkRequestPackage networkPackage, final short sequenceNumber) throws IOException {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		networkPackage.writeToOutputStream(sequenceNumber, bos);
		bos.flush();
		bos.close();
		return bos.toByteArray();
	}
	
	/**
	 * Ensure that all sequence numbers are distinct
	 */
	@Test
	public void testSequenceNumberGenerator1() {
		final int NUMBERS = 1000;
		final HashMap<Short, Short> sequenceNumberMap = new HashMap<Short, Short>();
		
		for(int i = 0; i < NUMBERS; i++) {
			final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
			Assert.assertFalse(sequenceNumberMap.containsKey(sequenceNumber));
			sequenceNumberMap.put(sequenceNumber, (short) 1);
		}
		
		Assert.assertEquals(sequenceNumberMap.size(), NUMBERS);
	}
	
	/**
	 * Ensure the generatror is able to create more than 2^16 numbers, even we have
	 * some overruns 
	 */
	@Test
	public void testSequenceNumberGenerator2() {
		final HashMap<Short, Short> sequenceNumberMap = new HashMap<Short, Short>();

		for(int i = 0; i < Integer.MAX_VALUE / 100; i++) {
			final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
			
			if(! sequenceNumberMap.containsKey(sequenceNumber)) {
				sequenceNumberMap.put(sequenceNumber, (short) 1);
			} else {
				short oldValue = sequenceNumberMap.get(sequenceNumber);
				sequenceNumberMap.put(sequenceNumber, (short) (oldValue + 1));
			}
		}
		
		Assert.assertEquals(65536, sequenceNumberMap.size());		
	}
	
	/**
	 * Test the encoding of the request package header
	 * @throws IOException 
	 */
	@Test
	public void testRequestPackageHeader() throws IOException {
		final short currentSequenceNumber = sequenceNumberGenerator.getSequeneNumberWithoutIncrement();
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		NetworkPackageEncoder.appendRequestPackageHeader(sequenceNumber, 10, new RoutingHeader(false),
				NetworkConst.REQUEST_TYPE_INSERT_TUPLE, bos);
		
		bos.flush();
		bos.close();

		final byte[] encodedPackage = bos.toByteArray();
		
		Assert.assertEquals(18, encodedPackage.length);
		
		final ByteBuffer bb = ByteBuffer.wrap(encodedPackage);
		bb.order(Const.APPLICATION_BYTE_ORDER);
		
		// Check fields
		Assert.assertEquals(NetworkConst.REQUEST_TYPE_INSERT_TUPLE, bb.getShort());
		Assert.assertEquals(currentSequenceNumber, bb.getShort());
	}
	
	/**
	 * The the encoding and decoding of an insert tuple package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeInsertTuple() throws IOException {
		final Tuple tuple = new Tuple("key", BoundingBox.EMPTY_BOX, "abc".getBytes(), 12);
		final InsertTupleRequest insertPackage = new InsertTupleRequest(new SSTableName("test"), tuple);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(insertPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final InsertTupleRequest decodedPackage = InsertTupleRequest.decodeTuple(bb);
				
		Assert.assertEquals(insertPackage.getTuple(), decodedPackage.getTuple());
		Assert.assertEquals(insertPackage.getTable(), decodedPackage.getTable());
		Assert.assertEquals(insertPackage.getRoutingHeader(), new RoutingHeader(false));
		Assert.assertEquals(insertPackage, decodedPackage);
	}
	
	/**
	 * The the encoding and decoding of an insert tuple package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeInsertTupleWithCustomHeader() throws IOException {
		final RoutingHeader routingHeader = new RoutingHeader(true, (short) 12, Arrays.asList(new DistributedInstance[] { new DistributedInstance("node1:3445")}));
		final Tuple tuple = new Tuple("key", BoundingBox.EMPTY_BOX, "abc".getBytes(), 12);
		final InsertTupleRequest insertPackage = new InsertTupleRequest(routingHeader, new SSTableName("test"), tuple);
		Assert.assertEquals(routingHeader, insertPackage.getRoutingHeader());
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(insertPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final InsertTupleRequest decodedPackage = InsertTupleRequest.decodeTuple(bb);
				
		Assert.assertEquals(insertPackage.getTuple(), decodedPackage.getTuple());
		Assert.assertEquals(insertPackage.getTable(), decodedPackage.getTable());
		Assert.assertEquals(routingHeader, decodedPackage.getRoutingHeader());
		Assert.assertFalse(insertPackage.getRoutingHeader().equals(new RoutingHeader(false)));
		Assert.assertEquals(insertPackage, decodedPackage);
	}
	
	
	/**
	 * The the encoding and decoding of an delete tuple package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeDeleteTuple() throws IOException {
				
		final DeleteTupleRequest deletePackage = new DeleteTupleRequest("test", "key");
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(deletePackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final DeleteTupleRequest decodedPackage = DeleteTupleRequest.decodeTuple(bb);
				
		Assert.assertEquals(deletePackage.getKey(), decodedPackage.getKey());
		Assert.assertEquals(deletePackage.getTable(), decodedPackage.getTable());
		Assert.assertEquals(deletePackage, decodedPackage);
	}
	
	/**
	 * The the encoding and decoding of an create distribution group package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeCreateDistributionGroup() throws IOException {
				
		final CreateDistributionGroupRequest groupPackage = new CreateDistributionGroupRequest("test", (short) 3);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(groupPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final CreateDistributionGroupRequest decodedPackage = CreateDistributionGroupRequest.decodeTuple(bb);
				
		Assert.assertEquals(groupPackage.getDistributionGroup(), decodedPackage.getDistributionGroup());
		Assert.assertEquals(groupPackage.getReplicationFactor(), decodedPackage.getReplicationFactor());
		Assert.assertEquals(groupPackage, decodedPackage);
	}
	
	/**
	 * The the encoding and decoding of an create distribution group package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeDeleteDistributionGroup() throws IOException {
				
		final DeleteDistributionGroupRequest groupPackage = new DeleteDistributionGroupRequest("test");
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(groupPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final DeleteDistributionGroupRequest decodedPackage = DeleteDistributionGroupRequest.decodeTuple(bb);
				
		Assert.assertEquals(groupPackage.getDistributionGroup(), decodedPackage.getDistributionGroup());
		Assert.assertEquals(groupPackage, decodedPackage);
	}
	
	/**
	 * The the encoding and decoding of an delete table package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeDeleteTable() throws IOException {
				
		final DeleteTableRequest deletePackage = new DeleteTableRequest("test");
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(deletePackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final DeleteTableRequest decodedPackage = DeleteTableRequest.decodeTuple(bb);
				
		Assert.assertEquals(deletePackage.getTable(), decodedPackage.getTable());
		Assert.assertEquals(deletePackage, decodedPackage);
	}
	
	
	/**
	 * Test decoding and encoding of the key query
	 * @throws IOException 
	 */
	@Test
	public void testDecodeKeyQuery() throws IOException {
		final String table = "1_mygroup_table1";
		final String key = "key1";
		
		final QueryKeyRequest queryKeyRequest = new QueryKeyRequest(table, key);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		final byte[] encodedPackage = networkPackageToByte(queryKeyRequest, sequenceNumber);
		Assert.assertNotNull(encodedPackage);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		final boolean result = NetworkPackageDecoder.validateRequestPackageHeader(bb, NetworkConst.REQUEST_TYPE_QUERY);
		Assert.assertTrue(result);

		final QueryKeyRequest decodedPackage = QueryKeyRequest.decodeTuple(bb);
		Assert.assertEquals(queryKeyRequest.getKey(), decodedPackage.getKey());
		Assert.assertEquals(queryKeyRequest.getTable(), decodedPackage.getTable());
		
		Assert.assertEquals(NetworkConst.REQUEST_QUERY_KEY, NetworkPackageDecoder.getQueryTypeFromRequest(bb));
	}
	
	/**
	 * Test decode bounding box query
	 * @throws IOException 
	 */
	@Test
	public void testDecodeBoundingBoxQuery() throws IOException {
		final String table = "table1";
		final BoundingBox boundingBox = new BoundingBox(10f, 20f);
		
		final QueryBoundingBoxRequest queryRequest = new QueryBoundingBoxRequest(table, boundingBox);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		byte[] encodedPackage = networkPackageToByte(queryRequest, sequenceNumber);
		Assert.assertNotNull(encodedPackage);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		boolean result = NetworkPackageDecoder.validateRequestPackageHeader(bb, NetworkConst.REQUEST_TYPE_QUERY);
		Assert.assertTrue(result);

		final QueryBoundingBoxRequest decodedPackage = QueryBoundingBoxRequest.decodeTuple(bb);
		Assert.assertEquals(queryRequest.getBoundingBox(), decodedPackage.getBoundingBox());
		Assert.assertEquals(queryRequest.getTable(), decodedPackage.getTable());
		
		Assert.assertEquals(NetworkConst.REQUEST_QUERY_BBOX, NetworkPackageDecoder.getQueryTypeFromRequest(bb));
	}
	
	/**
	 * Test decode time query
	 * @throws IOException 
	 */
	@Test
	public void testDecodeTimeQuery() throws IOException {
		final String table = "table1";
		final long timeStamp = 4711;
		
		final QueryTimeRequest queryRequest = new QueryTimeRequest(table, timeStamp);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		byte[] encodedPackage = networkPackageToByte(queryRequest, sequenceNumber);
		Assert.assertNotNull(encodedPackage);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		boolean result = NetworkPackageDecoder.validateRequestPackageHeader(bb, NetworkConst.REQUEST_TYPE_QUERY);
		Assert.assertTrue(result);

		final QueryTimeRequest decodedPackage = QueryTimeRequest.decodeTuple(bb);
		Assert.assertEquals(queryRequest.getTimestamp(), decodedPackage.getTimestamp());
		Assert.assertEquals(queryRequest.getTable(), decodedPackage.getTable());
		
		Assert.assertEquals(NetworkConst.REQUEST_QUERY_TIME, NetworkPackageDecoder.getQueryTypeFromRequest(bb));
	}
	
	/**
	 * The the encoding and decoding of a list tables package
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeListTable() throws IOException {
				
		final ListTablesRequest listPackage = new ListTablesRequest();
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(listPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final ListTablesRequest decodedPackage = ListTablesRequest.decodeTuple(bb);
				
		Assert.assertEquals(listPackage, decodedPackage);
	}
	
	/**
	 * The the encoding and decoding of the request helo
	 * @throws IOException 
	 */
	@Test
	public void encodeAndDecodeHelo() throws IOException {
				
		final HeloRequest listPackage = new HeloRequest();
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedVersion = networkPackageToByte(listPackage, sequenceNumber);
		Assert.assertNotNull(encodedVersion);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedVersion);
		final HeloRequest decodedPackage = HeloRequest.decodeTuple(bb);
				
		Assert.assertEquals(listPackage, decodedPackage);
	}
	
	/**
	 * Decode an encoded package
	 * @throws IOException 
	 */
	@Test
	public void testDecodePackage() throws IOException {
		final Tuple tuple = new Tuple("key", BoundingBox.EMPTY_BOX, "abc".getBytes(), 12);
		final InsertTupleRequest insertPackage = new InsertTupleRequest(new SSTableName("test"), tuple);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedPackage = networkPackageToByte(insertPackage, sequenceNumber);
		Assert.assertNotNull(encodedPackage);
				
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		boolean result = NetworkPackageDecoder.validateRequestPackageHeader(bb, NetworkConst.REQUEST_TYPE_INSERT_TUPLE);
		Assert.assertTrue(result);
	}	
	
	/**
	 * Get the sequence number from a package
	 * @throws IOException 
	 */
	@Test
	public void testGetSequenceNumber() throws IOException {
		final Tuple tuple = new Tuple("key", BoundingBox.EMPTY_BOX, "abc".getBytes(), 12);
		final InsertTupleRequest insertPackage = new InsertTupleRequest(new SSTableName("test"), tuple);
		
		// Increment to avoid sequenceNumber = 0
		sequenceNumberGenerator.getNextSequenceNummber();
		sequenceNumberGenerator.getNextSequenceNummber();
		
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedPackage = networkPackageToByte(insertPackage, sequenceNumber);
		
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		short packageSequencenUmber = NetworkPackageDecoder.getRequestIDFromRequestPackage(bb);
		
		Assert.assertEquals(sequenceNumber, packageSequencenUmber);		
	}
	
	/**
	 * Read the body length from a request package
	 * @throws IOException 
	 */
	@Test
	public void testGetRequestBodyLength() throws IOException {
		final Tuple tuple = new Tuple("key", BoundingBox.EMPTY_BOX, "abc".getBytes(), 12);
		final InsertTupleRequest insertPackage = new InsertTupleRequest(new SSTableName("test"), tuple);
		final short sequenceNumber = sequenceNumberGenerator.getNextSequenceNummber();
		
		byte[] encodedPackage = networkPackageToByte(insertPackage, sequenceNumber);
		Assert.assertNotNull(encodedPackage);
		
		// 18 Byte package header
		int calculatedBodyLength = encodedPackage.length - 18;
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		long bodyLength = NetworkPackageDecoder.getBodyLengthFromRequestPackage(bb);
		
		Assert.assertEquals(calculatedBodyLength, bodyLength);
	}
	
	/**
	 * Get the package type from the response
	 */
	@Test
	public void getPackageTypeFromResponse1() {
		final SuccessResponse response = new SuccessResponse((short) 2);
		byte[] encodedPackage = response.getByteArray();
		Assert.assertNotNull(encodedPackage);
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		Assert.assertEquals(NetworkConst.RESPONSE_SUCCESS, NetworkPackageDecoder.getPackageTypeFromResponse(bb));
	}
	
	/**
	 * Get the package type from the response
	 */
	@Test
	public void getPackageTypeFromResponse2() {
		final SuccessWithBodyResponse response = new SuccessWithBodyResponse((short) 2, "abc");
		byte[] encodedPackage = response.getByteArray();
		Assert.assertNotNull(encodedPackage);
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		Assert.assertEquals(NetworkConst.RESPONSE_SUCCESS_WITH_BODY, NetworkPackageDecoder.getPackageTypeFromResponse(bb));
	}
	
	/**
	 * Read the body length from a result package
	 */
	@Test
	public void testGetResultBodyLength1() {
		final SuccessResponse response = new SuccessResponse((short) 2);
		byte[] encodedPackage = response.getByteArray();
		Assert.assertNotNull(encodedPackage);
		
		// 8 Byte package header
		int calculatedBodyLength = 0;
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		long bodyLength = NetworkPackageDecoder.getBodyLengthFromResponsePackage(bb);
		
		Assert.assertEquals(calculatedBodyLength, bodyLength);
	}
	
	/**
	 * Read the body length from a result package
	 */
	@Test
	public void testGetResultBodyLength2() {
		final SuccessWithBodyResponse response = new SuccessWithBodyResponse((short) 2, "abc");
		byte[] encodedPackage = response.getByteArray();
		Assert.assertNotNull(encodedPackage);
		
		// 2 Byte (short) data length
		int calculatedBodyLength = 5;
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		long bodyLength = NetworkPackageDecoder.getBodyLengthFromResponsePackage(bb);
		
		Assert.assertEquals(calculatedBodyLength, bodyLength);
	}
	
	/**
	 * Try to encode and decode the list tables response
	 */
	@Test
	public void testListTablesResponse() {
		final List<SSTableName> tables = new ArrayList<SSTableName>();
		tables.add(new SSTableName("3_group1_table1"));
		tables.add(new SSTableName("3_group1_testtable"));
		tables.add(new SSTableName("3_group1_test4711"));
		tables.add(new SSTableName("3_group1_mytest57"));
		
		final ListTablesResponse response = new ListTablesResponse((short) 3, tables);
		byte[] encodedPackage = response.getByteArray();
		Assert.assertNotNull(encodedPackage);

		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		final ListTablesResponse responseDecoded = ListTablesResponse.decodePackage(bb);
		final List<SSTableName> myTables = responseDecoded.getTables();
		Assert.assertEquals(tables, myTables);
		Assert.assertEquals(tables.size(), myTables.size());
	}
	
	/**
	 * Try to encode and decode the single tuple response 
	 */
	@Test
	public void testSingleTupleResponse() {
		final String tablename = "table1";
		final Tuple tuple = new Tuple("abc", BoundingBox.EMPTY_BOX, "databytes".getBytes());
		
		final TupleResponse singleTupleResponse = new TupleResponse((short) 4, tablename, tuple);
		byte[] encodedPackage = singleTupleResponse.getByteArray();
		Assert.assertNotNull(encodedPackage);
		
		final ByteBuffer bb = NetworkPackageDecoder.encapsulateBytes(encodedPackage);
		final TupleResponse responseDecoded = TupleResponse.decodePackage(bb);
		Assert.assertEquals(singleTupleResponse.getTable(), responseDecoded.getTable());
		Assert.assertEquals(singleTupleResponse.getTuple(), responseDecoded.getTuple());		
	}
	
	/**
	 * Test the failed operation
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testFutureFailureState() throws InterruptedException, ExecutionException {
		final ClientOperationFuture future = new ClientOperationFuture();
		Assert.assertFalse(future.isFailed());
		Assert.assertFalse(future.isDone());
		future.setFailedState();
		Assert.assertTrue(future.isFailed());
		Assert.assertTrue(future.get(0) == null);
	}
	
}
