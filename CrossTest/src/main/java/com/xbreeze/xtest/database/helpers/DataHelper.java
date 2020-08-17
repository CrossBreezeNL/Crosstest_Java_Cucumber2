/*******************************************************************************
 * Copyright (c) 2019 CrossBreeze
 *
 *  This file is part of CrossTest.
 *
 *     CrossTest is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CrossTest is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CrossTest.  If not, see <https://www.gnu.org/licenses/>.
 *     
 * Contributors:
 *     Willem Otten - CrossBreeze
 *     Harmen Wessels - CrossBreeze
 *******************************************************************************/
package com.xbreeze.xtest.database.helpers;


import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;

import org.apache.commons.codec.digest.DigestUtils;
import java.util.logging.Logger;

import com.xbreeze.xtest.config.DatabaseConfig;
import com.xbreeze.xtest.exception.XTestDatabaseException;
import com.xbreeze.xtest.exception.XTestException;
import com.xbreeze.xtest.result.ResultContext;

import io.cucumber.datatable.DataTable;

public class DataHelper {
	
	static final Logger logger = Logger.getLogger(DataHelper.class.getName());
	private ResultContext _resultContext;
	private HashMap<String, Connection> _connections;

	public DataHelper(ResultContext resultContext) {
		this._resultContext = resultContext;
		_connections = new HashMap<>();
	}
	
	/**
	 * Populate a cached rowset from a Cucumber datatable
	 * @param tableName the tablename to query for rowset definition
	 * @param dataTable the content to store in the rowet
	 * @param connection connection to use
	 * @param limitToDefinedColumns indicator that steers if the rowset should only contain fields in the dataTable or all available fields in the table
	 * @return
	 * @throws XTestDatabaseException
	 */
	public CachedRowSet dataTableToRowSet(String tableName, DataTable dataTable, Connection connection, Boolean limitToDefinedColumns, Boolean distinct, Boolean includeEmptyRows, DatabaseConfig dbConfig) throws XTestDatabaseException{
			String selectQuery = getSQLQuery(dataTable, tableName, limitToDefinedColumns, dbConfig);
			
			//Create and populate rowset
			CachedRowSet crs = DatabaseCommandExecutor.executeCommandWithCachedResult(connection, selectQuery, dbConfig);
			
//			try {
//				crs.setTableName(tableName);
//			} catch (SQLException e) {
//				// TODO Auto-generated catch block
//				throw new XTestDatabaseException(e.getMessage());
//			}
			populateCachedRowSetFromDataTable(crs, dataTable, distinct, includeEmptyRows, limitToDefinedColumns, dbConfig);
			return crs;
	}
	
	public void populateCachedRowSetFromDataTable(CachedRowSet crs, DataTable dataTable, boolean distinct, boolean includeEmptyRows, boolean limitToDefinedColumns, DatabaseConfig dbConfig) throws XTestDatabaseException {
		List<Map<String, String>> list = dataTable.asMaps(String.class, String.class);
		populateRowSet(crs, list, distinct, includeEmptyRows, limitToDefinedColumns, dbConfig);
		try {
			//Move cursor to current row or navigating the rowset will not work
			crs.moveToCurrentRow();
		}
		catch(SQLException exc) {
			throw new XTestDatabaseException(exc.getMessage());
		}
	}
	
