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
