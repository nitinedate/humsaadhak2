package org.humsaadhak.audit.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.humsaadhak.audit.dto.Actor;
import org.humsaadhak.audit.model.Outcome;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditAction;
import org.humsaadhak.shared.security.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Around every @AuditAction controller method: runs the action and writes its audit record in one transaction, so a
 * change is never committed without its record. If the action is refused or fails, the transaction rolls back and the
 * attempt is recorded on its own. Runs outside method security so refusals by @PreAuthorize are recorded too.
 */
public class AuditMethodInterceptor implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditMethodInterceptor.class);
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();
    private final ObjectProvider<AuditRecorder> recorder;
    private final ObjectProvider<PlatformTransactionManager> transactions;

    public AuditMethodInterceptor(ObjectProvider<AuditRecorder> recorder, ObjectProvider<PlatformTransactionManager> transactions) {
        this.recorder = recorder; this.transactions = transactions;
    }

    /** Carries a checked exception out of the transaction callback. */
    private static final class Wrapped extends RuntimeException {
        Wrapped(Throwable cause) { super(cause); }
    }

    @Override
    public Object invoke(MethodInvocation inv) throws Throwable {
        AuditAction a = AnnotatedElementUtils.findMergedAnnotation(inv.getMethod(), AuditAction.class);
        if (a == null) return inv.proceed();
        boolean ownContext = AuditContext.current().isEmpty();
        AuditContext ctx = ownContext ? AuditContext.begin(UUID.randomUUID().toString(), null, null) : AuditContext.current().get();
        Actor before = AuditRecorder.currentActor();
        try {
            Object result;
            if (a.transactional()) {
                result = new TransactionTemplate(transactions.getObject()).execute(status -> {
                    Object r;
                    try { r = inv.proceed(); } catch (Throwable t) { throw new Wrapped(t); }
                    success(a, inv, r, ctx, before);
                    return r;
                });
            } else {
                result = inv.proceed();
                success(a, inv, result, ctx, before);
            }
            ctx.markRecorded();
            return result;
        } catch (Throwable thrown) {
            Throwable cause = thrown instanceof Wrapped w ? w.getCause() : thrown;
            if (!ctx.recorded()) failure(a, inv, ctx, before, cause);
            throw cause;
        } finally {
            if (ownContext) AuditContext.end();
        }
    }

    private void success(AuditAction a, MethodInvocation inv, Object result, AuditContext ctx, Actor before) {
        Actor after = AuditRecorder.currentActor();   // set by sign-in; cleared by sign-out
        Actor actor = after != null ? after : before;
        recorder.getObject().record(ctx, a.value(), target(a, ctx), targetId(a, inv, result, ctx), actor, Outcome.SUCCESS, null,
                detail(a, inv, result, ctx, null));
    }

    private void failure(AuditAction a, MethodInvocation inv, AuditContext ctx, Actor actor, Throwable cause) {
        Outcome outcome = cause instanceof AccessDeniedException ? Outcome.DENIED : Outcome.FAILED;
        String code;                                  // Java 17: instanceof patterns (pattern-matching switch is Java 21)
        if (cause instanceof AccessDeniedException) code = "denied";
        else if (cause instanceof DomainException d) code = d.code();
        else if (cause instanceof NotFoundException) code = "not-found";
        else if (cause instanceof OptimisticLockingFailureException) code = "conflict";
        else code = "error";
        String message = cause instanceof DomainException ? cause.getMessage() : null;
        try {
            recorder.getObject().record(ctx, a.value(), target(a, ctx), targetId(a, inv, null, ctx), actor, outcome, code,
                    detail(a, inv, null, ctx, message));
            ctx.markRecorded();
        } catch (RuntimeException e) {
            log.error("Could not record failed action {}: {}", a.value(), e.toString());
        }
    }

    private static AuditTarget target(AuditAction a, AuditContext ctx) { return ctx.target() != null ? ctx.target() : a.target(); }

    private String targetId(AuditAction a, MethodInvocation inv, Object result, AuditContext ctx) {
        if (ctx.targetId() != null) return ctx.targetId();
        return a.id().isBlank() ? null : evaluate(a.id(), inv, result);
    }

    private String detail(AuditAction a, MethodInvocation inv, Object result, AuditContext ctx, String extra) {
        List<String> parts = new ArrayList<>();
        if (!a.detail().isBlank()) { String d = evaluate(a.detail(), inv, result); if (d != null && !d.isBlank()) parts.add(d); }
        parts.addAll(ctx.notes());
        if (extra != null) parts.add(extra);
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** Developer-written SpEL over the method's parameters and #result; anything it cannot evaluate is left out. */
    String evaluate(String expression, MethodInvocation inv, Object result) {
        try {
            Method m = inv.getMethod();
            MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(null, m, inv.getArguments(), NAMES);
            ctx.setVariable("result", result);
            Object v = expressions.computeIfAbsent(expression, PARSER::parseExpression).getValue(ctx);
            return v == null ? null : String.valueOf(v);
        } catch (RuntimeException e) {
            log.debug("Audit expression {} not evaluated: {}", expression, e.toString());
            return null;
        }
    }
}
