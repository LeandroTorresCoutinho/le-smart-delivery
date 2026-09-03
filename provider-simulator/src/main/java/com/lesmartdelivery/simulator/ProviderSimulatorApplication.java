package com.lesmartdelivery.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Reusable deterministic delivery-provider simulator. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProviderSimulatorApplication {

  /**
   * Starts the provider simulator.
   *
   * @param args application arguments
   */
  static void main(String[] args) {
    SpringApplication.run(ProviderSimulatorApplication.class, args);
  }
}
