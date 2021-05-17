package com.example.simulator.grpc;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class ApplicationTests {

  @Test
  void a() throws IOException {
    DocumentContext json = JsonPath.parse(new ClassPathResource("simulator-data.json").getInputStream());
    Object payload = json.read("$.responses[0].payload");
    System.out.println(payload.getClass());
    System.out.println(JsonPath.parse(payload).jsonString());
    System.out.println((Object)json.read("$.responses[0].payload"));
    System.out.println((Object)json.read("method"));

  }

  @Test
  void data() throws IOException {
    DocumentContext json = JsonPath.parse(new ClassPathResource("data.json").getInputStream());
    Object payload = json.json();
    {
      StandardEvaluationContext context = new StandardEvaluationContext();
      context.setVariable("payload", payload);
      Expression expression = new SpelExpressionParser().parseExpression("#payload[data][f1] == 'aaa'");
      System.out.println(expression.getValue(context));
    }

    String templateJson = StreamUtils.copyToString(new ClassPathResource("res.json").getInputStream(), StandardCharsets.UTF_8);
    {
      StandardEvaluationContext context = new StandardEvaluationContext();
      context.setVariable("payload", payload);
      Expression expression = new SpelExpressionParser().parseExpression(templateJson);
      System.out.println(expression.getValue(context));
    }



//    System.out.println(JsonPath.parse(payload).jsonString());
//    System.out.println((Object)json.read("$.responses[0].payload"));
//    System.out.println((Object)json.read("method"));

  }
}
