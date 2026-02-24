# create a p2 mirror service

use the existing implementation `P2Mirror.java` and the project it is contained in as starting point.

Make it a DS factory service spawning new instances for each configuration provided.
Create a configuration interface `@P2MirrorConfig` containing the repo root folder location with the LOCAL_ROOT_URI as default and the other options from p2 mirroring with meaningful defaults like currently in the P2Mirror implementation provided.
Comment the properties inside the config

Create an api sub bundle containing exported P2Mirror api class with a method mirror(URI repo) which the P2Mirror service is implementing

create a gogo sub bundle containing  command name `mirrorRepo(URI sourceURI)` using the api to mirror

create a new bnd test project