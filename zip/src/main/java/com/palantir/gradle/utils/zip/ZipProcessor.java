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
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.palantir.gradle.utils.zip.Zips")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public final class ZipProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<Integer> arities = roundEnv.getElementsAnnotatedWith(Zips.class).stream()
                .map(element -> element.getAnnotation(Zips.class))
                .flatMapToInt(annotation -> IntStream.of(annotation.arities()))
                .boxed()
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
        return true;
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
                "    protected final <R> Provider<R> zipInternal(Function<List<Object>, R> combiner, Provider<?>... providers) {",
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
                String.format("    public final <%s, R> Provider<R> zip(", typeParams),
                String.format("            %s,", methodParams),
                String.format("            Function%d<%s, R> combiner) {", arity, typeParams),
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
                String.format("    public interface Function%d<%s, R> {", arity, typeParams),
                String.format("        R apply(%s);", applyParams),
                "    }",
                "");
    }

    private String generateTypeParams(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> String.valueOf((char) ('A' + i)))
                .collect(Collectors.joining(", "));
    }

    private String generateMethodParams(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("Provider<%c> provider%c", type, type);
                })
                .collect(Collectors.joining(", "));
    }

    private String generateProviderArgs(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> "provider" + (char) ('A' + i))
                .collect(Collectors.joining(", "));
    }

    private String generateExtractors(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("                    %c arg%d = (%c) list.get(%d);", type, i, type, i);
                })
                .collect(Collectors.joining("\n"));
    }

    private String generateCombinerArgs(int arity) {
        return IntStream.range(0, arity).mapToObj(i -> "arg" + i).collect(Collectors.joining(", "));
    }

    private String generateApplyParams(int arity) {
        return IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("%c arg%d", type, i);
                })
                .collect(Collectors.joining(", "));
    }
}