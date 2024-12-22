package Utility;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class ConnectJDBC {
    
    //private static String JDBC_URL = "jdbc:oracle:thin:@localhost:1521/orcl";
    private static final Map<String, Object> CONFIG = ConfigUtil.getConfig("config.json");
    private static final String JDBC_URL = (String) CONFIG.get("JDBC_URL");
    private static final String USERNAME = (String) CONFIG.get("USERNAME");
    private static final String PASSWORD = (String) CONFIG.get("PASSWORD");

    public static Connection startConnection() {
        Connection connection = null;
        
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
            System.out.println("Connection successful");
            return connection;
                        
        } catch (ClassNotFoundException e) {
            System.err.println("Oracle JDBC Driver not found. Include it in your library path.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed!");
            e.printStackTrace();
        }
        return connection;
    }
    
    //--- VERIFICATION
//    public static void main(String[] args) {
//        System.out.println("Current CLASSPATH: " + System.getProperty("java.class.path"));
//
//        ConnectJDBC connector = new ConnectJDBC();
//        connector.startConnection(); // Start the connection
//    }
}
