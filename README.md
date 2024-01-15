<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-utils"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-utils

Common utilities for use in Palantir Gradle Plugins.

Please add utilities in their own package, so there is a hope they can be broken out into their own repo at some point. It also allows different utility classes to be independently versioned in people's setups.

## LazilyConfiguredMapping

Like a `MapProperty`, except you do not have to know the keys upfront if you wish to insert multiple values lazily.


## EnvironmentVariables

A utility class for accessing environment variables. This is useful for testing, as it allows you to set environment variables in a test by using Gradle properties and have them be available to the code under test.
Moreover, using this class avoids accidentally using environment variables from the testing environment.

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

## GUtil

The `GUtil` class in the `org.gradle.util` package has been deprecated and will be banned at some point. There are some very useful methods in here, notably around camel casing for use as task names. This library provides a selection of these methods. Please add more as needed, but try to avoid adding methods that are not used by any of the plugins. 

## dependency-graph-utils

[To support Configuration Cache, you need to use new APIs](https://docs.gradle.org/8.4/userguide/configuration_cache.html#config_cache:requirements:~:text=Referencing%20dependency%20resolution,invoking%20ResolutionResult.getRootComponent()) when passing the components of a resolved `Configuration` to a `Task`. No longer can you pass in the `Configuration` object then use it in the task, instead you must call the method [`Provider<ResolvedComponentResult> getRootComponent()`](https://docs.gradle.org/8.4/javadoc/org/gradle/api/artifacts/result/ResolutionResult.html#getRootComponent--) on `ResolutionResult` rather than [`Set<ResolvedComponentResult> getAllComponents()`](https://docs.gradle.org/8.4/javadoc/org/gradle/api/artifacts/result/ResolutionResult.html#getAllComponents--).

The problem is that this just gives you the root - you need to do a search of the graph yourself to get all the components. This library provides a utility method to do this for you: `DependencyGraphUtils#getAllComponents(ResolvedComponentResult)`.

## circleci-artifacts

Plugins can sometimes fail in weird corner cases and may not be reproducible locally. When running a plugin in CI, it is often useful to emit files that can be later used for debugging after the fact should something go wrong. 

In CircleCI, you can write files to a location specified by the `CIRCLE_ARTIFACTS` environment variable, and you will be able to see these files in the artifacts tab in the UI.

This library makes it relatively easy to just get a location and not do a bunch of checks that end up getting proliferated everywhere.

In addition, it can produce a URL that can be embedded in error messages for easier debuggability.

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