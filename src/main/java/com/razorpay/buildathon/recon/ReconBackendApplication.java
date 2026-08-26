package com.razorpay.buildathon.recon;

import com.razorpay.buildathon.recon.ai.config.ReconAiConfig;
import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ReconMatchingConfig.class, ReconAiConfig.class})
public class ReconBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReconBackendApplication.class, args);
    }
}