	/**
	 * Write a data table to the database
	 * @param tableName The table to write the data to
	 * @param dataTable The data to write to the database
	 * @param dbConfig Connection to use
	 * @param insertDistinct Determines if distinct rows are written or all rows
	 */
	public void writeDataTableToDatabase(String tableName, DataTable dataTable, DatabaseConfig dbConfig, Boolean insertDistinct, Boolean limitToDefinedColumns) throws XTestDatabaseException {
		List<Map<String, String>> list = dataTable.asMaps(String.class, String.class);
		if (list.size() > 0) {
				tableName = dbConfig.getQualifiedTableName(tableName);
				String selectQuery = getSQLQuery(dataTable, tableName, false, dbConfig);				
				//Create empty rowset
				logger.info("Retrieving target data structure");
				CachedRowSet rs = DatabaseCommandExecutor.executeCommandWithCachedResult(getConnection(dbConfig), selectQuery, dbConfig);
				//Populate so values are converted to proper data types
				logger.info("Populating cached rowset for inserting");
				populateRowSet(rs, list, insertDistinct, false, limitToDefinedColumns, dbConfig);
				
				try {
					//Construct a batch for inserting rows because some JDBC drivers, such as SAP, do not support updateable rowset or resultset
					rs.moveToCurrentRow();
					rs.beforeFirst();
					//Create a batch statement
					ResultSetMetaData meta = rs.getMetaData();
	
					List<String> columns = new ArrayList<>();					
			        for (int i = 1; i <= meta.getColumnCount(); i++) {
			            columns.add(dbConfig.getFormattedColumnName(meta.getColumnName(i)));
			        }
			        logger.info("Prepare inserting of records");
			        PreparedStatement s2 = getConnection(dbConfig).prepareStatement(
						                "INSERT INTO " + tableName + " ("
						              + columns.stream().collect(Collectors.joining(", "))
						              + ") VALUES ("
						              + columns.stream().map(c -> "?").collect(Collectors.joining(", "))
						              + ")"
						        );
			        
		            while (rs.next()) {
		                for (int i = 1; i <= meta.getColumnCount(); i++) {
		                	
		                	meta.getColumnName(i);
		                    Object cellValue = rs.getObject(i);
		                    if (cellValue != null)
		                    	s2.setObject(i, cellValue);
		                    else
		                    	s2.setNull(i, meta.getColumnType(i));
		                }
		
		                s2.addBatch();
		            }
		
		            logger.info("Executing insert");
		            s2.executeBatch();
		            s2.close();
		        }
		 
			catch(SQLException exc) {
				if(exc.getNextException() != null) {
					logger.info(String.format("Next exception: %s", exc.getNextException().toString()));
				}				
				throw XTestDatabaseException.createXTestDatabaseException("Error inserting data", exc);
			}
		}
	}
			
	/**
	 * Get a Select statement based on a result table	
	 * @param dataTable The result table used to determine the columns for the select
	 * @param tableName The qualified table name
	 * @return a string with the appropriate Select statement
	 */
	public String getSQLSelectFrom(DataTable dataTable, String tableName, DatabaseConfig dbConfig) {
		String sqlStatement = null;
		List<Map<String, String>> list = dataTable.asMaps(String.class, String.class);
		if (list.size() > 0) {
		
			//Create a select that results in an empty result in order to get 
			//proper column definitions
			String columnList = "";
			//Get column names from first row
			Set<String> columnNames = list.get(0).keySet();		
			
			for (String col:columnNames){
				columnList = columnList.concat(dbConfig.getFormattedColumnName(col)).concat(",");					
				
			}				
			
			columnList = columnList.substring(0,columnList.length() - 1);		
			sqlStatement = "SELECT ".concat(columnList).concat(" FROM ").concat(tableName);
		}
		return sqlStatement;
	}
	
	
	/**
	 * Method creates a SQL string needed to populate a rowset
	 * @param dataTable The datatable to use as a base
	 * @param tableName The table that is the basis for the rowset
	 * @param OnlyDefinedColumns Use all columns of the table (false) or only the columns specified in the data table
	 * @return a SQL statement that can be used to construct an empty rowset.
	 */
	private String getSQLQuery(DataTable dataTable, String tableName, Boolean OnlyDefinedColumns, DatabaseConfig dbConfig) {
		String sqlStatement = null;
	
		if (OnlyDefinedColumns) {
			sqlStatement = getSQLSelectFrom(dataTable, tableName, dbConfig).concat(" WHERE 1 = 0;");
		}
		else {
			sqlStatement = String.format("SELECT * FROM %s WHERE 1 = 0;", tableName);
		}
		return sqlStatement;		
	}
	
	/**
	 * 
	 * @param rowSet the rowset to populate
	 * @param dataTableList Cucumber datatable in list format	
	  * @throws XTestDatabaseException
	 */
	
