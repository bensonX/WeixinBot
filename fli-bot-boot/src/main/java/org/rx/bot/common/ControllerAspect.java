package org.rx.bot.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.rx.core.NQuery;
import org.rx.core.common.InfoLogException;
import org.rx.core.dto.common.RestResult;
import org.rx.util.ControllerInterceptor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
@Order(0)
@ControllerAdvice
public class ControllerAspect extends ControllerInterceptor {
    @Around("execution(public * org.rx.bot.web..*.*(..))")
    @Override
    public Object onAround(ProceedingJoinPoint joinPoint) throws Throwable {
        return super.onAround(joinPoint);
    }

    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler({Exception.class})
    @Override
    public ResponseEntity handleException(Exception e, HttpServletRequest request) {
        return super.handleException(e, request);
    }

    @Override
    protected NQuery<Class> handleInfoLogExceptions() {
        return NQuery.of(InfoLogException.class);
    }

    @Override
    protected Object handleExceptionResponse(String msg, String debugMsg) {
        return RestResult.fail(String.valueOf(super.handleExceptionResponse(msg, debugMsg)));
    }
}
