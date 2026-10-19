package in.codebasket.cbwa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CbwaApplication {

    public static void main(String[] args) {
        // All persistence and computation is done in UTC; the UI converts to Asia/Kolkata.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CbwaApplication.class, args);
    }
}
