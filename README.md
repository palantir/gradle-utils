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

