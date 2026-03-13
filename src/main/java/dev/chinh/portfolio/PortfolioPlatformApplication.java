package dev.chinh.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;

@SpringBootApplication(exclude = {
    OAuth2ClientAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
public class PortfolioPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(PortfolioPlatformApplication.class, args);
	}

}
