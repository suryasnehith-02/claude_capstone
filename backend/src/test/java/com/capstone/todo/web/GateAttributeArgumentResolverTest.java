package com.capstone.todo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

/** The resolver hands over the two request attributes of the gateway filter and touches nothing else. */
class GateAttributeArgumentResolverTest {

    @SuppressWarnings("unused")
    static class Handler {
        void method(RouteMatch route, RawBody body, String text, Object any, UUID id, byte[] bytes,
                HttpServletRequest request) {
        }
    }

    private final GateAttributeArgumentResolver resolver = new GateAttributeArgumentResolver();

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = Handler.class.getDeclaredMethod("method", RouteMatch.class, RawBody.class, String.class,
                Object.class, UUID.class, byte[].class, HttpServletRequest.class);
        return new MethodParameter(method, index);
    }

    /** A request that fails the test as soon as anything other than an attribute is read from it. */
    private static final class AttributesOnlyRequest extends MockHttpServletRequest {

        @Override
        public ServletInputStream getInputStream() {
            throw new AssertionError("the input stream must not be read");
        }

        @Override
        public BufferedReader getReader() {
            throw new AssertionError("the reader must not be read");
        }

        @Override
        public String getRequestURI() {
            throw new AssertionError("the path must not be read");
        }

        @Override
        public String getParameter(String name) {
            throw new AssertionError("no parameter may be read");
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            throw new AssertionError("no parameter may be read");
        }

        @Override
        public Enumeration<String> getParameterNames() {
            throw new AssertionError("no parameter may be read");
        }

        @Override
        public String getHeader(String name) {
            throw new AssertionError("no header may be read");
        }
    }

    @Test
    void itSupportsExactlyTheRouteMatchAndTheRawBody() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isTrue();
        for (int index = 2; index <= 6; index++) {
            assertThat(resolver.supportsParameter(parameter(index))).as("parameter " + index).isFalse();
        }
    }

    @Test
    void itReturnsTheObjectsTheGatewayRecordedWithoutReadingAnythingElse() throws Exception {
        AttributesOnlyRequest request = new AttributesOnlyRequest();
        RouteMatch route = new RouteMatch(ApiRoutes.TODO_ITEM, "%61bc");
        RawBody body = new RawBody(new byte[] {1, 2, 3}, true);
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, route);
        request.setAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE, body);

        Object resolvedRoute = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);
        Object resolvedBody = resolver.resolveArgument(parameter(1), null, new ServletWebRequest(request), null);

        assertThat(resolvedRoute).isSameAs(route);
        assertThat(resolvedBody).isSameAs(body);
    }

    @Test
    void theAttributeNamesAreTheClassNamesOfTheTwoTypes() {
        assertThat(ApiGatewayFilter.ROUTE_ATTRIBUTE).isEqualTo(RouteMatch.class.getName());
        assertThat(ApiGatewayFilter.RAW_BODY_ATTRIBUTE).isEqualTo(RawBody.class.getName());
    }

    @Test
    void aMissingRouteMatchIsAProgrammingErrorWithAMessageThatHoldsNoRequestData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/todos/SECRET-ID");
        request.setQueryString("q=SECRET-QUERY");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RouteMatch")
                .hasMessageNotContaining("SECRET");
    }

    @Test
    void aMissingRawBodyIsAProgrammingError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/todos");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(1), null, new ServletWebRequest(request), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RawBody");
    }

    @Test
    void anAttributeOfTheWrongTypeIsNotHandedOver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/todos");
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, "/todos");
        request.setAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE, new byte[0]);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(1), null, new ServletWebRequest(request), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
