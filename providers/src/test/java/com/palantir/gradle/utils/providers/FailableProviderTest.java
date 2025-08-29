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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FailableProviderTest {

    @Value.Immutable
    public interface TestValue {
        String value();

        boolean isFailure();

        static TestValue of(String value, boolean isFailure) {
            return ImmutableTestValue.builder()
                    .value(value)
                    .isFailure(isFailure)
                    .build();
        }
    }

    private Project project;

    @BeforeEach
    void beforeEach() {
        project = ProjectBuilder.builder().build();
    }

    private FailableProvider<TestValue> createProvider(TestValue testValue) {
        return new DefaultFailableProvider<>(
                project.provider(() -> testValue),
                TestValue::isFailure,
                value -> new IllegalStateException("Failure: " + value.value()));
    }

    @Test
    void get_returns_value_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        assertThat(provider.get().value()).isEqualTo("ok");
    }

    @Test
    void get_throws_on_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("bad", true));
        assertThatThrownBy(provider::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failure: bad");
    }

    @Test
    void getOrNull_returns_null_on_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("bad", true));
        assertThat(provider.getOrNull()).isNull();
    }

    @Test
    void getOrNull_returns_value_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("good", false));
        assertThat(provider.getOrNull().value()).isEqualTo("good");
    }

    @Test
    void getOrElse_returns_default_on_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("bad", true));
        TestValue fallback = TestValue.of("fallback", false);
        assertThat(provider.getOrElse(fallback)).isEqualTo(fallback);
    }

    @Test
    void getOrElse_returns_value_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("good", false));
        TestValue fallback = TestValue.of("fallback", false);
        assertThat(provider.getOrElse(fallback).value()).isEqualTo("good");
    }

    @Test
    void getOrThrow_throws_custom_exception_on_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        assertThatThrownBy(() -> provider.getOrThrow(val -> new RuntimeException("Custom: " + val.value())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Custom: fail");
    }

    @Test
    void getOrThrow_returns_value_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        assertThat(provider.getOrThrow(_val -> new RuntimeException("Should not throw"))
                        .value())
                .isEqualTo("ok");
    }

    @Test
    void mapFailure_replaces_exception() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true))
                .mapFailure(val -> new UnsupportedOperationException("Oops: " + val.value()));
        assertThatThrownBy(provider::get)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Oops: fail");
    }

    @Test
    void mapFailure_does_not_affect_success() {
        FailableProvider<TestValue> provider =
                createProvider(TestValue.of("ok", false)).mapFailure(_val -> new RuntimeException("Should not throw"));
        assertThat(provider.get().value()).isEqualTo("ok");
    }

    @Test
    void getRaw_returns_value_even_on_failure() {
        TestValue fail = TestValue.of("fail", true);
        FailableProvider<TestValue> provider = createProvider(fail);
        assertThat(provider.getRaw()).isEqualTo(fail);
    }

    @Test
    void fold_returns_onSuccess_for_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        String result = provider.handle(val -> "Yay: " + val.value(), val -> "Boo: " + val.value())
                .get();
        assertThat(result).isEqualTo("Yay: ok");
    }

    @Test
    void fold_returns_onFailure_for_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        String result = provider.handle(val -> "Yay: " + val.value(), val -> "Boo: " + val.value())
                .get();
        assertThat(result).isEqualTo("Boo: fail");
    }

    @Test
    void map_propagates_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        Provider<String> mapped = provider.map(val -> "mapped: " + val.value());
        assertThatThrownBy(mapped::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failure: fail");
    }

    @Test
    void map_applies_transform_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        Provider<String> mapped = provider.map(val -> "mapped: " + val.value());
        assertThat(mapped.get()).isEqualTo("mapped: ok");
    }

    @Test
    void flatMap_propagates_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        Provider<String> mapped = provider.flatMap(_val -> project.provider(() -> "should not run"));
        assertThatThrownBy(mapped::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void flatMap_applies_transform_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        Provider<String> mapped = provider.flatMap(val -> project.provider(() -> "flat: " + val.value()));
        assertThat(mapped.get()).isEqualTo("flat: ok");
    }

    @Test
    void filter_passes_success_when_spec_matches() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("good", false));
        Provider<TestValue> filtered = provider.filter(val -> val.value().equals("good"));
        assertThat(filtered.get().value()).isEqualTo("good");
    }

    @Test
    void filter_returns_null_when_spec_does_not_match() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("good", false));
        Provider<TestValue> filtered = provider.filter(_val -> false);
        assertThat(filtered.getOrNull()).isNull();
    }

    @Test
    void filter_always_passes_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        Provider<TestValue> filtered = provider.filter(_val -> false);
        assertThatThrownBy(filtered::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zip_combines_success() {
        FailableProvider<TestValue> provider1 = createProvider(TestValue.of("one", false));
        FailableProvider<TestValue> provider2 = createProvider(TestValue.of("two", false));
        Provider<String> zipped = provider1.zip(provider2, (a, b) -> a.value() + "+" + b.value());
        assertThat(zipped.get()).isEqualTo("one+two");
    }

    @Test
    void zip_fails_fast_on_failure() {
        FailableProvider<TestValue> provider1 = createProvider(TestValue.of("fail", true));
        FailableProvider<TestValue> provider2 = createProvider(TestValue.of("good", false));
        Provider<String> zipped = provider1.zip(provider2, (_a, _b) -> "should not run");
        assertThatThrownBy(zipped::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void orElse_returns_default_on_failure() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        TestValue fallback = TestValue.of("fallback", false);
        assertThat(provider.orElse(fallback).get()).isEqualTo(fallback);
    }

    @Test
    void orElse_returns_value_on_success() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        TestValue fallback = TestValue.of("fallback", false);
        assertThat(provider.orElse(fallback).get().value()).isEqualTo("ok");
    }

    @Test
    void isPresent_is_true_when_delegate_present() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        assertThat(provider.isPresent()).isTrue();
    }

    @Test
    void forUseAtConfigurationTime_delegates() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("ok", false));
        assertThat(provider.forUseAtConfigurationTime().get().value()).isEqualTo("ok");
    }

    @Test
    void failure_predicate_and_exception_mapper_are_used() {
        Predicate<TestValue> failIfValueIsBar = val -> val.value().equals("bar");
        Function<TestValue, RuntimeException> exceptionMapper =
                val -> new IllegalArgumentException("bad: " + val.value());
        FailableProvider<TestValue> provider = new DefaultFailableProvider<>(
                project.provider(() -> TestValue.of("bar", false)), failIfValueIsBar, exceptionMapper);
        assertThatThrownBy(provider::get)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad: bar");
    }

    @Test
    void mapFailure_is_chainable() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true))
                .mapFailure(_val -> new IllegalArgumentException("first"))
                .mapFailure(_val -> new UnsupportedOperationException("second"));
        assertThatThrownBy(provider::get)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("second");
    }

    @Test
    void mapFailure_can_be_used_multiple_times_and_last_wins() {
        FailableProvider<TestValue> provider = createProvider(TestValue.of("fail", true));
        FailableProvider<TestValue> provider2 = provider.mapFailure(_val -> new IllegalArgumentException("first"));
        FailableProvider<TestValue> provider3 =
                provider2.mapFailure(_val -> new UnsupportedOperationException("second"));
        assertThatThrownBy(provider3::get)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("second");
    }

    @Test
    void provider_is_lazy() {
        AtomicInteger calls = new AtomicInteger();
        Provider<TestValue> lazy = project.provider(() -> {
            calls.incrementAndGet();
            return TestValue.of("lazy", false);
        });
        FailableProvider<TestValue> provider =
                new DefaultFailableProvider<>(lazy, _v -> false, _v -> new RuntimeException("fail"));
        assertThat(calls.get()).isZero();
        provider.get();
        assertThat(calls.get()).isEqualTo(1);
    }
}
