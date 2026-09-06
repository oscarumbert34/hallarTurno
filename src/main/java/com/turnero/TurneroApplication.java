package com.turnero;

import java.util.TimeZone;
import com.turnero.booking.BookingReminderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(BookingReminderProperties.class)
@SpringBootApplication
public class TurneroApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TurneroApplication.class, args);
    }
}
