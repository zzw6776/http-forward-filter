package io.github.zzw6776.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author ZZW
 */
@SpringBootApplication
public class DemoApplication8080 {

	public static void main(String[] args) {
		System.setProperty("spring.profiles.active", "8080");
		SpringApplication.run(DemoApplication8080.class, args);
	}

}
