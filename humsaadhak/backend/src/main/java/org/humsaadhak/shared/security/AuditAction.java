package org.humsaadhak.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.humsaadhak.shared.model.AuditTarget;

/**
 * Marks a controller method as a user action to record in the audit trail. Every state-changing endpoint must carry it
 * (LayeredArchitectureTest), and so must sensitive reads such as document downloads and exports.
 *
 * <p>The action and its audit record are committed in one transaction; a refused or failed attempt is recorded on its
 * own after the rollback. Expressions are SpEL over the method's parameter names (#id, #body) and, once the method has
 * returned, #result. They are written by developers, never taken from requests.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    /** "category.verb", e.g. "purpose.update"; the category groups actions on the pages that show them. */
    String value();

    /** What kind of record the action is about. */
    AuditTarget target() default AuditTarget.NONE;

    /** SpEL for the record's id, e.g. "#id" or "#result.id()". Services may also set it with AuditContext.target. */
    String id() default "";

    /** SpEL for a short, non-secret description, e.g. "#body.comment()". Never passwords or tokens. */
    String detail() default "";

    /** False for sign-in, sign-out and read-only actions: these are recorded after the method instead of in its transaction. */
    boolean transactional() default true;
}
