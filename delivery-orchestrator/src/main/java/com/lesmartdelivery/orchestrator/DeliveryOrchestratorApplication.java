package com.lesmartdelivery.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Public delivery-options orchestration service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DeliveryOrchestratorApplication {

  /**
   * Starts the delivery orchestrator.
   *
   * @param args application arguments
   */
  static void main(String[] args) {
    SpringApplication.run(DeliveryOrchestratorApplication.class, args);
  }
}
