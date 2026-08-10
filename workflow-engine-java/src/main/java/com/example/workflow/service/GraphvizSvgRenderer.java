package com.example.workflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class GraphvizSvgRenderer {
    public String renderSvg(String dot) {
        ProcessBuilder processBuilder = new ProcessBuilder("dot", "-Tsvg");
        try {
            Process process = processBuilder.start();
            process.getOutputStream().write(dot.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            if (!exited || process.exitValue() != 0) {
                return fallbackSvg("Graphviz render failed");
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return fallbackSvg("Graphviz dot command not found");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallbackSvg("Graphviz render interrupted");
        }
    }

    private String fallbackSvg(String message) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="680" height="160" viewBox="0 0 680 160">
                  <rect width="680" height="160" fill="#ffffff" stroke="#d8dee7"/>
                  <text x="24" y="72" font-family="Arial" font-size="18" fill="#17202a">""" + escapeXml(message) + """
                </text>
                  <text x="24" y="104" font-family="Arial" font-size="13" fill="#526170">Use /graph.dot or install Graphviz to render SVG.</text>
                </svg>
                """;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
