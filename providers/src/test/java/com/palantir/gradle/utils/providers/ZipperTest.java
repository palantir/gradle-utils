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

import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZipperTest {

    private Zipper zipper;

    @BeforeEach
    void beforeEach() {
        zipper = ProjectBuilder.builder().build().getObjects().newInstance(Zipper.class);
    }

    @Nested
    class Zip3 {

        @Test
        void should_combine_three_providers() {
            // Given
            Provider<String> p1 = ProjectBuilder.builder().build().provider(() -> "A");
            Provider<String> p2 = ProjectBuilder.builder().build().provider(() -> "B");
            Provider<String> p3 = ProjectBuilder.builder().build().provider(() -> "C");

            // When
            Provider<String> combined = zipper.zip(p1, p2, p3, (a, b, c) -> a + b + c);

            // Then
            assertThat(combined).isNotNull();
            assertThat(combined.get()).isEqualTo("ABC");
        }

        @Test
        void should_defer_execution_until_get() {
            // Given
            boolean[] called = {false};
            Provider<String> p1 = ProjectBuilder.builder().build().provider(() -> {
                called[0] = true;
                return "A";
            });
            Provider<String> p2 = ProjectBuilder.builder().build().provider(() -> "B");
            Provider<String> p3 = ProjectBuilder.builder().build().provider(() -> "C");

            // When
            Provider<String> combined = zipper.zip(p1, p2, p3, (a, b, c) -> a + b + c);

            // Then
            assertThat(called[0]).isFalse();
            combined.get();
            assertThat(called[0]).isTrue();
            assertThat(combined.get()).isEqualTo("ABC");
        }
    }

    @Nested
    class Zip4 {

        @Test
        void should_combine_four_providers() {
            // Given
            Provider<String> p1 = ProjectBuilder.builder().build().provider(() -> "A");
            Provider<String> p2 = ProjectBuilder.builder().build().provider(() -> "B");
            Provider<String> p3 = ProjectBuilder.builder().build().provider(() -> "C");
            Provider<String> p4 = ProjectBuilder.builder().build().provider(() -> "D");

            // When
            Provider<String> combined = zipper.zip(p1, p2, p3, p4, (a, b, c, d) -> a + b + c + d);

            // Then
            assertThat(combined.get()).isEqualTo("ABCD");
        }

        @Test
        void should_defer_execution_until_get() {
            // Given
            boolean[] called = {false};
            Provider<String> p1 = ProjectBuilder.builder().build().provider(() -> {
                called[0] = true;
                return "A";
            });
            Provider<String> p2 = ProjectBuilder.builder().build().provider(() -> "B");
            Provider<String> p3 = ProjectBuilder.builder().build().provider(() -> "C");
            Provider<String> p4 = ProjectBuilder.builder().build().provider(() -> "D");

            // When
            Provider<String> combined = zipper.zip(p1, p2, p3, p4, (a, b, c, d) -> a + b + c + d);

            // Then
            assertThat(called[0]).isFalse();
            combined.get();
            assertThat(called[0]).isTrue();
            assertThat(combined.get()).isEqualTo("ABCD");
        }
    }
}
