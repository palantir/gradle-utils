/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.utils.zip;

import com.google.auto.service.AutoService;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.palantir.gradle.utils.zip.Zips")
public final class ZipProcessor extends AbstractProcessor {

    private static final int MAX_ARITY = 253;

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<Integer> arities = roundEnv.getElementsAnnotatedWith(Zips.class).stream()
                .map(element -> element.getAnnotation(Zips.class))
                .flatMapToInt(annotation -> IntStream.of(annotation.value()))
                .boxed()
                .filter(arity -> {
                    if (arity > MAX_ARITY) {
                        throw new SafeRuntimeException("Cannot zip more than " + MAX_ARITY + " providers");
                    }
                    if (arity <= 0) {
                        throw new SafeRuntimeException("Cannot zip a negative arity");
                    }
                    return true;
                })
                .collect(Collectors.toSet());

        if (!arities.isEmpty()) {
            try {
                generateZipMethods("com.palantir.gradle.utils.zip", "Zipper", arities);
            } catch (IOException e) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                javax.tools.Diagnostic.Kind.ERROR, "Failed to generate zip methods: " + e.getMessage());
            }
        }
        return false;
    }

    private void generateZipMethods(String packageName, String className, Set<Integer> arities) throws IOException {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + className);

        try (Writer writer = sourceFile.openWriter()) {
            writeHeader(writer, packageName, className);

            String methods =
                    arities.stream().sorted().map(this::generateMethodsForArity).collect(Collectors.joining("\n"));

            writer.write(methods);
            writer.write("}\n");
        }
    }

    private void writeHeader(Writer writer, String packageName, String className) throws IOException {
        String header = String.join(
                "\n",
                String.format("package %s;", packageName),
                "",
                "import org.gradle.api.provider.Provider;",
                "import org.gradle.api.provider.ListProperty;",
                "import org.gradle.api.model.ObjectFactory;",
                "import java.util.List;",
                "import java.util.function.Function;",
                "import javax.inject.Inject;",
                "",
                "/** Generated zip methods by ZipProcessor */",
                String.format("public abstract class %s {", className),
                "",
                "    @Inject",
                "    protected abstract ObjectFactory getObjectFactory();",
                "",
                "    protected final <Res> Provider<Res> zipInternal(Function<List<Object>, Res> combiner, Provider<?>... providers) {",
                "        ListProperty<Object> listProperty = getObjectFactory().listProperty(Object.class);",
                "        for (Provider<?> provider : providers) {",
                "            listProperty.add(provider);",
                "        }",
                "        return listProperty.map(combiner::apply);",
                "    }",
                "");

        writer.write(header);
        writer.write("\n");
    }

    private String generateMethodsForArity(int arity) {
        return generateZipMethod(arity) + "\n" + generateFunctionalInterface(arity);
    }

    private String generateZipMethod(int arity) {
        String typeParams = generateTypeParams(arity);
        String methodParams = generateMethodParams(arity);
        String providerArgs = generateProviderArgs(arity);
        String extractors = generateExtractors(arity);
        String combinerArgs = generateCombinerArgs(arity);

        return String.join(
                "\n",
                String.format("    public final <%s, Res> Provider<Res> zip(", typeParams),
                String.format("            %s,", methodParams),
                String.format("            Function%d<%s, Res> combiner) {", arity, typeParams),
                "        return zipInternal(",
                "                list -> {",
                extractors,
                String.format("                    return combiner.apply(%s);", combinerArgs),
                "                },",
                String.format("                %s);", providerArgs),
                "    }",
                "");
    }

    private String generateFunctionalInterface(int arity) {
        String typeParams = generateTypeParams(arity);
        String applyParams = generateApplyParams(arity);

        return String.join(
                "\n",
                "    @FunctionalInterface",
                String.format("    public interface Function%d<%s, Res> {", arity, typeParams),
                String.format("        Res apply(%s);", applyParams),
                "    }",
                "");
    }

    /**
     * Generates a type parameter name using T-prefix convention: T1, T2, T3, etc.
     * This is much cleaner and scales infinitely without alphabet limitations.
     */
    private String getTypeParamName(int index) {
        return "T" + (index + 1);
    }

    private String generateTypeParams(int arity) {
        return IntStream.range(0, arity).mapToObj(this::getTypeParamName).collect(Collectors.joining(", "));
    }

    private String generateMethodParams(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    String typeName = getTypeParamName(i);
                    // Use lowercase 'provider' prefix with index for cleaner parameter names
                    return String.format("Provider<%s> provider%d", typeName, i + 1);
                })
                .collect(Collectors.joining(", "));
    }

    private String generateProviderArgs(int arity) {
        return IntStream.range(0, arity).mapToObj(i -> "provider" + (i + 1)).collect(Collectors.joining(", "));
    }

    private String generateExtractors(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    String typeName = getTypeParamName(i);
                    // Use 1-based indexing for arg names to match type params
                    return String.format(
                            "                    %s arg%d = (%s) list.get(%d);", typeName, i + 1, typeName, i);
                })
                .collect(Collectors.joining("\n"));
    }

    private String generateCombinerArgs(int arity) {
        return IntStream.range(0, arity).mapToObj(i -> "arg" + (i + 1)).collect(Collectors.joining(", "));
    }

    private String generateApplyParams(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    String typeName = getTypeParamName(i);
                    return String.format("%s arg%d", typeName, i + 1);
                })
                .collect(Collectors.joining(", "));
    }
}
