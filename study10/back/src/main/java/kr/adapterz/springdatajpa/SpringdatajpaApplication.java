package kr.adapterz.springdatajpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringdatajpaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringdatajpaApplication.class, args);
	}

}
