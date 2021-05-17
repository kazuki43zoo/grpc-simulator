package com.example.simulator.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GrpcSimulatorServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(GrpcSimulatorServerApplication.class, args);
  }

  @Bean
  GrpcSimulatorServer grpcServer(GrpcSimulatorServerProperties properties) {
    GrpcSimulatorServer server = new GrpcSimulatorServer(properties.getPort(), properties.getScanPackages());
    server.setSaveInput(properties.isSaveInput());
    return server;
  }

  @Bean
  ManagedChannel grpcManagedChannel() {
    return ManagedChannelBuilder.forAddress("localhost", 50051)
        .usePlaintext()
        .build();
  }

}
