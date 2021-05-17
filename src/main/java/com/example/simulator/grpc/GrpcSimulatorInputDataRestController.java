package com.example.simulator.grpc;

import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/input-data")
public class GrpcSimulatorInputDataRestController {

  private final GrpcSimulatorServer server;

  public GrpcSimulatorInputDataRestController(GrpcSimulatorServer server) {
    this.server = server;
  }

  @GetMapping
  Collection<GrpcSimulatorServer.GrpcSimulatorInputData> getAll() {
    return server.getInputRepository().getAll();
  }

  @PostMapping("/clear")
  void clear() {
    server.getInputRepository().clear();
  }

}
