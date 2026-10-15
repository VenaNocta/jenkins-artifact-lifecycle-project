# Jenkins Artifact Lifecycle Project

The Artifact Lifecycle Project aims to secure the Supply-Pipeline of Artifacts built with Jenkins.
The project was created alongside the Master's Thesis:

**"Prevention Methods of Data Exfiltration & Infiltration through Tool-Dependency Poisoning in CI/CD Pipelines"**

If you found your way here, through the Thesis and you wish to test the exact plugins introduced in it, you have to switch to the [**`archive` branch**](https://github.com/VenaNocta/jenkins-artifact-lifecycle-project/tree/archive).

## WARNING: Rework & Migration in Progress

Please be adviced that we are currently reworking the project for the public release to the Jenkins Community.

The rework currently focuses on clear improvements in configurability and modularity.
The core functionality will stay mostly AS-IS, prior to the migration.

The Rework is roughly 80% completed.

Once our Rework-Tasks are completet we will open the Review Process with the Jenkins Community to migrate the Plugins.
Should you wish to use the plugins beforehand you can build them as shown in the [Building Section](#building).

### Rework - Open Tasks

- [ ] `[artifact-deployment-plugin]` Transition from **Direct Repo-Upload** to **Deployment Pipelines**
<br>With this change the sequential Artifact Validation (Malware Scan, ...) & Deployment to multiple Repos can be configured and modified without having to change any Pipelines.
- [x] `[repo-proxy-plugin]` Split off Maven Repo-Proxy from generic `repo-proxy-plugin` to `repo-proxy-maven-plugin`
- [ ] `[repo-proxy-plugin]` Switch to repackaged Netty hpi package.
- [ ] Dependency Cleanup (some projects currently have more dependencies than they actually need)
- [ ] Update all Deplendencies
- Upstream Deplendencies:
  - [ ] `[maven-repo-project]` Release
  - [ ] `[maven-jenkins-workflow]` Release

## Building

The project uses the 2 prerelease dependencies:

1. The maven plugin `maven-jenkins-workflow`, which provides configurations for the used `r4m-maven-extension`.
2. The `maven-repo-project` by RunedUniverse, which contains the just-in-time maven repo proxy on which the `repo-proxy-plugin` builds on.

As long as those projects are in the pre-release stage you can obtain their versions from RunedUniverse's development repository: https://nexus.runeduniverse.net/repository/maven-development/

After the initial clone of this repository you will have to initialize and pull all submodules, this can be accomplished with following command:
```bash
git submodule update --init --recursive --remote
```

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