	public void populateRowSet(RowSet rowSet, List<Map<String, String>> dataTableList, Boolean distinct, Boolean includeEmptyRows, Boolean limitToDefinedColumns, DatabaseConfig dbConfig) throws XTestDatabaseException {
		//Populate rowset
		try {
			ResultSetMetaData rmd = rowSet.getMetaData();
			
			//Check if there are invalid columns in the dataTable and report error if only existing columns should be processed
			if (limitToDefinedColumns && dataTableList.size() > 0) {
				Map<String, String> record = dataTableList.get(0);
				for (String field:record.keySet()) {
					Boolean found = false;
					for (int i = 1; i <= rmd.getColumnCount(); i++) {
						if (rmd.getColumnName(i).equalsIgnoreCase(field)) {
							//If the field was found, break the for loop
							found = true;
							break;
						}						
					}
					if (!found) {
						throw new XTestDatabaseException(String.format("Field %s from scenario data is not found in target table or view", field));
					}
				}
			}
			Set<String> processedRecords = new HashSet<String>();
			//Add values to rowset
			for (Map<String, String> dRecord:dataTableList) {
				//Convert the map to a treemap in order for the keys to be case insensitive
				TreeMap<String, String> dataRecord = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				dataRecord.putAll(dRecord);
				String recordKey= "";
				//Check if a relevant value is set at all, otherwise do not insert row
				Boolean hasRelevantValueSet = false;
				rowSet.moveToInsertRow();
				//Set fields from datatable to specified value				
				for (int i = 1; i <= rmd.getColumnCount(); i++) {	
					if (dataRecord.containsKey(rmd.getColumnName(i))) {
						String cellValue = dataRecord.get(rmd.getColumnName(i));
						// TODO: Disable this behaviour for non composite inserts.
						if (!cellValue.equalsIgnoreCase("")) {
							hasRelevantValueSet = true;
						}
						setFieldValue(rowSet, i, cellValue, rmd.getColumnType(i));
						recordKey = recordKey.concat("|").concat(cellValue);
					}
					else {
						//If field is not specified, check for default value otherwise set to null						
						if (dbConfig.getTemplate() != null && dbConfig.getTemplate().getDefaultValue(rmd.getColumnName(i)) != null) {
							setFieldValue(rowSet, i, dbConfig.getTemplate().getDefaultValue(rmd.getColumnName(i)), rmd.getColumnType(i));
						}						
						else {
							rowSet.updateNull(i);
						}
					}
						
				}		
				
				//Only add record when either distinct is false or the record is not added previously
				if ((hasRelevantValueSet || includeEmptyRows) && (distinct == false || !processedRecords.contains(recordKey))) {
					//Insert the new row
					logger.info("Inserting row in rowSet");
					for (int i=1;i <= rowSet.getMetaData().getColumnCount();i++) {
						logger.info(String.format("Field value for field %s is %s", rowSet.getMetaData().getColumnName(i), String.valueOf(rowSet.getObject(i))));
					}
					rowSet.insertRow();
					if (distinct) {
						processedRecords.add(recordKey);
					}
				}
				else {
					//cancel row updates if row should not be added
					for (int i = 1; i <= rmd.getColumnCount(); i++) {
						rowSet.updateNull(i);
					}
					
				}
				
			}	
			//Move cursor back to current row otherwise navigation will not work later on
			//rowSet.moveToCurrentRow();
			
		} catch (SQLException exc) {	
			//exc.printStackTrace();
			logger.severe(String.format("Error code: %d, SQL State: %s", exc.getErrorCode(), exc.getSQLState()));
			throw new XTestDatabaseException(String.format("Error while populating rowset: %s", exc.getMessage()));
		}
	}
		
