# This plugin is still work in progress!

# Jenkins: artifact-signing-plugin


## Secure Signing Concept

The Core is to keep the key-material (GPG private key) save, therefore the key-material is never transferred to a possibly insecure Jenkins Node or exposed to the Sandboxed Scripted-Pipeline where it is used.

**What does the `signArtifacts` step do?**

1. A secure node is selected. By default that is the Jenkins Controller but other Nodes may also be marked as secure.
2. The artifacts to be signed are selected from the archived artifacts and downloaded to the secure node's memory.
3. The selected artifacts are signed in memory and written to a seperate workspace on the secure node.
4. The artifact signatures are now archived with the `ArtifactManager` and fingerprinted (if enabled, default=`true`).

> [!WARNING]
> Due to Jenkin's implementation of the `ArtifactManager` the manager has to be able to spawn processes in the workspace of the secure node! (and thats the Reason why we can't simply do everything in memory...)
> <br>
> Dear Admins, if you mark your Build-Node as "secure", you have to ensure that processes started from the Sandboxed Scripted-Pipeline are unable to access the "secure" workspace on the filesystem level. This is to ensure that malicious software injected into the pipeline via a "Supply Pipeline Attack" can not modify the artifact-signature before it is archived and fingerprinted.

