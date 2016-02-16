package de.fernunihagen.dna.jkn.scalephant.network;

import java.nio.ByteOrder;

public class NetworkConst {
	
	/**
	 *  The port for client requests
	 */
	public final static int NETWORK_PORT = 50505;
	
	/**
	 *  The amount of threads to handle client connections
	 */
	public final static int SERVER_CONNECTION_THREADS = 10;
	
	/**
	 * The version of the network protocol
	 */
	public static final byte PROTOCOL_VERSION = 0x01;
	
	/**
	 * Byte order for network communication
	 */
	public static final ByteOrder NETWORK_BYTEORDER = ByteOrder.BIG_ENDIAN;
	
	/**
	 * Request type insert tuple
	 */
	public static final byte REQUEST_TYPE_INSERT_TUPLE = 0x00;
	
	/**
	 * Request type delete tuple
	 */
	public static final byte REQUEST_TYPE_DELETE_TUPLE = 0x01;
	
	/**
	 * Request type delete table
	 */
	public static final byte REQUEST_TYPE_DELETE_TABLE = 0x02;
	
	/**
	 * Request type list tables
	 */
	public static final byte REQUEST_TYPE_LIST_TABLES = 0x03;
	
	/**
	 * Request type disconnect
	 */
	public static final byte REQUEST_TYPE_DISCONNECT = 0x04;
	
	/**
	 * Request type query
	 */
	public static final byte REQUEST_TYPE_QUERY = 0x05;
	
	
	/**
	 * Query type key
	 */
	public static final byte REQUEST_QUERY_KEY = 0x01;
	
	/**
	 * Query type bounding box
	 */
	public static final byte REQUEST_QUERY_BBOX = 0x02;
	
	
	/**
	 * Response type success
	 */
	public static final byte RESPONSE_SUCCESS = 0x00;
	
	/**
	 * Response type with body
	 */
	public static final byte RESPONSE_SUCCESS_WITH_BODY = 0x01;
	
	/**
	 * Response type error
	 */
	public static final byte RESPONSE_ERROR = 0x02;
	
	/**
	 * Response type error with body
	 */
	public static final byte RESPONSE_ERROR_WITH_BODY = 0x03;
	
	/**
	 * Response of the list tables request
	 */
	public static final byte RESPONSE_LIST_TABLES = 0x04;
}
