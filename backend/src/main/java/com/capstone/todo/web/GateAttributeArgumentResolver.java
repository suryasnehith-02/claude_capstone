package com.capstone.todo.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;

/**
 * Hands the controller the two objects the gateway filter recorded as request attributes: the {@link RouteMatch}
 * (with the raw, undecoded id segment) and the {@link RawBody} (the bytes the body gate has already read and capped).
 * It never reads the request stream and never looks at the path or at a parameter, so Spring's own path-variable
 * decoding and message converters play no part (architecture D-13, section 6).
 *
 * <p>A missing attribute is a programming error (the filter always sets them before the chain runs), so it fails
 * with an {@link IllegalStateException} that carries no request data and becomes a 500 in the exception handler.
 */
public final class GateAttributeArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return type == RouteMatch.class || type == RawBody.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Class<?> type = parameter.getParameterType();
        String attribute = type == RouteMatch.class ? ApiGatewayFilter.ROUTE_ATTRIBUTE : ApiGatewayFilter.RAW_BODY_ATTRIBUTE;
        Object value = webRequest.getAttribute(attribute, RequestAttributes.SCOPE_REQUEST);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("The gateway filter did not record a " + type.getSimpleName()
                    + " for this request");
        }
        return value;
    }
}
