package com.cresensolutions.userservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;


@RequiredArgsConstructor
public abstract class BaseExceptionHandler {

    protected final MessageSource messageSource;

    protected String translate(String message) {
        if (message == null) return "Unknown error";
        try {
            return messageSource.getMessage(message, null, message, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return message;
        }
    }
}
