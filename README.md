# Jenkins Artifact Lifecycle Project

This repository contains the 6 Jenkins plugins used in the tesis:
"Prevention Methods of Data Exfiltration & Infiltration through Tool-Dependency Poisoning in CI/CD Pipelines"

## Building

The project uses the 2 prerelease dependencies:

1. The maven plugin `maven-jenkins-workflow`, which provides configurations for the used `r4m-maven-extension`.
2. The `maven-repo-project` by RunedUniverse, which contains the just-in-time maven repo proxy on which the `repo-proxy-plugin` builds on.

As long as those projects are in the pre-release stage you can obtain their versions from RunedUniverse's development repository: https://nexus.runeduniverse.net/repository/maven-development/

If you use the predefined Maven profile `dev-install` you can simply rebuild all plugins from source.
RunedUniverse's development repository is also preconfigured in that profile.

Execute the command in the folder where this README is located! -> it will build all projects

Build with:
```bash
mvn -Pdev-install
```

Additionally, you will need to have the jdk-toolchain for version `openjdk-17` configured:

Toolchain config example, for fedora: `~/.m2/toolchains.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
	<toolchain>
		<type>jdk</type>
		<provides>
			<version>openjdk-17</version>
		</provides>
		<configuration>
			<jdkHome>/usr/lib/jvm/java-17-openjdk</jdkHome>
		</configuration>
	</toolchain>
</toolchains>
```

