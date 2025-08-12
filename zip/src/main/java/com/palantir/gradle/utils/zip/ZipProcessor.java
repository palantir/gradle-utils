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
import org.gradle.api.UncheckedIOException;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.palantir.gradle.utils.zip.GenerateZip")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public final class ZipProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<Integer> arities = roundEnv.getElementsAnnotatedWith(GenerateZip.class).stream()
                .map(e -> e.getAnnotation(GenerateZip.class).arity())
                .collect(Collectors.toSet());

        if (!arities.isEmpty()) {
            try {
                generateZipMethods("com.palantir.gradle.utils.zip", "ProviderZipGenerated", arities);
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

            arities.forEach(arity -> {
                try {
                    generateZipMethod(writer, arity);
                    generateFunctionalInterface(writer, arity);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            writer.write("}\n");
        }
    }

    private void writeHeader(Writer writer, String packageName, String className) throws IOException {
        writer.write(String.format("package %s;\n\n", packageName));
        writer.write("import org.gradle.api.provider.Provider;\n");
        writer.write("import org.gradle.api.provider.ListProperty;\n");
        writer.write("import org.gradle.api.model.ObjectFactory;\n");
        writer.write("import java.util.List;\n");
        writer.write("import java.util.function.Function;\n");
        writer.write("import javax.inject.Inject;\n\n");
        writer.write("/** Generated zip methods by ZipProcessor */\n");
        writer.write(String.format("public abstract class %s {\n\n", className));
        writer.write("    @Inject\n");
        writer.write("    protected abstract ObjectFactory getObjectFactory();\n\n");
        writer.write(
                "    protected final <R> Provider<R> zipInternal(Function<List<Object>, R> combiner, Provider<?>... providers) {\n");
        writer.write("        ListProperty<Object> listProperty = getObjectFactory().listProperty(Object.class);\n");
        writer.write("        for (Provider<?> provider : providers) {\n");
        writer.write("            listProperty.add(provider);\n");
        writer.write("        }\n");
        writer.write("        return listProperty.map(combiner::apply);\n");
        writer.write("    }\n\n");
    }

    private void generateZipMethod(Writer writer, int arity) throws IOException {
        String typeParams = IntStream.range(0, arity)
                .mapToObj(i -> String.valueOf((char) ('A' + i)))
                .collect(Collectors.joining(", "));

        String params = IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("Provider<%c> provider%c", type, type);
                })
                .collect(Collectors.joining(", "));

        String args = IntStream.range(0, arity)
                .mapToObj(i -> "provider" + (char) ('A' + i))
                .collect(Collectors.joining(", "));

        String extractors = IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("                    %c arg%d = (%c) list.get(%d);", type, i, type, i);
                })
                .collect(Collectors.joining("\n"));

        String combinerArgs = IntStream.range(0, arity).mapToObj(i -> "arg" + i).collect(Collectors.joining(", "));

        writer.write(String.format("    public final <%s, R> Provider<R> zip(\n", typeParams));
        writer.write(String.format("            %s,\n", params));
        writer.write(String.format("            Function%d<%s, R> combiner) {\n", arity, typeParams));
        writer.write("        return zipInternal(\n");
        writer.write("                list -> {\n");
        writer.write(extractors + "\n");
        writer.write(String.format("                    return combiner.apply(%s);\n", combinerArgs));
        writer.write("                },\n");
        writer.write(String.format("                %s);\n", args));
        writer.write("    }\n\n");
    }

    private void generateFunctionalInterface(Writer writer, int arity) throws IOException {
        String typeParams = IntStream.range(0, arity)
                .mapToObj(i -> String.valueOf((char) ('A' + i)))
                .collect(Collectors.joining(", "));

        String params = IntStream.range(0, arity)
                .mapToObj(i -> {
                    char type = (char) ('A' + i);
                    return String.format("%c arg%d", type, i);
                })
                .collect(Collectors.joining(", "));

        writer.write("    @FunctionalInterface\n");
        writer.write(String.format("    public interface Function%d<%s, R> {\n", arity, typeParams));
        writer.write(String.format("        R apply(%s);\n", params));
        writer.write("    }\n\n");
    }
}
