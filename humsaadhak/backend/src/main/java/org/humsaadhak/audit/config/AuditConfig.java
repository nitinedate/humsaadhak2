package org.humsaadhak.audit.config;

import org.humsaadhak.audit.service.AuditMethodInterceptor;
import org.humsaadhak.audit.service.AuditRecorder;
import org.humsaadhak.audit.service.AuditRequestInterceptor;
import org.humsaadhak.shared.security.AuditAction;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the audit trail: the advisor around @AuditAction methods and the request interceptor for /api. */
@Configuration
public class AuditConfig implements WebMvcConfigurer {
    private final AuditRequestInterceptor requests;

    public AuditConfig(AuditRequestInterceptor requests) { this.requests = requests; }

    /**
     * Ordered before method security (@PreAuthorize) so refused calls are recorded, and before the services' own
     * transactions so they join the transaction the audit record is written in.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor auditAdvisor(ObjectProvider<AuditRecorder> recorder, ObjectProvider<PlatformTransactionManager> transactions) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(AnnotationMatchingPointcut.forMethodAnnotation(AuditAction.class),
                new AuditMethodInterceptor(recorder, transactions));
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return advisor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(requests).addPathPatterns("/api/**"); }
}
