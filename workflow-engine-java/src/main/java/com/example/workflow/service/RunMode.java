package com.example.workflow.service;

public enum RunMode {
    SUCCESS,
    FAILURE,
    RETRY_THEN_SUCCESS;

    public static RunMode fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return SUCCESS;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("mode")) {
                return switch (pair[1]) {
                    case "failure" -> FAILURE;
                    case "retry" -> RETRY_THEN_SUCCESS;
                    default -> SUCCESS;
                };
            }
        }
        return SUCCESS;
    }
}
