/*
 * v0.0.1 - Initial working version
 * v0.0.2 - Added x-request-id validation
 * v0.0.4 - Added x-request-id to span attributes
 * v0.1.0 - Added support to MongoDB change stream
 * 
 */

package com.example.spanlink_demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpanlinkDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpanlinkDemoApplication.class, args);
	}

}