	/**
	 * Set a field in a rowset using the appropriate setter, based on datatype
	 * @param crs The rowset where a field needs to be set
	 * @param fieldname The fieldname to set
	 * @param fieldPosition The field ordinal position
	 * @param fieldValue the value to set
	 * @param dataType Datatype of the field
	 * @throws XTestDatabaseException
	 */
	private void setFieldValue (RowSet crs, int fieldPosition, String fieldValue, int dataType) throws XTestDatabaseException{
		try {
			//For teradata: access fields on index and not on name
			//https://teradata-docs.s3.amazonaws.com/doc/connectivity/jdbc/reference/current/jdbcug_chapter_4.html
			
			//If field value is an empty string, set value to null
			String fieldName = crs.getMetaData().getColumnName(fieldPosition);
			
			//if fieldValue references a variable, replace it with variable contents first
			if (fieldValue.startsWith(ResultContext.VARIABLE_PREFIX)) {
				logger.info(String.format("Field %s is set with variable %s", fieldName, fieldValue));
				try {
					fieldValue = _resultContext.getVariable(fieldValue);
				} catch (XTestException exc) {
					throw new XTestDatabaseException(XTestException.getCompositeErrorMessage(String.format("Error getting variable valie for field %s",  fieldName), exc));
				}
				logger.info(String.format("Replace variable with value %s for field %s", fieldValue, fieldName));
			}
			
			logger.info(String.format("Setting value %s for field %s with datatype %d", fieldValue, fieldName, dataType));
			if (fieldValue.equalsIgnoreCase("")) {
				crs.updateNull(fieldPosition);
			}
			
			else if ((dataType == java.sql.Types.DECIMAL) || (dataType == java.sql.Types.DOUBLE) || (dataType == java.sql.Types.NUMERIC)) {
				crs.updateDouble(fieldPosition, Double.valueOf(fieldValue));
			}
			
			else if (
					(dataType == java.sql.Types.VARCHAR) || (dataType == java.sql.Types.CHAR) ||
					(dataType == java.sql.Types.NVARCHAR) || (dataType == java.sql.Types.NCHAR) ||
					(dataType == java.sql.Types.LONGNVARCHAR) || (dataType == java.sql.Types.LONGVARCHAR) 
				) {
				crs.updateString(fieldPosition, fieldValue);
			}
			
			else if (dataType == java.sql.Types.BIGINT) {
				crs.updateLong(fieldPosition, java.lang.Long.parseLong(fieldValue));
			}
			
			else if (
				(dataType == java.sql.Types.INTEGER) ||
				(dataType == java.sql.Types.SMALLINT) ||
				(dataType == java.sql.Types.TINYINT) 
				) {
				crs.updateInt(fieldPosition, Integer.parseInt(fieldValue));
			}
			else if (dataType == java.sql.Types.DATE) {				
				crs.updateDate(fieldPosition, Date.valueOf(fieldValue));
			}
			else if (dataType == java.sql.Types.TIME) {
				crs.updateTime(fieldPosition, Time.valueOf(fieldValue));
			}
			else if ((dataType == java.sql.Types.BOOLEAN) || (dataType == java.sql.Types.BIT)) {
				crs.updateBoolean(fieldPosition, Boolean.parseBoolean(fieldValue));
			}
			else if (dataType == java.sql.Types.TIMESTAMP) {
				
			
				try {
					// try to parse the field value as a timestamp
					Timestamp tsVal = Timestamp.valueOf(fieldValue);
					crs.updateTimestamp(fieldPosition,tsVal);
				}
				catch (IllegalArgumentException exc) {
					//If not parseable as timestamp, process as date.
					Date dateVal = Date.valueOf(fieldValue);
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					String timeVal =df.format(dateVal);
					crs.updateTimestamp(fieldPosition,Timestamp.valueOf(timeVal));	
				}
				
			}
			
			else {
				throw new XTestDatabaseException(String.format("Unsupported datatype %d for field %s", dataType, fieldName));
			}
			
		} catch (SQLException exc) {
			//TODO handle exception
			//exc.printStackTrace();
			throw new XTestDatabaseException(exc.getMessage());
		}
	}
	
	/**
	 * Get a field value from a rowset in the appropriate datatype and then convert in to string
	 * @param crs
	 * @param fieldPosition
	 * @return the field value converted to string
	 * @throws XTestDatabaseException 
	 */
	private String getFieldValue(RowSet crs, int fieldPosition) throws XTestDatabaseException {
		try {
			String fieldName = crs.getMetaData().getColumnName(fieldPosition);
			int dataType = crs.getMetaData().getColumnType(fieldPosition);
			logger.info(String.format("Getting value for field %s with datatype %d", fieldName, dataType));
			//Read value as string to determine if the field is null
			String strVal = crs.getString(fieldPosition);
			if (crs.wasNull()) {
				logger.info("Column read was null");
				return "";			
			}
			
			else if ((dataType == java.sql.Types.DECIMAL) || (dataType == java.sql.Types.DOUBLE) || (dataType == java.sql.Types.NUMERIC)) {
				Double dblVal = crs.getDouble(fieldPosition);
				return String.valueOf(dblVal);
			}
			
			else if (
					(dataType == java.sql.Types.VARCHAR) || 
					(dataType == java.sql.Types.NVARCHAR) ||
					(dataType == java.sql.Types.LONGNVARCHAR) || 
					(dataType == java.sql.Types.LONGVARCHAR) 
				) {
				return strVal;
			}
			//In case of char datatype, trim result obtained from database
			else if (
					(dataType == java.sql.Types.CHAR) ||
					(dataType == java.sql.Types.NCHAR)  
				) {
				return strVal.trim();
			}
			
			else if (dataType == java.sql.Types.BIGINT) {
				Long bigIntVal = crs.getLong(fieldPosition);
				return String.valueOf(bigIntVal);
			}
			
			else if (
				(dataType == java.sql.Types.INTEGER) ||
				(dataType == java.sql.Types.SMALLINT) ||
				(dataType == java.sql.Types.TINYINT) 				
				) {
				Integer intVal = crs.getInt(fieldPosition);
				return String.valueOf(intVal);
			}
			else if ((dataType == java.sql.Types.BOOLEAN) || (dataType == java.sql.Types.BIT)) {
				boolean boolVal = crs.getBoolean(fieldPosition);
				return String.valueOf(boolVal);
			}			
			else if (dataType == java.sql.Types.DATE) {				
				Date dateVal = crs.getDate(fieldPosition);
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
				return df.format(dateVal);
			}
			else if (dataType == java.sql.Types.TIME) {
				Time timeVal = crs.getTime(fieldPosition);
				return String.valueOf(timeVal);
			}
			else if (dataType == java.sql.Types.TIMESTAMP) {
				
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				try {
					// try to parse the field value as a timestamp
					Timestamp tsVal = crs.getTimestamp(fieldPosition);
					return df.format(tsVal);
				}
				catch (IllegalArgumentException exc) {
					//If not parseable as timestamp, process as date.
					Date dateVal = crs.getDate(fieldPosition);					
					return df.format(dateVal);
					
				}
				
			}
			
			else {
				throw new XTestDatabaseException(String.format("Unsupported datatype %d for field %s", dataType, fieldName));
			}
		
	} catch (SQLException exc) {
		//TODO handle exception
		//exc.printStackTrace();
		throw  XTestDatabaseException.createXTestDatabaseException("Exception while setting field", exc);
	}
	}
	
