package com.cresensolutions.userservice.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;


@Slf4j
@Aspect
@Component
public class ExceptionAspect {

    @Pointcut("within(com.cresensolutions.userservice.service.Impl..*)" +
              " || within(com.cresensolutions.userservice.controller..*)")
    public void applicationLayer() {}

    @AfterThrowing(pointcut = "applicationLayer()", throwing = "ex")
    public void logException(JoinPoint jp, Throwable ex) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        String location = sig.getDeclaringType().getSimpleName() + "." + sig.getName() + "()";

        if (isBusinessException(ex)) {
            log.warn("[EXCEPTION] {} threw {}: {}", location, ex.getClass().getSimpleName(), ex.getMessage());
        } else {
            log.error("[EXCEPTION] {} threw unexpected {}: {}", location, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    private boolean isBusinessException(Throwable ex) {
        return ex instanceof IllegalArgumentException
                || ex instanceof IllegalStateException
                || ex instanceof com.cresensolutions.userservice.exception.ResourceNotFoundException
                || ex instanceof com.cresensolutions.userservice.exception.AuthenticationFailedException
                || ex instanceof com.cresensolutions.userservice.exception.InvalidOtpException
                || ex instanceof org.springframework.security.access.AccessDeniedException;
    }
}
