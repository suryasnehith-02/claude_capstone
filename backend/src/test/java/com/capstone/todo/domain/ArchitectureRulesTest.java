package com.capstone.todo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.capstone.todo.archfixture.TransactionalOnMethod;
import com.capstone.todo.archfixture.TransactionalOnType;
import com.capstone.todo.archfixture.WebTypeInBodyOnly;
import com.capstone.todo.archfixture.WebTypeInSignature;
import com.capstone.todo.domain.support.IdGenerator;

/**
 * U-ARCH: the {@code domain} package has no {@code @Transactional} on any type, method or field, and imports nothing
 * from {@code web} or {@code persistence} (architecture 7.2, 10). It scans the compiled classes and proves the
 * scan can fail by running the same rules on fixture classes in test scope.
 */
class ArchitectureRulesTest {

    private static final List<String> FORBIDDEN_PACKAGES =
            List.of("com.capstone.todo.web.", "com.capstone.todo.persistence.");

    private static final Set<String> TRANSACTIONAL_ANNOTATIONS = Set.of(
            "org.springframework.transaction.annotation.Transactional",
            "jakarta.transaction.Transactional",
            "javax.transaction.Transactional");

    /** Text that must not appear in a domain class file's constant pool (this also catches use inside method bodies). */
    private static final List<String> FORBIDDEN_CONSTANT_POOL_TEXT = List.of(
            "com/capstone/todo/web/",
            "com/capstone/todo/persistence/",
            "transaction/annotation/Transactional",
            "jakarta/transaction/Transactional",
            "javax/transaction/Transactional");

    // ---- the rule under test ----

    @Test
    void domainHasNoTransactionalAndNoWebOrPersistenceDependency() throws Exception {
        List<Class<?>> domainClasses = domainClasses();

        assertThat(domainClasses).contains(Task.class, TaskStatus.class, TaskRepository.class, IdGenerator.class);
        List<String> violations = new ArrayList<>();
        for (Class<?> type : domainClasses) {
            violations.addAll(violationsOf(type));
        }
        assertThat(violations).isEmpty();
    }

    // ---- proof that the rule can fail (bad fixtures in test scope) ----

    @Test
    void transactionalOnAMethodIsReported() throws Exception {
        assertThat(violationsOf(TransactionalOnMethod.class)).anyMatch(v -> v.contains("Transactional"));
    }

    @Test
    void transactionalOnATypeIsReported() throws Exception {
        assertThat(violationsOf(TransactionalOnType.class)).anyMatch(v -> v.contains("Transactional"));
    }

    @Test
    void aWebTypeInASignatureIsReported() throws Exception {
        List<String> violations = violationsOf(WebTypeInSignature.class);

        assertThat(violations).anyMatch(v -> v.contains("signature") && v.contains("ApiResponses"));
        assertThat(violations).anyMatch(v -> v.contains("signature") && v.contains("ErrorCode"));
    }

    @Test
    void aWebTypeUsedOnlyInsideAMethodBodyIsReported() throws Exception {
        List<String> violations = violationsOf(WebTypeInBodyOnly.class);

        assertThat(violations).noneMatch(v -> v.contains("signature"));
        assertThat(violations).anyMatch(v -> v.contains("com/capstone/todo/web/"));
    }

    @Test
    void persistenceAndWebPackagesAreForbiddenByName() {
        assertThat(isForbiddenType("com.capstone.todo.persistence.SqliteTaskRepository")).isTrue();
        assertThat(isForbiddenType("com.capstone.todo.web.response.ApiResponses")).isTrue();
        assertThat(isForbiddenType("com.capstone.todo.domain.Task")).isFalse();
        assertThat(isForbiddenType("java.util.List")).isFalse();
        assertThat(isForbiddenType("com.capstone.todo.webhook.Other")).isFalse();
    }

    @Test
    void theDomainClassesAreFoundInTheMainClassDirectory() throws Exception {
        assertThat(classFileOf(Task.class).toString().replace('\\', '/')).contains("/target/classes/");
    }

    // ---- scanning ----

    private static List<Class<?>> domainClasses() throws Exception {
        Path directory = classFileOf(Task.class).getParent();
        String packageName = Task.class.getPackageName();
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String relative = directory.relativize(file).toString().replace('\\', '/').replace('/', '.');
                String name = packageName + "." + relative.substring(0, relative.length() - ".class".length());
                classes.add(Class.forName(name, false, Task.class.getClassLoader()));
            }
        }
        return classes;
    }

    private static Path classFileOf(Class<?> type) throws Exception {
        URL url = type.getClassLoader().getResource(type.getName().replace('.', '/') + ".class");
        return Path.of(url.toURI());
    }

    private static List<String> violationsOf(Class<?> type) throws Exception {
        List<String> violations = new ArrayList<>();
        String where = type.getName();

        String constantPoolText = new String(Files.readAllBytes(classFileOf(type)), StandardCharsets.ISO_8859_1);
        for (String forbidden : FORBIDDEN_CONSTANT_POOL_TEXT) {
            if (constantPoolText.contains(forbidden)) {
                violations.add(where + " mentions " + forbidden);
            }
        }

        checkAnnotations(where, type.getAnnotations(), violations);
        collectTypes(type.getGenericSuperclass(), where + " superclass", violations);
        for (Type implemented : type.getGenericInterfaces()) {
            collectTypes(implemented, where + " interface", violations);
        }
        for (Field field : type.getDeclaredFields()) {
            checkAnnotations(where + "." + field.getName(), field.getAnnotations(), violations);
            collectTypes(field.getGenericType(), where + "." + field.getName() + " signature", violations);
        }
        for (Method method : type.getDeclaredMethods()) {
            String id = where + "." + method.getName();
            checkAnnotations(id, method.getAnnotations(), violations);
            collectTypes(method.getGenericReturnType(), id + " signature", violations);
            for (Type parameter : method.getGenericParameterTypes()) {
                collectTypes(parameter, id + " signature", violations);
            }
            for (Type thrown : method.getGenericExceptionTypes()) {
                collectTypes(thrown, id + " signature", violations);
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            String id = where + ".<init>";
            checkAnnotations(id, constructor.getAnnotations(), violations);
            for (Type parameter : constructor.getGenericParameterTypes()) {
                collectTypes(parameter, id + " signature", violations);
            }
        }
        return violations;
    }

    private static void checkAnnotations(String where, Annotation[] annotations, List<String> violations) {
        for (Annotation annotation : annotations) {
            if (TRANSACTIONAL_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                violations.add(where + " has @Transactional");
            }
        }
    }

    private static void collectTypes(Type type, String where, List<String> violations) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                collectTypes(clazz.getComponentType(), where, violations);
            } else if (isForbiddenType(clazz.getName())) {
                violations.add(where + " uses " + clazz.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectTypes(parameterized.getRawType(), where, violations);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectTypes(argument, where, violations);
            }
        } else if (type instanceof GenericArrayType array) {
            collectTypes(array.getGenericComponentType(), where, violations);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collectTypes(bound, where, violations);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                collectTypes(bound, where, violations);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                collectTypes(bound, where, violations);
            }
        }
    }

    private static boolean isForbiddenType(String className) {
        return FORBIDDEN_PACKAGES.stream().anyMatch(className::startsWith);
    }
}
