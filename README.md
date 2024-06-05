<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-utils"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-utils

Common utilities for use in Palantir Gradle Plugins.

Please add utilities in their own package, so there is a hope they can be broken out into their own repo at some point. It also allows different utility classes to be independently versioned in people's setups.

## `LazilyConfiguredMapping`

Like a `MapProperty`, except you do not have to know the keys upfront if you wish to insert multiple values lazily.

### Dependency

```
implementation 'com.palantir.gradle.utils:lazily-configured-mapping:<version>'
```

## `EnvironmentVariables`

A utility class for accessing environment variables. This is useful for testing, as it allows you to set environment variables in a test by using Gradle properties and have them be available to the code under test.
Moreover, using this class avoids accidentally using environment variables from the testing environment.

### Dependency

```
implementation 'com.palantir.gradle.utils:environment-variables:<version>'
```

### Example Usage

To use these in Gradle tests, set the Gradle property ```__TESTING``` to true and the desired variable value as a property with the prefix ```__TESTING_```. Example:
```
runTasksSuccessfully(taskName, '-P__TESTING=true', '-P__TESTING_FOO=TEST_VALUE')
```

To retrieve the value of a (test) environment variable, use the ```EnvironmentVariables``` class. Example:
```java
// If testing like above, returns "TEST_VALUE", otherwise returns the actual value of the environment variable "FOO"
String value = environmentVariables.envVarOrFromTestingProperty("FOO").get(); 
```

## `gutil`

The `GUtil` class in the `org.gradle.util` package has been deprecated and will be banned at some point. There are some very useful methods in here, notably around camel casing for use as task names. This library provides a selection of these methods. Please add more as needed, but try to avoid adding methods that are not used by any of the plugins.

### Dependency

```
implementation 'com.palantir.gradle.utils:gutil:<version>'
```

## `dependency-graph-utils`

[To support Configuration Cache, you need to use new APIs](https://docs.gradle.org/8.4/userguide/configuration_cache.html#config_cache:requirements:~:text=Referencing%20dependency%20resolution,invoking%20ResolutionResult.getRootComponent()) when passing the components of a resolved `Configuration` to a `Task`. No longer can you pass in the `Configuration` object then use it in the task, instead you must call the method [`Provider<ResolvedComponentResult> getRootComponent()`](https://docs.gradle.org/8.4/javadoc/org/gradle/api/artifacts/result/ResolutionResult.html#getRootComponent--) on `ResolutionResult` rather than [`Set<ResolvedComponentResult> getAllComponents()`](https://docs.gradle.org/8.4/javadoc/org/gradle/api/artifacts/result/ResolutionResult.html#getAllComponents--).

The problem is that this just gives you the root - you need to do a search of the graph yourself to get all the components. This library provides a utility method to do this for you: `DependencyGraphUtils#getAllComponents(ResolvedComponentResult)`.

### Dependency

```
implementation 'com.palantir.gradle.utils:dependency-graph-utils:<version>'
```

## `circleci-artifacts`

Plugins can sometimes fail in weird corner cases and may not be reproducible locally. When running a plugin in CI, it is often useful to emit files that can be later used for debugging after the fact should something go wrong. 

In CircleCI, you can write files to a location specified by the `CIRCLE_ARTIFACTS` environment variable, and you will be able to see these files in the artifacts tab in the UI.

This library makes it relatively easy to just get a location and not do a bunch of checks that end up getting proliferated everywhere.

In addition, it can produce a URL that can be embedded in error messages for easier debuggability.

### Dependency Information

```
implementation 'com.palantir.gradle.utils:circleci-artifacts:<version>'
```

### How to use

#### `@Nested` annotation

You can use this within any sort of [managed object](https://docs.gradle.org/current/userguide/custom_gradle_types.html#managed_properties) by using the `@Nested` annotation.
```gradle
public abstract class MyTaskOrExtension {
    @Nested
    abstract CircleCiArtifacts getCircleCiArtifacts();
}
```

As Gradle manages the lifecycle of this object, it will instantiate `CircleCiArtifacts` for you and provide an implementation of the method that returns the aforementioned object

#### `ObjectFactory` instantiation

Depending on context, you can also explicitly invoke Gradle's `ObjectFactory`:

```java
public final class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // you can create as many instances as  you like as it's not tied to any lifecycle
        CircleCiArtifacts artifacts = project.getObjects().newInstance(CircleCiArtifacts.class);
    }
}
```

## `develocity-adapter-utils`

With the introduction of Develocity, the gradle-enterprise plugin was deprecated and extensions were renamed. If you make use of the deprecated classes, a deprecation warning is printed at the end of each build for example:

```
BUILD FAILED in 36s
98 actionable tasks: 6 executed, 1 from cache, 91 up-to-date
WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin. 
Run with '-Ddevelocity.deprecation.captureOrigin=true' to see where the deprecated functionality is being used. 
For assistance with migration, see https://gradle.com/help/gradle-plugin-develocity-migration.
- The deprecated "gradleEnterprise.buildScan.server" API has been replaced by "develocity.server"
- The deprecated "gradleEnterprise.buildScan.tag" API has been replaced by "develocity.buildScan.tag"
- The deprecated "gradleEnterprise.buildScan.value" API has been replaced by "develocity.buildScan.value"
- The deprecated "gradleEnterprise.buildScan.background" API has been replaced by "develocity.buildScan.background"
- The deprecated "gradleEnterprise.buildScan.obfuscation.hostname" API has been replaced by "develocity.buildScan.obfuscation.hostname"
- The deprecated "gradleEnterprise.buildScan.obfuscation.ipAddresses" API has been replaced by "develocity.buildScan.obfuscation.ipAddresses"
- The deprecated "gradleEnterprise.buildScan.buildFinished" API has been replaced by "develocity.buildScan.buildFinished"
- The "com.gradle.enterprise" plugin has been replaced by "com.gradle.develocity"
- The "buildScan.publishAlways" API has been removed, the updated Develocity "buildScan.publishing" API publishes a Build Scan by default
```

Both of these plugins are `settings` plugins for builds on Gradle 6 and above. It's not possible to ascertain whether a `settings` plugin has been applied within a `project`  context. If we want to support both types of builds, we're left having to check for the existence of the new extension (`develocity`) and then performing the required actions or fall back to the `gradleEnterprise` extension if the new plugin has not been applied. 

The extensions are of different types - `DevelocityConfiguration` vs `GradleEnterpriseExtension` and have slight differences. Fortunately, gradle have released an [adapters](https://github.com/gradle/develocity-agent-adapters) library to make supporting both easier. This is achieved by using a `DevelocityAdapter` which will delegate to the right methods on each type of extension. Unfortunately, you still have to write the adapter construction code yourself. This is where this library comes in. You can use it like so:

```java
private void emitValueInBuildScan(Project project, String name, String value) {
    Optional<DevelocityAdapter> adapter = DevelocityUtils.getDevelocityExtension(project);
    if (adapter.isEmpty()) {
        return;
    }
    
    adapter.get().buildScan(buildScan -> buildScan.value(name, value));
}
```

### Dependency Information
```
implementation 'com.palantir.gradle.utils:develocity-adapter-utils:<version>'
```