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
package com.palantir.gradle.utils.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZipperTest {

    private Zipper zipper;
    private Project project;

    @BeforeEach
    void beforeEach() {
        project = ProjectBuilder.builder().build();
        zipper = project.getObjects().newInstance(Zipper.class);
    }

    @Nested
    class Zip3 {

        @Test
        void should_combine_three_providers() {
            // Given
            Provider<String> p1 = project.provider(() -> "A");
            Provider<String> p2 = project.provider(() -> "B");
            Provider<String> p3 = project.provider(() -> "C");

            // When
            Provider<String> combined = zipper.zip3(p1, p2, p3, (a, b, c) -> a + b + c);

            // Then
            assertThat(combined).isNotNull();
            assertThat(combined.get()).isEqualTo("ABC");
        }

        @Test
        void should_defer_execution_until_get() {
            // Given
            AtomicBoolean called = new AtomicBoolean(false);
            Provider<String> p1 = project.provider(() -> {
                called.set(true);
                return "A";
            });
            Provider<String> p2 = project.provider(() -> "B");
            Provider<String> p3 = project.provider(() -> "C");

            // When
            Provider<String> combined = zipper.zip3(p1, p2, p3, (a, b, c) -> a + b + c);

            // Then
            assertThat(called).isFalse();
            combined.get();
            assertThat(called).isTrue();
            assertThat(combined.get()).isEqualTo("ABC");
        }
    }

    @Nested
    class Zip4 {

        @Test
        void should_combine_four_providers() {
            // Given
            Provider<String> p1 = project.provider(() -> "A");
            Provider<String> p2 = project.provider(() -> "B");
            Provider<String> p3 = project.provider(() -> "C");
            Provider<String> p4 = project.provider(() -> "D");

            // When
            Provider<String> combined = zipper.zip4(p1, p2, p3, p4, (a, b, c, d) -> a + b + c + d);

            // Then
            assertThat(combined.get()).isEqualTo("ABCD");
        }

        @Test
        void should_defer_execution_until_get() {
            // Given
            AtomicBoolean called = new AtomicBoolean(false);
            Provider<String> p1 = project.provider(() -> {
                called.set(true);
                return "A";
            });
            Provider<String> p2 = project.provider(() -> "B");
            Provider<String> p3 = project.provider(() -> "C");
            Provider<String> p4 = project.provider(() -> "D");

            // When
            Provider<String> combined = zipper.zip4(p1, p2, p3, p4, (a, b, c, d) -> a + b + c + d);

            // Then
            assertThat(called).isFalse();
            combined.get();
            assertThat(called).isTrue();
            assertThat(combined.get()).isEqualTo("ABCD");
        }
    }
}
