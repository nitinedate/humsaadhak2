package org.humsaadhak.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.humsaadhak.audit.model.Outcome;
import org.humsaadhak.audit.util.AuditActions;
import org.humsaadhak.shared.security.AuditAction;
import org.humsaadhak.shared.security.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Starts the audit context of each audited request (request id, client address, browser) and records requests that
 * never reached the method, such as invalid input. The request id is returned in X-Request-Id for support.
 */
@Component
public class AuditRequestInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditRequestInterceptor.class);
    private final AuditRecorder recorder;

    public AuditRequestInterceptor(AuditRecorder recorder) { this.recorder = recorder; }

    static AuditAction actionOf(Object handler) {
        return handler instanceof HandlerMethod hm ? hm.getMethodAnnotation(AuditAction.class) : null;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (actionOf(handler) == null) return true;
        String id = UUID.randomUUID().toString();
        AuditContext.begin(id, req.getRemoteAddr(), req.getHeader("User-Agent"));
        res.setHeader("X-Request-Id", id);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        try {
            AuditAction a = actionOf(handler);
            AuditContext ctx = AuditContext.current().orElse(null);
            if (a == null || ctx == null || ctx.recorded()) return;
            Outcome outcome = AuditActions.outcomeForStatus(res.getStatus());
            if (outcome == Outcome.SUCCESS) return;
            String code = res.getStatus() == 400 ? "invalid-input" : "http-" + res.getStatus();
            recorder.record(ctx, a.value(), a.target(), null, AuditRecorder.currentActor(), outcome, code, null);
        } catch (RuntimeException e) {
            log.error("Could not record a rejected request: {}", e.toString());
        } finally {
            AuditContext.end();
        }
    }
}
