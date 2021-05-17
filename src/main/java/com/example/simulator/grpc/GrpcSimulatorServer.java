package com.example.simulator.grpc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GrpcSimulatorServer implements SmartLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(GrpcSimulatorServer.class);
  private static final JsonFormat.Parser jsonParser = JsonFormat.parser();
  private static final JsonFormat.Printer jsonPrinter = JsonFormat.printer().includingDefaultValueFields();

  public static final int DEFAULT_PORT = 50051;

  private final int port;
  private final BindableService[] bindableServices;
  private final Map<Class<?>, Supplier<? extends Message.Builder>> messageBuilderSuppliers;
  private final GrpcSimulatorReplyDataRepository replyRepository = new GrpcSimulatorReplyDataRepository();
  private final GrpcSimulatorInputDataRepository inputRepository = new GrpcSimulatorInputDataRepository();
  private boolean saveInput;
  private Server server;

  public GrpcSimulatorServer(int port, String... scanPackages) {
    this.port = port;
    GrpcScanner scanner = new GrpcScanner();
    this.bindableServices = scanner.scanBindableServices(scanPackages);
    this.messageBuilderSuppliers = scanner.scanMessageBuilderSuppliers(scanPackages);
  }

  public GrpcSimulatorServer(String... scanPackages) {
    this(DEFAULT_PORT, scanPackages);
  }

  public void start() {
    ServerBuilder<?> builder = ServerBuilder.forPort(port);
    Stream.of(bindableServices).forEach(builder::addService);
    try {
      server = builder.build().start();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to start the gRPC Simulator Server.", e);
    }
    replyRepository.load();
    logger.info("gRPC Simulator Server started, listening on " + getPort());
  }

  public void stop() {
    if (server != null) {
      int port = getPort();
      try {
        server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        logger.error("Interrupted during stop.", e);
      }
      logger.info("gRPC Simulator Server stopped, listening on " + port);
      server = null;
      replyRepository.clear();
    }
  }

  @Override
  public boolean isRunning() {
    return server != null;
  }

  public int getPort() {
    return server == null ? -1 : server.getPort();
  }

  public void setSaveInput(boolean saveInput) {
    this.saveInput = saveInput;
  }

  public boolean isSaveInput() {
    return saveInput;
  }

  public GrpcSimulatorReplyDataRepository getReplyRepository() {
    return replyRepository;
  }

  public GrpcSimulatorInputDataRepository getInputRepository() {
    return inputRepository;
  }

  private class GrpcScanner {

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private final MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

    private BindableService[] scanBindableServices(String... packagePatterns) {
      try {
        return scanBindableServiceClasses(packagePatterns).stream()
            .map(this::newBindableService).toArray(BindableService[]::new);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to scan 'io.grpc.BindableService' classes.", e);
      }
    }

    private Map<Class<?>, Supplier<? extends Message.Builder>> scanMessageBuilderSuppliers(String... packagePatterns) {
      try {
        return scanMessageClasses(packagePatterns).stream()
            .collect(Collectors.toMap(x -> x, this::newMessageBuilderSupplier));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to scan 'com.google.protobuf.Message' classes.", e);
      }
    }

    private BindableService newBindableService(Class<?> target) {
      logger.debug("Generate a grpc stub service for '{}'.", target);
      GrpcBindableServiceMethodInterceptor interceptor = new GrpcBindableServiceMethodInterceptor();
      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.addAdvice(interceptor);
      proxyFactory.setTargetClass(target);
      proxyFactory.setProxyTargetClass(true);
      BindableService service = (BindableService) proxyFactory.getProxy();
      interceptor.setServerServiceDefinition(service.bindService());
      return service;
    }

    private Supplier<? extends Message.Builder> newMessageBuilderSupplier(Class<?> target) {
      logger.debug("Generate a grpc message builder supplier for '{}'.", target);
      return () -> {
        try {
          return (Message.Builder) target.getMethod("newBuilder").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
          throw new IllegalStateException("Failed to create a supplier for providing a message builder.", e);
        }
      };
    }

    private Set<Class<?>> scanBindableServiceClasses(String... packagePatterns) throws IOException {
      return scanClasses(BindableService.class, packagePatterns);
    }

    private Set<Class<?>> scanMessageClasses(String... packagePatterns) throws IOException {
      return scanClasses(Message.class, packagePatterns);
    }

    private Set<Class<?>> scanClasses(Class<?> type, String... packagePatterns) throws IOException {
      Set<Class<?>> classes = new HashSet<>();
      for (String packagePattern : packagePatterns) {
        Resource[] resources = resourcePatternResolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + ClassUtils.convertClassNameToResourcePath(packagePattern) + "/**/*.class");
        for (Resource resource : resources) {
          try {
            ClassMetadata classMetadata = metadataReaderFactory.getMetadataReader(resource).getClassMetadata();
            Class<?> clazz = ClassUtils.forName(classMetadata.getClassName(), null);
            if (type.isAssignableFrom(clazz)) {
              classes.add(clazz);
            }
          } catch (Throwable e) {
            // ignore
          }
        }
      }
      return classes;
    }
  }

  private class GrpcBindableServiceMethodInterceptor implements MethodInterceptor {
    private final Map<String, Lock> methodLocks = new ConcurrentHashMap<>();
    private ServerServiceDefinition serviceDefinition;

    void setServerServiceDefinition(ServerServiceDefinition serviceDefinition) {
      this.serviceDefinition = serviceDefinition;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

      int lastArgumentIndex = invocation.getArguments().length - 1;
      if (!(invocation.getArguments().length > 1 &&
          invocation.getMethod().getParameterTypes()[lastArgumentIndex] == StreamObserver.class)) {
        return invocation.proceed();
      }

      String methodName = StringUtils.capitalize(invocation.getMethod().getName());
      String fullMethodName = serviceDefinition.getServiceDescriptor().getName() + "/" + methodName;

      Message requestMessage = (Message) invocation.getArguments()[0];
      @SuppressWarnings("unchecked")
      StreamObserver<Object> replyObserver = (StreamObserver<Object>) invocation.getArguments()[lastArgumentIndex];

      try {
        String requestMessageJson = jsonPrinter.print(requestMessage);
        logger.debug("Receive a request on '{}' with '{}'.", fullMethodName, requestMessageJson);

        if (saveInput) {
          inputRepository.add(new GrpcSimulatorInputData(fullMethodName, JsonPath.parse(requestMessageJson).json()));
        }

        Lock methodLock = methodLocks.computeIfAbsent(fullMethodName, x -> new ReentrantLock());
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("payload", requestMessage);
        GrpcSimulatorReplyData data;
        try {
          methodLock.lock();
          data = replyRepository.find(fullMethodName, conditions);
          Optional.ofNullable(data).ifPresent(x -> {
            x.increment();
            replyRepository.moveToLast(x);
          });
        } finally {
          methodLock.unlock();
        }

        if (data != null) {
          if (data.getDelay() > 0) {
            TimeUnit.MILLISECONDS.sleep(data.getDelay());
          }
          logger.debug("Matches a simulator data that filtered by '{}' on '{}'.", Optional.ofNullable(data.getMatcher()).orElse("*"), fullMethodName);
          if (data.isUsePayload()) {
            Class<?> replyType = (Class<?>) ((ParameterizedType) invocation.getMethod()
                .getGenericParameterTypes()[lastArgumentIndex]).getActualTypeArguments()[0];
            Message.Builder replyMessageBuilder = messageBuilderSuppliers.get(replyType).get();
            jsonParser.merge(data.getPayload(), replyMessageBuilder);
            Message replyMessage = replyMessageBuilder.build();
            replyObserver.onNext(replyMessage);
            if (data.isComplete()) {
              replyObserver.onCompleted();
            }
            logger.debug("Send a reply on '{}' with '{}'.", fullMethodName, jsonPrinter.print(replyMessage));
          } else {
            Throwable error = data.getThrowable();
            replyObserver.onError(error);
            logger.debug("Send a error on '{}'.", fullMethodName, error);
          }
        } else {
          RuntimeException error = Status.NOT_FOUND
              .withDescription("Not found a simulator data for '" + fullMethodName + "'.").asRuntimeException();
          replyObserver.onError(error);
          logger.warn("Not found a simulator data for '{}'.", fullMethodName);
        }
      } catch (Throwable cause) {
        if (cause instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        RuntimeException error = Status.INTERNAL
            .withDescription("Failed to simulate on '" + fullMethodName + "'.").asRuntimeException();
        replyObserver.onError(error);
        logger.error("Failed to simulate on '{}'.", fullMethodName, cause);
      }
      return null;
    }
  }

  static class GrpcSimulatorInputDataRepository {
    private final Queue<GrpcSimulatorInputData> dataList = new LinkedBlockingQueue<>();

    void add(GrpcSimulatorInputData data) {
      dataList.add(data);
    }

    void clear() {
      dataList.clear();
    }

    Collection<GrpcSimulatorInputData> getAll() {
      return Collections.unmodifiableCollection(dataList);
    }

  }

  static class GrpcSimulatorReplyDataRepository {

    private final Map<String, Queue<GrpcSimulatorReplyData>> dataListPerMethod = new ConcurrentHashMap<>();

    void add(GrpcSimulatorReplyData data) {
      dataListPerMethod.computeIfAbsent(data.getMethod(), x -> new LinkedBlockingQueue<>()).add(data);
    }

    void remove(GrpcSimulatorReplyData data) {
      dataListPerMethod.computeIfAbsent(data.getMethod(), x -> new LinkedBlockingQueue<>()).remove(data);
    }

    void moveToLast(GrpcSimulatorReplyData data) {
      remove(data);
      add(data);
    }

    void load() {
      try {
        DocumentContext json = JsonPath.parse(new ClassPathResource("simulator-data.json").getInputStream());
        List<Map<String, Object>> data = json.json();
        data.stream().map(GrpcSimulatorReplyData::new).forEach(this::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    void reload() {
      clear();
      load();
    }

    void clear() {
      dataListPerMethod.values().forEach(Queue::clear);
      dataListPerMethod.clear();
    }

    GrpcSimulatorReplyData find(String method, Map<String, Object> conditions) {
      Queue<GrpcSimulatorReplyData> methodDataList = dataListPerMethod.get(method);
      if (methodDataList != null) {
        return methodDataList.stream()
            .filter(x -> x.getMatcher() != null)
            .filter(x -> x.matches(conditions))
            .findFirst().orElseGet(() -> methodDataList.stream()
                .filter(x -> x.getMatcher() == null).findFirst().orElse(null));
      }
      return null;
    }

    Map<String, Collection<GrpcSimulatorReplyData>> getAll() {
      return Collections.unmodifiableMap(dataListPerMethod);
    }

  }

  public static class GrpcSimulatorInputData {
    private final String method;
    private final Map<String, Object> payload;

    GrpcSimulatorInputData(String method, Map<String, Object> payload) {
      this.method = method;
      this.payload = payload;
    }

    public String getMethod() {
      return method;
    }

    public Map<String, Object> getPayload() {
      return payload;
    }

  }

  public static class GrpcSimulatorReplyData {
    private final static ExpressionParser expressionParser = new SpelExpressionParser();

    private final AtomicLong counter = new AtomicLong(0);
    private final String id;
    private final String method;
    private final boolean active;
    private final String matcher;
    private final Expression matcherExpression;
    private final Map<String, Object> payload;
    private final Map<String, Object> errorSettings;
    private final String use;
    private final long delay;
    private final boolean complete;

    @SuppressWarnings("unchecked")
    GrpcSimulatorReplyData(Map<String, Object> settings) {
      this.id = UUID.randomUUID().toString();
      this.method = (String) settings.get("method");
      this.active = (boolean) settings.getOrDefault("active", true);
      this.matcher = (String) settings.get("matcher");
      this.matcherExpression = matcher != null ? expressionParser.parseExpression(matcher) : null;
      this.payload = (Map<String, Object>) settings.get("payload");
      this.errorSettings = (Map<String, Object>) settings.get("error");
      this.use = (String) settings.getOrDefault("use", (errorSettings != null) ? "error" : "payload");
      this.delay = (long) settings.getOrDefault("delay", 0L);
      this.complete = (boolean) settings.getOrDefault("complete", true);
    }

    boolean matches(Map<String, Object> conditions) {
      if (!active) {
        return false;
      }
      if (matcherExpression != null) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariables(conditions);
        Boolean result = matcherExpression.getValue(context, Boolean.class);
        return result != null && result;
      }
      return true;
    }

    public void increment() {
      counter.incrementAndGet();
    }

    public long getDelay() {
      return delay;
    }

    public boolean isUsePayload() {
      return "payload".equalsIgnoreCase(use);
    }

    @JsonIgnore
    public String getPayload() {
      return JsonPath.parse(payload).jsonString();
    }

    @JsonIgnore
    public Throwable getThrowable() {
      Status status = Status.fromCode(Status.Code.valueOf((String) errorSettings.getOrDefault("status", Status.Code.INTERNAL.name())));
      if (errorSettings.containsKey("description")) {
        status = status.withDescription((String) errorSettings.get("description"));
      }
      return (boolean) errorSettings.getOrDefault("runtime", true) ? status.asRuntimeException() : status.asException();
    }

    public String getId() {
      return id;
    }

    public String getMethod() {
      return method;
    }

    public String getMatcher() {
      return matcher;
    }

    public boolean isActive() {
      return active;
    }

    @JsonProperty("payload")
    public Map<String, Object> getPayloadJson() {
      return payload;
    }

    @JsonProperty("error")
    public Map<String, Object> getErrorSettings() {
      return errorSettings;
    }

    public long getCount() {
      return counter.get();
    }

    public boolean isComplete() {
      return complete;
    }

  }

}
