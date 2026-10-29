package me.ifmo.backend.security;

record AccessEndpoint(String method, String pattern, int minimumRank, String body, int allowedStatus) {

    String key() {
        return method + " " + pattern;
    }

    String path() {
        return pattern.replace("{id}", "99999999")
                .replace("{branchId}", "99999999")
                .replace("{code}", pattern.startsWith("/roles") ? "READER" : "MISSING")
                .replace("{roleCode}", "READER")
                .replace("{violationType}", "OTHER")
                .replace("{isbn}", "1234567890")
                .replace("{externalPayment}", "missing")
                .replace("{inventoryNumber}", "missing");
    }

    @Override
    public String toString() {
        return key();
    }
}
