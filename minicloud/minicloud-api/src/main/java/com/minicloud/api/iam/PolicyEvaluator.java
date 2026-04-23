package com.minicloud.api.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.domain.Policy;
import com.minicloud.api.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;


@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyEvaluator {

    private final ObjectMapper mapper;

    /**
     * Evaluates authorization for a user across all attached and inline policies.
     */
    public boolean isAuthorized(User user, String action, String resource) {
        if (Boolean.TRUE.equals(user.getRootUser())) return true; // Root has full access

        boolean allowed = false;

        // 1. Evaluate Inline Policy
        if (user.getInlinePolicy() != null) {
            AuthorizationDecision decision = evaluateJson(user.getInlinePolicy(), action, resource);
            if (decision == AuthorizationDecision.DENY) return false;
            if (decision == AuthorizationDecision.ALLOW) allowed = true;
        }

        // 2. Evaluate Attached Policies
        if (user.getPolicies() != null) {
            for (Policy policy : user.getPolicies()) {
                AuthorizationDecision decision = evaluateJson(policy.getDocument(), action, resource);
                if (decision == AuthorizationDecision.DENY) return false;
                if (decision == AuthorizationDecision.ALLOW) allowed = true;
            }
        }

        return allowed;
    }

    private AuthorizationDecision evaluateJson(String json, String action, String resource) {
        if (json == null || json.isEmpty()) return AuthorizationDecision.NEUTRAL;

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode statements = root.get("Statement");
            if (statements == null) return AuthorizationDecision.NEUTRAL;

            if (statements.isObject()) {
                return evaluateStatement(statements, action, resource);
            } else if (statements.isArray()) {
                AuthorizationDecision result = AuthorizationDecision.NEUTRAL;
                for (JsonNode stmt : statements) {
                    AuthorizationDecision decision = evaluateStatement(stmt, action, resource);
                    if (decision == AuthorizationDecision.DENY) return AuthorizationDecision.DENY;
                    if (decision == AuthorizationDecision.ALLOW) result = AuthorizationDecision.ALLOW;
                }
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to parse policy JSON: {}", e.getMessage());
        }
        return AuthorizationDecision.NEUTRAL;
    }

    private AuthorizationDecision evaluateStatement(JsonNode stmt, String action, String resource) {
        String effect = stmt.path("Effect").asText();
        JsonNode actions = stmt.get("Action");
        JsonNode resources = stmt.get("Resource");

        if (actions == null || resources == null) return AuthorizationDecision.NEUTRAL;

        boolean actionMatch = matchWildcard(actions, action);
        boolean resourceMatch = matchWildcard(resources, resource);

        if (actionMatch && resourceMatch) {
            return "Deny".equalsIgnoreCase(effect) ? AuthorizationDecision.DENY : AuthorizationDecision.ALLOW;
        }

        return AuthorizationDecision.NEUTRAL;
    }

    private boolean matchWildcard(JsonNode patternNode, String target) {
        if (patternNode.isTextual()) {
            return matches(patternNode.asText(), target);
        } else if (patternNode.isArray()) {
            for (JsonNode n : patternNode) {
                if (matches(n.asText(), target)) return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String target) {
        if (pattern.equals("*")) return true;
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return target.matches(regex);
        }
        return pattern.equalsIgnoreCase(target);
    }

    private enum AuthorizationDecision {
        ALLOW, DENY, NEUTRAL
    }
}
