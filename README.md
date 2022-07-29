<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-utils"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-utils

Common utilities for use in Palantir Gradle Plugins.

Please add utilities in their own package, so there is a hope they can be broken out into their own repo at some point. It also allows different utility classes to be independently versioned in people's setups.

## LazilyConfiguredMapping

Like a `MapProperty`, except you do not have to know the keys upfront if you wish to insert multiple values lazily.
