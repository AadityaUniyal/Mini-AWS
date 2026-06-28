package com.minicloud.api.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.api.domain.Policy;
import com.minicloud.api.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

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
            AuthorizationDecision decision = evaluateJson(user.getInlinePolicy(), action, resource, user);
            if (decision == AuthorizationDecision.DENY) return false;
            if (decision == AuthorizationDecision.ALLOW) allowed = true;
        }

        // 2. Evaluate Attached Policies
        if (user.getPolicies() != null) {
            for (Policy policy : user.getPolicies()) {
                AuthorizationDecision decision = evaluateJson(policy.getDocument(), action, resource, user);
                if (decision == AuthorizationDecision.DENY) return false;
                if (decision == AuthorizationDecision.ALLOW) allowed = true;
            }
        }

        return allowed;
    }

    private AuthorizationDecision evaluateJson(String json, String action, String resource, User user) {
        if (json == null || json.isEmpty()) return AuthorizationDecision.NEUTRAL;

        try {
            // Resolve variables before parsing JSON
            String resolvedJson = json
                    .replace("${aws:username}", user.getUsername())
                    .replace("${aws:PrincipalAccount}", user.getAccountId());

            JsonNode root = mapper.readTree(resolvedJson);
            JsonNode statements = root.get("Statement");
            if (statements == null) return AuthorizationDecision.NEUTRAL;

            if (statements.isObject()) {
                return evaluateStatement(statements, action, resource, user);
            } else if (statements.isArray()) {
                AuthorizationDecision result = AuthorizationDecision.NEUTRAL;
                for (JsonNode stmt : statements) {
                    AuthorizationDecision decision = evaluateStatement(stmt, action, resource, user);
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

    private AuthorizationDecision evaluateStatement(JsonNode stmt, String action, String resource, User user) {
        String effect = stmt.path("Effect").asText();
        JsonNode actions = stmt.get("Action");
        JsonNode resources = stmt.get("Resource");

        if (actions == null || resources == null) return AuthorizationDecision.NEUTRAL;

        boolean actionMatch = matchWildcard(actions, action);
        boolean resourceMatch = matchWildcard(resources, resource);

        if (actionMatch && resourceMatch) {
            // Evaluate Condition block if present
            JsonNode conditionNode = stmt.get("Condition");
            if (conditionNode != null && !evaluateConditions(conditionNode, user)) {
                return AuthorizationDecision.NEUTRAL;
            }
            return "Deny".equalsIgnoreCase(effect) ? AuthorizationDecision.DENY : AuthorizationDecision.ALLOW;
        }

        return AuthorizationDecision.NEUTRAL;
    }

    private boolean evaluateConditions(JsonNode conditionNode, User user) {
        if (conditionNode == null || conditionNode.isNull()) return true;

        Iterator<Map.Entry<String, JsonNode>> fields = conditionNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> operatorEntry = fields.next();
            String operator = operatorEntry.getKey();
            JsonNode keyValuesNode = operatorEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> kvFields = keyValuesNode.fields();
            while (kvFields.hasNext()) {
                Map.Entry<String, JsonNode> kvEntry = kvFields.next();
                String contextKey = kvEntry.getKey();
                JsonNode expectedValues = kvEntry.getValue();

                String actualValue = getContextValue(contextKey, user);
                if (actualValue == null) return false;

                boolean match = false;
                if (expectedValues.isArray()) {
                    for (JsonNode val : expectedValues) {
                        if (matchConditionValue(operator, val.asText(), actualValue)) {
                            match = true;
                            break;
                        }
                    }
                } else {
                    if (matchConditionValue(operator, expectedValues.asText(), actualValue)) {
                        match = true;
                    }
                }
                if (!match) return false;
            }
        }
        return true;
    }

    private String getContextValue(String contextKey, User user) {
        if ("aws:username".equalsIgnoreCase(contextKey)) {
            return user.getUsername();
        }
        if ("aws:PrincipalAccount".equalsIgnoreCase(contextKey)) {
            return user.getAccountId();
        }
        if ("aws:SourceIp".equalsIgnoreCase(contextKey)) {
            return "127.0.0.1"; // Mocked source IP for simulation
        }
        return null;
    }

    private boolean matchConditionValue(String operator, String expected, String actual) {
        if ("StringEquals".equalsIgnoreCase(operator)) {
            return expected.equalsIgnoreCase(actual);
        }
        if ("StringLike".equalsIgnoreCase(operator)) {
            return matches(expected, actual);
        }
        if ("IpAddress".equalsIgnoreCase(operator)) {
            if (expected.contains("/")) {
                String expectedPrefix = expected.split("/")[0];
                return actual.startsWith(expectedPrefix);
            }
            return expected.equals(actual);
        }
        return false;
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
