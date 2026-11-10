package com.doc_manag;

import com.doc_manag.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // picks up S3Properties and UploadProperties
public class DocumentManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(DocumentManagementSystemApplication.class);
		app.addListeners(new DotEnvLoader());   // finds .env even if the IDE starts us from another folder
		app.run(args);
	}

}
