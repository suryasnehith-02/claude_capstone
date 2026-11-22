package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.web.ApiRoutes;
import com.capstone.todo.web.FallbackErrorController;
import com.capstone.todo.web.TodoController;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.gate.RouteTable;

/**
 * T-RT (a): the {@link RouteTable} equals the API handler mappings of the real application context. The comparison
 * covers the templates and, per template, the method sets, in both directions; the {@code /error} mapping of the
 * {@link FallbackErrorController} is not an API route and is excluded, but the test also pins that it is the only other
 * mapping and that no mapping carries a media-type, parameter or header condition (those would give Spring a say in
 * 404, 405 and 415).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RouteTableMappingTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(RouteTableMappingTest.class);
    private static final List<String> STANDARD_METHODS =
            List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @Autowired
    private ApplicationContext context;

    private Map<RequestMappingInfo, HandlerMethod> handlerMethods() {
        return context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class).getHandlerMethods();
    }

    private Map<RequestMappingInfo, HandlerMethod> apiHandlerMethods() {
        Map<RequestMappingInfo, HandlerMethod> api = new java.util.LinkedHashMap<>();
        handlerMethods().forEach((info, method) -> {
            if (method.getBeanType() != FallbackErrorController.class) {
                api.put(info, method);
            }
        });
        return api;
    }

    private static String describe(RequestMappingInfo info) {
        return info.getPathPatternsCondition().getPatternValues() + " " + info.getMethodsCondition().getMethods();
    }

    @Test
    void theApiMappingsAreTheControllersSixOperationsAndTheErrorControllerIsTheOnlyOtherMapping() {
        Set<Class<?>> beanTypes = new java.util.HashSet<>();
        handlerMethods().values().forEach(method -> beanTypes.add(method.getBeanType()));

        assertThat(beanTypes).containsExactlyInAnyOrder(TodoController.class, FallbackErrorController.class);
        assertThat(apiHandlerMethods()).hasSize(6);
        assertThat(handlerMethods().size() - apiHandlerMethods().size()).isEqualTo(1);
    }

    @Test
    void theRouteTableAndTheHandlerMappingsListTheSameTemplatesWithTheSameMethods() {
        Map<String, Set<String>> mapped = new TreeMap<>();
        apiHandlerMethods().forEach((info, method) -> {
            assertThat(info.getPathPatternsCondition().getPatternValues()).as(describe(info)).hasSize(1);
            String template = info.getPathPatternsCondition().getPatternValues().iterator().next();
            Set<String> methods = mapped.computeIfAbsent(template, key -> new TreeSet<>());
            info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
        });
        Map<String, Set<String>> table = new TreeMap<>();
        new RouteTable().allowByRoute().forEach((template, allow) ->
                table.put(template, new TreeSet<>(Arrays.asList(allow.split(", ")))));

        assertThat(mapped).isEqualTo(table);
        assertThat(mapped.keySet()).containsExactlyInAnyOrder(ApiRoutes.TODOS, ApiRoutes.TODO_ITEM, ApiRoutes.TODO_STATUS);
    }

    @Test
    void theRouteTableAcceptsExactlyTheMethodsEachTemplateIsMappedFor() {
        RouteTable table = new RouteTable();
        String id = UUID.randomUUID().toString();
        Map<String, String> uriByTemplate = Map.of(ApiRoutes.TODOS, "/todos", ApiRoutes.TODO_ITEM, "/todos/" + id,
                ApiRoutes.TODO_STATUS, "/todos/" + id + "/status");
        Map<String, Set<String>> mapped = new TreeMap<>();
        apiHandlerMethods().forEach((info, method) -> info.getPathPatternsCondition().getPatternValues().forEach(
                template -> info.getMethodsCondition().getMethods().forEach(
                        m -> mapped.computeIfAbsent(template, key -> new TreeSet<>()).add(m.name()))));

        uriByTemplate.forEach((template, uri) -> {
            Set<String> accepted = new TreeSet<>();
            for (String method : STANDARD_METHODS) {
                if (table.resolve(method, uri) instanceof RouteTable.Outcome.Matched matched) {
                    assertThat(matched.match().route()).isEqualTo(template);
                    accepted.add(method);
                }
            }
            assertThat(accepted).as(template).isEqualTo(mapped.get(template));
        });
    }

    @Test
    void noMappingAsksSpringToLookAtTheMediaTypeParametersHeadersOrAnyCustomCondition() {
        assertThat(handlerMethods()).isNotEmpty();
        handlerMethods().forEach((info, method) -> {
            String where = describe(info) + " -> " + method.getShortLogMessage();
            assertThat(info.getConsumesCondition().isEmpty()).as("consumes " + where).isTrue();
            assertThat(info.getProducesCondition().isEmpty()).as("produces " + where).isTrue();
            assertThat(info.getParamsCondition().isEmpty()).as("params " + where).isTrue();
            assertThat(info.getHeadersCondition().isEmpty()).as("headers " + where).isTrue();
            assertThat(info.getCustomCondition()).as("custom " + where).isNull();
        });
    }

    @Test
    void noControllerMethodDeclaresAPathVariableAndTheOnlyGateObjectsAreTheRouteMatchAndTheRawBody() {
        apiHandlerMethods().forEach((info, method) -> {
            for (Parameter parameter : method.getMethod().getParameters()) {
                String where = describe(info) + " parameter " + parameter.getName();
                assertThat(parameter.getAnnotation(PathVariable.class)).as(where).isNull();
                assertThat(parameter.getType()).as(where).isIn(RouteMatch.class, RawBody.class);
            }
        });
    }

    @Test
    void theErrorControllerReplacesBootsAndIsMappedAtTheErrorPathForEveryMethod_v18() {
        Map<String, ErrorController> errorControllers = context.getBeansOfType(ErrorController.class);
        List<String> patterns = new ArrayList<>();
        List<Integer> methodCounts = new ArrayList<>();
        handlerMethods().forEach((info, method) -> {
            if (method.getBeanType() == FallbackErrorController.class) {
                patterns.addAll(info.getPathPatternsCondition().getPatternValues());
                methodCounts.add(info.getMethodsCondition().getMethods().size());
            }
        });

        assertThat(errorControllers.values()).hasSize(1).first().isInstanceOf(FallbackErrorController.class);
        assertThat(errorControllers.keySet()).noneMatch(name -> name.toLowerCase().contains("basicerror"));
        assertThat(patterns).containsExactly(ApiRoutes.ERROR);
        assertThat(methodCounts).as("no method restriction on /error").containsExactly(0);
    }
}
