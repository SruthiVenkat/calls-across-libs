package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * DB - librarycallsdb_new
 * Tables - caller_callee_count, all_methods
 * 
 * create database testdb;
 * 
 * create table caller_callee_count (id SERIAL PRIMARY
 * KEY, caller_method_id int, callee_method_id int,count int, FOREIGN KEY
 * (caller_method_id) REFERENCES all_methods(id) ON DELETE CASCADE, FOREIGN KEY
 * (callee_method_id) REFERENCES all_methods(id) ON DELETE CASCADE);
 * 
 * create table all_methods (id SERIAL PRIMARY KEY, method_name
 * varchar(255),library_name varchar(255), UNIQUE(method_name, library_name), FOREIGN KEY
 * (library_name) REFERENCES libs_info(library_name) ON DELETE CASCADE);
 *
 * create table if not exists libs_info (library_name varchar(255) PRIMARY KEY, total_count int, classes text);
 */
public class DatabaseConnector {
	private static DatabaseConnector dc;
	private static Connection conn;
	private final String url = "jdbc:postgresql://localhost/librarycallsdb_new";
	private final String user = "postgres";
	private final String password = "password";

	private DatabaseConnector() {
		createDBAndTables();
	}
	
	public static DatabaseConnector buildDatabaseConnector() {
		if (dc == null) {
			dc = new DatabaseConnector();
			System.out.println("called");		
		}
		return dc;
	}
	/**
	 * Connect to the PostgreSQL database
	 *
	 * @return a Connection object
	 */
	public void connect() {
		try {
			conn = DriverManager.getConnection(url, user, password);
		} catch(SQLException e) {
			System.out.println(e);		
		}
	}
	