	/**
	 * Returns a hashmap containing al hashes with pointer to row number in the rowset
	 * @param crs The rowset for which hashes need to be calculated
	 * @param fieldNames The field names that need to be included in the hash
	 * @return the hashmap with hashes
	 * @throws XTestException
	 */
	public HashMap<String, RecordInformation> getHashSet(RowSet crs, LinkedList<String> fieldNames) throws XTestException {
		HashMap<String, RecordInformation> hashMap = new HashMap<>();
		try {
			crs.beforeFirst();			
			int rowCounter = 0;
			while (crs.next()) {
				StringBuilder hashInput = new StringBuilder();
				rowCounter++;
				for (int i = 1; i <= crs.getMetaData().getColumnCount(); i++) {
					String val = getFieldValue(crs, i);	
					String fieldName = crs.getMetaData().getColumnName(i).toLowerCase();
					if (fieldNames.contains(fieldName)) {
						hashInput.append(val);
					}
				}
				String hashValue = DigestUtils.md5Hex(hashInput.toString());
				if (hashMap.containsKey(hashValue)) {
					hashMap.get(hashValue).addRecordNumber(rowCounter);
					logger.info(String.format("Found duplicate record for hash value %s at row %d", hashValue, rowCounter));
					//throw new XTestException("Duplicate records is not supported in actual or expected result");
				}
				else {
					hashMap.put(hashValue, new RecordInformation(hashValue, rowCounter));
				}
			}
			return hashMap;
		} catch (SQLException exc) {
			throw new XTestDatabaseException(exc.getMessage());
		}
	}
	
	public LinkedList<String> rowSetRecordToList(RowSet crs) throws XTestException {
		LinkedList<String> record = new LinkedList<>();
		try {
			for (int i = 1; i <= crs.getMetaData().getColumnCount(); i++) {
				String val = crs.getString(i);
				if (crs.wasNull()) {
					record.addLast("");
				} else {
					record.addLast(val);
				}
			}
		}
		catch (SQLException exc) {
			throw new XTestDatabaseException(String.format("Error getting rowset record: %s", exc.getMessage()));	
		}
		return record;
	}
	
	public LinkedList<String> rowSetHeader(RowSet crs) throws XTestException {
		LinkedList<String> header = new LinkedList<>();
		try {
			for (int i = 1; i <= crs.getMetaData().getColumnCount(); i++) {
				header.addLast(crs.getMetaData().getColumnName(i));
			}			
		}
		catch (SQLException exc) {
			throw new XTestDatabaseException(String.format("Error getting rowset header: %s", exc.getMessage()));
		}
		return header;
	}
	
	public Connection getConnection(DatabaseConfig dbConfig) throws XTestDatabaseException{
		return this.getConnection(dbConfig, dbConfig.getName());
	}
	
