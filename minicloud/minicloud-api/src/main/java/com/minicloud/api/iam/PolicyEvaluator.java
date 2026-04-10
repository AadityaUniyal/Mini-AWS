package com.minicloud.api.iam;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Slf4j
@Component
public class PolicyEvaluator {

    /**
     * Evaluates whether the given policies allow the specified action on the resource.
     * AWS Evaluation Logic:
     * 1. Default DENY.
     * 2. Any explicit DENY wins.
     * 3. An explicit ALLOW is required to permit the action.
     */
    public boolean isAuthorized(Collection<Policy> policies, String action, String resource) {
        if (policies == null || policies.isEmpty()) {
            return false;
        }

        boolean allowed = false;

        for (Policy policy : policies) {
            for (PolicyStatement statement : policy.getStatements()) {
                if (match(statement.getAction(), action) && match(statement.getResource(), resource)) {
                    if (statement.getEffect() == PolicyStatement.Effect.DENY) {
                        log.debug("Explicit DENY found for action '{}' on resource '{}' in policy '{}'", action, resource, policy.getName());
                        return false; // Explicit DENY always wins
                    }
                    if (statement.getEffect() == PolicyStatement.Effect.ALLOW) {
                        allowed = true;
                    }
                }
            }
        }

        return allowed;
    }

    /**
     * Simple wildcard matching for actions and resources.
     * e.g., "s3:*" matches "s3:ListBuckets"
     * "*" matches everything.
     */
    private boolean match(String pattern, String target) {
        if (pattern.equals("*")) return true;
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return target.startsWith(prefix);
        }
        return pattern.equals(target);
    }
}