	public void createDBAndTables() {
		try {
			conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/", user, password);
		} catch(SQLException e) {
			System.out.println(e);		
		}
		String SQL1 = "CREATE DATABASE librarycallsdb_new;";
		try (PreparedStatement pstmt = conn.prepareStatement(SQL1)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			if (ex.getSQLState().equals("42P04"))
				System.out.println("Using the librarycallsdb_new database");
			else
				System.out.println(ex);
		}
		connect();	
		String SQL2 = "create table if not exists libs_info (library_name varchar(255) PRIMARY KEY, total_count int, classes text);";
		try (PreparedStatement pstmt = conn.prepareStatement(SQL2)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		String SQL3 = "create table if not exists all_methods (id SERIAL PRIMARY KEY, method_name varchar(255),library_name varchar(255), UNIQUE(method_name, library_name), FOREIGN KEY(library_name) REFERENCES libs_info(library_name) ON DELETE CASCADE);";
		try (PreparedStatement pstmt = conn.prepareStatement(SQL3)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		String SQL4 = "create table if not exists caller_callee_count (id SERIAL PRIMARY KEY, caller_method_id int, callee_method_id int,count int, FOREIGN KEY (caller_method_id) REFERENCES all_methods(id) ON DELETE CASCADE, FOREIGN KEY (callee_method_id) REFERENCES all_methods(id) ON DELETE CASCADE);";
		try (PreparedStatement pstmt = conn.prepareStatement(SQL4)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			System.out.println(ex);
		}
	}
	
	public void addToLibsInfoTable(Map<String, ArrayList<Object>> libsAndTotalCountsAndClasses) {
		String SQL1 = "INSERT INTO libs_info(library_name,total_count,classes) " + "VALUES(?,?,?);";
		for (String libName: libsAndTotalCountsAndClasses.keySet()) {
			int totalCount = (int) libsAndTotalCountsAndClasses.get(libName).get(0);
			String classes = (String) libsAndTotalCountsAndClasses.get(libName).get(1);
			try (PreparedStatement pstmt = conn.prepareStatement(SQL1, Statement.RETURN_GENERATED_KEYS)) {
				pstmt.setString(1, libName);
				pstmt.setInt(2, totalCount);
				pstmt.setString(3, classes);

				pstmt.executeUpdate();
			} catch (SQLIntegrityConstraintViolationException e) {
				System.out.println(e);
			} catch (SQLException ex) {
				System.out.println(ex);
			}
		}
	}
	
	public Map<String, List<String>> getLibsToClasses() {
		Map<String, List<String>> libsToClasses = new HashMap<String, List<String>>();
		String SQL1 = "select library_name,classes from libs_info;";
		try (PreparedStatement pstmt = conn.prepareStatement(SQL1)) {
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				libsToClasses.put(rs.getString(1), Arrays.asList(rs.getString(2).split(":")));
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return libsToClasses;
	}
	
	public void createSQLProcForFetchingCallsToALibrary() {
		String SQLProcedure = "CREATE\r\n"
				+ "OR\r\n"
				+ "replace FUNCTION fetchCallsToALibrary (callee_library varchar, client_lib varchar)\r\n"
				+ "returns TABLE\r\n"
				+ "(id int,\r\n"
				+ "caller_method_name varchar,\r\n"
				+ "callee_method_name varchar ) AS $$ BEGIN\r\n"
				+ "RETURN query\r\n"
				+ "select A.id,\r\n"
				+ "  (select method_name from all_methods D where D.id=caller_method_id) as caller_method_name,\r\n"
				+ "  (select method_name from all_methods E where E.id=callee_method_id) as callee_method_name\r\n"
				+ "  from caller_callee_count A\r\n"
				+ "  where\r\n"
				+ "  callee_method_id in (select B.id from all_methods B where library_name LIKE Concat('%',callee_library,'%'))\r\n"
				+ "  and\r\n"
				+ "  caller_method_id in (select C.id from all_methods C where library_name LIKE Concat('%',client_lib,'%'));\r\n"
				+ "END; $$\r\n"
				+ "language 'plpgsql';";
				try (PreparedStatement pstmt = conn.prepareStatement(SQLProcedure)) {
					pstmt.executeUpdate();
				} catch (SQLException ex) {
					System.out.println(ex);
				}
	}
	
	public float fetchCallsToALibrary(String library, String client) {
		String SQL = "SELECT * from  fetchCallsToALibrary(?, ?);";
		float count = 0;
		try (PreparedStatement pstmt = conn.prepareStatement(SQL)) {
			pstmt.setString(1, library);
			pstmt.setString(2, client);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getFloat(1);
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return count;
	}
	
	public void createSQLProcForJaccardSimilarity() {
		String SQLProcedure = "CREATE\r\n"
				+ "OR\r\n"
				+ "replace FUNCTION findJaccardSimilarity (library varchar, clienta varchar, clientb varchar)\r\n"
				+ "returns TABLE\r\n"
				+ "( count numeric ) AS $$ BEGIN\r\n"
				+ "RETURN query\r\n"
				+ "SELECT (\r\n"
				+ "         (\r\n"
				+ "                SELECT 1.0*count(*)\r\n"
				+ "                FROM   (\r\n"
				+ "                  SELECT * FROM caller_callee_count WHERE callee_method_id IN (\r\n"
				+ "                SELECT callee_method_id FROM caller_callee_count\r\n"
				+ "                WHERE  callee_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',library,'%'))\r\n"
				+ "                      AND    caller_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',clienta,'%')\r\n"
				+ "                       )\r\n"
				+ "                        INTERSECT\r\n"
				+ "                  SELECT callee_method_id from caller_callee_count\r\n"
				+ "                  WHERE  callee_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',library,'%'))\r\n"
				+ "                      AND    caller_method_id IN\r\n"
				+ "                       (SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',clientb,'%')\r\n"
				+ "                        )\r\n"
				+ "                              \r\n"
				+ "                       )) AS A)\r\n"
				+ "          /\r\n"
				+ "         (\r\n"
				+ "                SELECT 1.0*count(*)\r\n"
				+ "                FROM   caller_callee_count A\r\n"
				+ "                WHERE  callee_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',library,'%'))\r\n"
				+ "                AND caller_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',clienta,'%')\r\n"
				+ "                              UNION\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',clientb,'%')\r\n"
				+ "                              \r\n"
				+ "                              )))\r\n"
				+ "                              AS COUNT;\r\n"
				+ "END; $$\r\n"
				+ "language 'plpgsql';";
		try (PreparedStatement pstmt = conn.prepareStatement(SQLProcedure)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			System.out.println(ex);
		}
	}
	
	public float findJaccardSimilarity(String library, String clientA, String clientB) {
		String SQL = "SELECT * from  findJaccardSimilarity(?, ?, ?);";
		float count = 0;
		try (PreparedStatement pstmt = conn.prepareStatement(SQL)) {
			pstmt.setString(1, library);
			pstmt.setString(2, clientA);
			pstmt.setString(3, clientB);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getFloat(1);
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return count;
	}
	
	public void createSQLProcForAPIProportionCalled() {
		String SQLProcedure = "CREATE\r\n"
				+ "OR\r\n"
				+ "replace FUNCTION findAPIProportionCalled (library varchar, client varchar)\r\n"
				+ "returns TABLE\r\n"
				+ "( count numeric ) AS $$ BEGIN\r\n"
				+ "RETURN query\r\n"
				+ "SELECT (\r\n"
				+ "         (\r\n"
				+ "                SELECT 1.0*count(distinct callee_method_id) AS COUNT\r\n"
				+ "                FROM   caller_callee_count A\r\n"
				+ "                WHERE  callee_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',library,'%'))\r\n"
				+ "                AND caller_method_id IN\r\n"
				+ "                       (\r\n"
				+ "                              SELECT id\r\n"
				+ "                              FROM   all_methods\r\n"
				+ "                              WHERE  library_name LIKE Concat('%',client,'%')\r\n"
				+ "                        ))*1.0"
				+ "/ (SELECT total_count from libs_info WHERE  library_name LIKE Concat('%',library,'%'))"
				+ ");\r\n"
				+ "END; $$\r\n"
				+ "language 'plpgsql';";
		try (PreparedStatement pstmt = conn.prepareStatement(SQLProcedure)) {
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			System.out.println(ex);
		}
	}
	
	public float findAPIProportionCalled(String library, String client) {
		String SQL = "SELECT * from  findAPIProportionCalled(?, ?);";
		float count = 0;
		try (PreparedStatement pstmt = conn.prepareStatement(SQL)) {
			pstmt.setString(1, library);
			pstmt.setString(2, client);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getFloat(1);
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return count;
	}

	public long insertMethodIntoAllMethodsTable(String methodName, String libraryName) {
		String SQL = "INSERT INTO all_methods(method_name,library_name) " + "VALUES(?,?);";
		long id = 0;

		try (PreparedStatement pstmt = conn.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)) {
			pstmt.setString(1, methodName);
			pstmt.setString(2, libraryName);

			int affectedRows = pstmt.executeUpdate();
			if (affectedRows > 0) {
				try (ResultSet rs = pstmt.getGeneratedKeys()) {
					if (rs.next()) {
						id = rs.getLong(1);
					}
				} catch (SQLException ex) {
					System.out.println(ex);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			System.out.println(e);
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return id;
	}

	public long getMethodFromAllMethodsTable(String methodName, String libraryName) {
		String SQL = "SELECT id from all_methods where method_name = ? and library_name = ?;";
		long id = 0;

		try (PreparedStatement pstmt = conn.prepareStatement(SQL)) {
			pstmt.setString(1, methodName);
			pstmt.setString(2, libraryName);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				id = rs.getLong(1);
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return id;
	}

	public int getCountFromCallerCalleeCountTable(long callerMethodId, long calleeMethodId) {
		String SQL = "SELECT count from caller_callee_count where caller_method_id = ? and callee_method_id = ?;";
		int id = 0;

		try (PreparedStatement pstmt = conn.prepareStatement(SQL)) {

			pstmt.setLong(1, callerMethodId);
			pstmt.setLong(2, calleeMethodId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (SQLException ex) {
			System.out.println(ex);
		}
		return id;
	}

	public long updateCountInCallerCalleeCountTable(String callerMethod, String callerLibrary, String calleeMethod, String calleeLibrary,
			int count) {
		long callerId = getMethodFromAllMethodsTable(callerMethod, callerLibrary);
		if (callerId == 0) {
			callerId = insertMethodIntoAllMethodsTable(callerMethod, callerLibrary);
		}
		long calleeId = getMethodFromAllMethodsTable(calleeMethod, calleeLibrary);
		if (calleeId == 0) {
			calleeId = insertMethodIntoAllMethodsTable(calleeMethod, calleeLibrary);
		}

		int countFromDb = getCountFromCallerCalleeCountTable(callerId, calleeId);
		if (countFromDb == 0) {
			String SQL = "INSERT INTO caller_callee_count(caller_method_id,callee_method_id,count) "
					+ "VALUES(?,?,?);";

			long id = 0;
			try (PreparedStatement pstmt = conn.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)) {
				pstmt.setLong(1, callerId);
				pstmt.setLong(2, calleeId);
				pstmt.setInt(3, count);
				int affectedRows = pstmt.executeUpdate();
				if (affectedRows > 0) {
					try (ResultSet rs = pstmt.getGeneratedKeys()) {
						if (rs.next()) {
							id = rs.getLong(1);
						}
					} catch (SQLException ex) {
						System.out.println(ex);
					}
				}
			} catch (SQLIntegrityConstraintViolationException e) {
				System.out.println(e);
			} catch (SQLException ex) {
				System.out.println(ex);
			}
			return id;
		} else {
			String SQL = "UPDATE caller_callee_count SET count = ? where caller_method_id = ? and callee_method_id = ?;";
			long id = 0;
			try (PreparedStatement pstmt = conn.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS)) {
				pstmt.setInt(1, count + countFromDb);
				pstmt.setLong(2, callerId);
				pstmt.setLong(3, calleeId);
				int affectedRows = pstmt.executeUpdate();
				if (affectedRows > 0) {
					try (ResultSet rs = pstmt.getGeneratedKeys()) {
						if (rs.next()) {
							id = rs.getLong(1);
						}
					} catch (SQLException ex) {
						System.out.println(ex);
					}
				}
			} catch (SQLIntegrityConstraintViolationException e) {
				System.out.println(e);
			} catch (SQLException ex) {
				System.out.println(ex);
			}
			return id;
		}
	}
	
	/*public List<String> getListOfExcludedMethods() {
		List<String> excludeMethods = new ArrayList<String>();
		// get list of methods to be excluded
		JSONParser jsonParser = new JSONParser();
		int index = new File(".").getAbsolutePath().indexOf("inter-library-calls");
		if (index == -1)
			return excludeMethods;
		String filePath = new File(".").getAbsolutePath().substring(0, index)+File.separator
				+"inter-library-calls"+File.separator+"projects"+File.separator+"exclude-methods.json";
        try (FileReader reader = new FileReader(filePath))
        {
            Object obj = jsonParser.parse(reader);
            JSONArray excludeMethodsArr = (JSONArray) obj;
            Iterator<String> iterator = excludeMethodsArr.iterator();
            while (iterator.hasNext()) {
            	excludeMethods.add((String)iterator.next());
            }
        } catch (Exception e) {
			System.out.println("Error while reading file with methods to be excluded" + e.toString());		
		}				
		return excludeMethods;
	}*/
}
