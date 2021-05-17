package com.example.simulator.grpc;

import com.example.demo.proto.TransactionGrpc;
import com.example.demo.proto.TransactionReply;
import com.example.demo.proto.TransactionRequest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@SpringBootTest
class GrpcSimulatorApplicationTests {

  @Autowired
  ManagedChannel managedChannel;


  @Test
  void contextLoads() throws InterruptedException {
    TransactionGrpc.TransactionBlockingStub blockingStub = TransactionGrpc.newBlockingStub(managedChannel);
    new Thread(() ->
    {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("kazuki")
//        .setVendor("JCB")
          .setAmount(100)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();
    new Thread(() ->
    {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("hanako")
        .setVendor("")
          .setAmount(200)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();

    new Thread(() -> {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("foo")
          .setVendor("7pay")
          .setAmount(300)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();

    new Thread(() -> {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("bar")
          .setVendor("line pay")
          .setAmount(400)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();

    new Thread(() -> {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("hoge")
          .setVendor("demo pay")
          .setAmount(500)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();

    new Thread(() -> {
      TransactionRequest request = TransactionRequest.newBuilder()
          .setName("fuga")
          .setVendor("rakuten pay")
          .setAmount(600)
          .build();
      TransactionReply reply = blockingStub.create(request);
      System.out.println(reply.getId());
    }).start();

    Thread.sleep(1000);
  }

  @Test
  void a() throws IOException {
    DocumentContext json = JsonPath.parse(new ClassPathResource("simulator-data.json").getInputStream());
    System.out.println(json);

  }

}
