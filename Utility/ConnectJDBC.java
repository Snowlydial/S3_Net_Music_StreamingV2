package Utility;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectJDBC {
    
//    static String JDBC_URL = "jdbc:oracle:thin:@localhost:1521/orcl";
    static String JDBC_URL = "jdbc:oracle:thin:@localhost:1521/xe";
    static String USERNAME = "snowlyS3";
    static String PASSWORD = "snow";
    

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
