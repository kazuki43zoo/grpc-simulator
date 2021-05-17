package com.example.simulator.grpc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@RestController
@RequestMapping("/api/reply-data")
public class GrpcSimulatorReplyDataRestController {

  private final GrpcSimulatorServer server;

  public GrpcSimulatorReplyDataRestController(GrpcSimulatorServer server) {
    this.server = server;
  }

  @GetMapping
  Map<String, Collection<GrpcSimulatorServer.GrpcSimulatorReplyData>> getAll() {
    return server.getReplyRepository().getAll();
  }

  @PostMapping("/add")
  void add(@RequestBody List<Map<String, Object>> settingsList) {
    settingsList.forEach(x -> server.getReplyRepository().add(new GrpcSimulatorServer.GrpcSimulatorReplyData(x)));
  }

  @PostMapping("/load")
  void load() {
    server.getReplyRepository().load();
  }

  @PostMapping("/reload")
  void reload() {
    server.getReplyRepository().reload();
  }

  @PostMapping("/clear")
  void clear() {
    server.getReplyRepository().clear();
  }

}
