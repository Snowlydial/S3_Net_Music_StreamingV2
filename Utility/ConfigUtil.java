package Utility;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigUtil {
    public static Map<String, Object> getConfig(String filePath) {
        Map<String, Object> config = new HashMap<>();
        JsonFactory factory = new JsonFactory();
        
        try (JsonParser parser = factory.createParser(new File(filePath))) {
            if (parser.nextToken() == JsonToken.START_OBJECT) {
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();
                    parser.nextToken();
                    switch (fieldName) {
                        case "serverAddress":
                        case "musicFolder":
                        case "JDBC_URL":
                        case "USERNAME":
                        case "PASSWORD":
                            config.put(fieldName, parser.getText());
                            break;
                        case "port":
                            config.put(fieldName, parser.getIntValue());
                            break;
                        default:
                            System.out.println("Unknown field: " + fieldName);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading JSON configuration: " + e.getMessage());
        }
        
        return config;
    }
}


