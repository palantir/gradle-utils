/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.utils.circleciartifacts;

import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import com.palantir.gradle.utils.providers.Zipper;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

@SuppressWarnings("JavaxInjectOnAbstractMethod")
public abstract class CircleCiArtifacts {

    @Nested
    protected abstract EnvironmentVariables getVariables();

    @Nested
    protected abstract Zipper getZipper();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    public final Provider<ArtifactLocation> resolveArtifactLocation(String pathWithinArtifactsDirectory) {
        Provider<RegularFile> physicalArtifactPath = getProjectLayout()
                .file(getVariables()
                        .envVarOrFromTestingProperty("CIRCLE_ARTIFACTS")
                        .map(Paths::get)
                        .map(artifactsPath -> artifactsPath.resolve(pathWithinArtifactsDirectory))
                        .map(Path::toFile));
        Provider<String> circleProjectUsername = getVariables().envVarOrFromTestingProperty("CIRCLE_PROJECT_USERNAME");
        Provider<String> circleProjectReponame = getVariables().envVarOrFromTestingProperty("CIRCLE_PROJECT_REPONAME");
        Provider<String> circleBuildNum = getVariables().envVarOrFromTestingProperty("CIRCLE_BUILD_NUM");
        Provider<String> circleNodeIndex = getVariables().envVarOrFromTestingProperty("CIRCLE_NODE_INDEX");
        Provider<String> circleArtifacts = physicalArtifactPath
                .map(file -> file.getAsFile().getAbsolutePath())
                .map(artifacts -> artifacts.replace("/home/circleci/", "/~/"));
        Provider<String> circleUrl = getVariables()
                .envVarOrFromTestingProperty("CIRCLE_BUILD_URL")
                .map(CircleCiArtifacts::extractDomain)
                .orElse("https://<circle_url>");
        Provider<String> workflowJobId = getVariables().envVarOrFromTestingProperty("CIRCLE_WORKFLOW_JOB_ID");

        Provider<String> externalLocation = getZipper()
                .zip4(
                        circleProjectUsername,
                        circleProjectReponame,
                        circleBuildNum,
                        circleNodeIndex,
                        (username, reponame, buildNum, nodeIndex) ->
                                username + "/" + reponame + "/" + buildNum + "/artifacts/" + nodeIndex)
                .zip(circleArtifacts, (fullPath, artifactPath) -> fullPath + artifactPath);

        Provider<String> circleLink = getZipper()
                .zip4(
                        circleUrl,
                        workflowJobId,
                        circleNodeIndex,
                        circleArtifacts,
                        (url, jobId, nodeIndex, artifactPath) ->
                                url + "/output/job/" + jobId + "/artifacts/" + nodeIndex + artifactPath);

        return getZipper().zip3(physicalArtifactPath, externalLocation, circleLink, ArtifactLocation::of);
    }

    private static String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new RuntimeException("Invalid CIRCLE_BUILD_URL: " + url);
            }

            return scheme + "://" + host;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid CIRCLE_BUILD_URL: " + url, e);
        }
    }
}
