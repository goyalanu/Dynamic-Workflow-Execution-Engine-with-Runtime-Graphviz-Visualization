package com.example.workflow.service;

import com.example.workflow.workflow.WorkflowConfigLoader;
import com.example.workflow.workflow.WorkflowDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class WorkflowHttpServer {
    private final HttpServer server;
    private final WorkflowRunService runService;
    private final GraphvizSvgRenderer svgRenderer;
    private final LiveDashboardPage dashboardPage;

    public WorkflowHttpServer(int port, WorkflowDefinition workflow) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.runService = new WorkflowRunService(workflow);
        this.svgRenderer = new GraphvizSvgRenderer();
        this.dashboardPage = new LiveDashboardPage();
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        runService.close();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            send(exchange, 404, "text/plain", exception.getMessage());
        } catch (Exception exception) {
            send(exchange, 500, "text/plain", "Internal error: " + exception.getMessage());
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equals("GET") && (path.equals("/") || path.equals("/dashboard"))) {
            send(exchange, 200, "text/html", dashboardPage.render());
            return;
        }
        if (method.equals("POST") && path.equals("/api/runs")) {
            RunMode mode = RunMode.fromQuery(exchange.getRequestURI().getRawQuery());
            String runId = runService.startRun(mode);
            send(exchange, 202, "application/json", "{\"runId\":\"" + runId + "\"}");
            return;
        }
        if (method.equals("GET") && path.equals("/api/runs")) {
            StringBuilder json = new StringBuilder("{\"runs\":[");
            for (int i = 0; i < runService.runIds().size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append("\"").append(runService.runIds().get(i)).append("\"");
            }
            json.append("]}");
            send(exchange, 200, "application/json", json.toString());
            return;
        }
        if (method.equals("GET") && path.startsWith("/api/runs/")) {
            String[] parts = path.split("/");
            if (parts.length != 5) {
                send(exchange, 404, "text/plain", "Unknown route");
                return;
            }
            String runId = parts[3];
            String resource = parts[4];
            if (resource.equals("state")) {
                send(exchange, 200, "application/json", runService.stateJson(runId));
                return;
            }
            if (resource.equals("graph.dot")) {
                send(exchange, 200, "text/vnd.graphviz", runService.dot(runId));
                return;
            }
            if (resource.equals("graph.svg")) {
                send(exchange, 200, "image/svg+xml", svgRenderer.renderSvg(runService.dot(runId)));
                return;
            }
        }
        send(exchange, 404, "text/plain", "Unknown route");
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        WorkflowDefinition workflow = WorkflowConfigLoader.loadFromClasspath("config/workflows.properties");
        WorkflowHttpServer server = new WorkflowHttpServer(port, workflow);
        server.start();
        System.out.println("Workflow service listening at http://localhost:" + port + "/dashboard");
    }
}
