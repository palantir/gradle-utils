/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.utils.gutil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public final class GUtil {
    private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");

    public static String toLowerCamelCase(CharSequence string) {
        return toCamelCase(string, true);
    }

    public static String toCamelCase(CharSequence string) {
        return toCamelCase(string, false);
    }

    // Taken from the now deprecated Gradle GUtil class
    private static String toCamelCase(CharSequence string, boolean lower) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        Matcher matcher = WORD_SEPARATOR.matcher(string);
        int pos = 0;
        boolean first = true;
        while (matcher.find()) {
            String chunk = string.subSequence(pos, matcher.start()).toString();
            pos = matcher.end();
            if (chunk.isEmpty()) {
                continue;
            }
            if (lower && first) {
                chunk = StringUtils.uncapitalize(chunk);
                first = false;
            } else {
                chunk = StringUtils.capitalize(chunk);
            }
            builder.append(chunk);
        }
        String rest = string.subSequence(pos, string.length()).toString();
        if (lower && first) {
            rest = StringUtils.uncapitalize(rest);
        } else {
            rest = StringUtils.capitalize(rest);
        }
        builder.append(rest);
        return builder.toString();
    }

    private GUtil() {}
}
