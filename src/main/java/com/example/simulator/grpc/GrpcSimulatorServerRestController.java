package com.example.simulator.grpc;

import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/server")
public class GrpcSimulatorServerRestController {

  private final GrpcSimulatorServer server;

  public GrpcSimulatorServerRestController(GrpcSimulatorServer server) {
    this.server = server;
  }

  @PostMapping("/start")
  void start() {
    server.start();
  }

  @PostMapping("/stop")
  void stop() {
    server.stop();
  }

  @GetMapping("/status")
  Map<String, Object> getStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("running", server.isRunning());
    status.put("port", server.getPort());
    status.put("settings", getStatus());
    return status;
  }

  @GetMapping("/settings")
  Map<String, Object> getSettings() {
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("save-input", server.isSaveInput());
    return settings;
  }

  @PutMapping("/settings")
  void putSettings(Map<String, Object> settings) {
    if (settings.containsKey("save-input")) {
      server.setSaveInput((boolean) settings.get("save-input"));
    }
  }

}