	private Connection getConnection(DatabaseConfig dbConfig, String configName) throws XTestDatabaseException {
		if (!_connections.containsKey(configName)) {
			_connections.put(configName, this.getNewConnection(dbConfig));
		}
		return _connections.get(configName);
	}
	
	
	private Connection getNewConnection(DatabaseConfig dbConfig) throws XTestDatabaseException {
		Connection conn = null;
	    Properties connectionProps = new Properties();
	    //Set username and password if given
	    if (dbConfig.getDatabaseServerConfig().getUsername() != null) {
	    	connectionProps.put("user", dbConfig.getDatabaseServerConfig().getUsername());
	    }
	    if (dbConfig.getDatabaseServerConfig().getPassword() != null) {		    
	    	connectionProps.put("password", dbConfig.getDatabaseServerConfig().getPassword());
	    }
	    
	    try {
			conn = DriverManager.getConnection(dbConfig.getDatabaseServerConfig().getJDBCUrl(), connectionProps);
			//If scenario is run in a transaction set autocommit to false on the connection
			if (this._resultContext.isInTransaction()) {
				logger.info(String.format("Running in a transaction, setting autocommit off for %s", dbConfig.getName()));
				conn.setAutoCommit(false);
			}

		} catch (SQLException e) {
			throw new XTestDatabaseException(e.getMessage());
		}
	    
	    //If a schema was given on the database config, try to set it as a default catalog or schema
	    //behaviour is DB platform specific
	    if (dbConfig.getSchema().trim().length() > 0) {
	    	//If a custom statement is specified for set schema, subsitute database name in template and execute statement
	    	if (dbConfig.getDatabaseServerConfig().getSetSchemaTemplate() != null) {	    		
	    		String stmtText = dbConfig.getDatabaseServerConfig().getSetSchemaTemplate().replace("{SCHEMA}",dbConfig.getSchema());
	    		logger.info(String.format("Setting default catalog using setSchema template, statement is %s", stmtText));
	    		try {
	    		  Statement stmt = conn.createStatement() ;
	    		  stmt.executeUpdate (stmtText);
	    		  stmt.close(); 
	    		}
	    		catch (SQLException exc) {
	    			throw new XTestDatabaseException(String.format("Could not set default catalog using setSchema template: %s", exc.getMessage()));	    			
	    		}
	    	}
	    	//If no custom statement is specified, try to set db using setSchema and setCatalog
	    	else {
		    	boolean schemaSet = false;
		    	boolean dbSet = false; 
			    try {
			    	//Set the catalog based on the schema
			    	logger.info(String.format("Setting default catalog to %s", dbConfig.getSchema()));
					conn.setCatalog(dbConfig.getSchema());
					dbSet = true;
			    }
			    catch (SQLException | AbstractMethodError exc){
			    	logger.info(String.format("Could not set catalog based on schema %s: %s", dbConfig.getSchema(), exc.getMessage()));		    	
			    }
			    
			    try {
		    		logger.info(String.format("Setting default schema to: %s", dbConfig.getSchema()));
		    		conn.setSchema(dbConfig.getSchema());
		    		schemaSet = true;
		    	}
		    	catch (SQLException | AbstractMethodError excs) {
		    		logger.info(String.format("Could not set schema to %s: %s", dbConfig.getSchema(), excs.getMessage()));
		    		if (dbSet == false && schemaSet == false) {
		    			throw new XTestDatabaseException(String.format("Could not set default catalog or schema to %s", dbConfig.getSchema()));
		    		}
		    	}
	    	}
	    }
	    logger.info(String.format("Connected to database %s", dbConfig.getName()));
	    return conn;
	}
	
	/***
	 * Close all connections, if running in a transaction, rollback on all open database connections
	 */
	public void closeConnections() {
		for(Entry<String, Connection> connectionEntry:_connections.entrySet()) {
			Connection con = connectionEntry.getValue();
			String conName = connectionEntry.getKey();
			logger.info(String.format("Closing connection %s", conName));
			try {
				if (_resultContext.isInTransaction() && (con.isClosed() == false)) {
					logger.info(String.format("Rollback on %s", conName));
					con.rollback();
				}
			}
			catch(SQLException exc) {
				logger.severe(String.format("Error during rollback on %s: %s", conName, exc.getMessage()));
			}
			try {
				con.close();
			} catch (SQLException exc) {
				logger.severe(String.format("Error during close of connection to %s: %s", conName, exc.getMessage()));
			}
		}
		this._resultContext.stopTransaction();
	}
}
