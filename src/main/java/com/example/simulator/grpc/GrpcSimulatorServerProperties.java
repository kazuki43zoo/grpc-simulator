package com.example.simulator.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.simulator-server")
public class GrpcSimulatorServerProperties {

  private int port = GrpcSimulatorServer.DEFAULT_PORT;

  private String[] scanPackages;

  private boolean saveInput;

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public void setScanPackages(String[] scanPackages) {
    this.scanPackages = scanPackages;
  }

  public String[] getScanPackages() {
    return scanPackages;
  }

  public void setSaveInput(boolean saveInput) {
    this.saveInput = saveInput;
  }

  public boolean isSaveInput() {
    return saveInput;
  }

}
