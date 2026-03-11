package io.klib.app.p2.mirror.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
	name = "P2 Mirror Service Configuration",
	description = "Factory configuration for spawning p2 mirror service instances."
)
public @interface P2MirrorConfig {

	String LOCAL_ROOT_URI = "LOCAL_ROOT_URI";
	String AGENT_URI = "LOCAL_AGENT_URI";

	@AttributeDefinition(
		name = "Mirror Root Folder URI",
		description = "Root folder where mirrored repositories are written."
	)
	String repoRootUri() default LOCAL_ROOT_URI;

	@AttributeDefinition(
		name = "Provisioning Agent URI",
		description = "Location for Equinox p2 provisioning agent data used by this instance."
	)
	String agentUri() default AGENT_URI;

	@AttributeDefinition(
		name = "Destination Name",
		description = "Repository name used for created metadata and artifact destinations."
	)
	String destinationName() default "mirror";

	@AttributeDefinition(
		name = "Raw Mirror",
		description = "Enable raw mirroring mode in the p2 mirror application."
	)
	boolean raw() default true;

	@AttributeDefinition(
		name = "Verbose Logging",
		description = "Enable verbose output from the p2 mirror application."
	)
	boolean verbose() default true;

	@AttributeDefinition(
		name = "Download Metadata Files",
		description = "Copy p2 metadata files like p2.index and content/artifacts descriptors after mirror run."
	)
	boolean downloadMetadata() default true;

	@AttributeDefinition(
		name = "Artifact Destination Compressed",
		description = "Compress artifact destination repository metadata."
	)
	boolean artifactCompressed() default true;

	@AttributeDefinition(
		name = "Metadata Destination Compressed",
		description = "Compress metadata destination repository metadata."
	)
	boolean metadataCompressed() default true;

	@AttributeDefinition(
		name = "Atomic Writes",
		description = "Use atomic repository update semantics where supported."
	)
	boolean atomic() default true;

	@AttributeDefinition(
		name = "Latest Version Only",
		description = "Mirror only the latest IU versions."
	)
	boolean latestVersionOnly() default false;

	@AttributeDefinition(
		name = "Strict Dependencies Only",
		description = "Consider only strict dependencies during slicing."
	)
	boolean strictDependenciesOnly() default false;

	@AttributeDefinition(
		name = "Follow Filtered Requirements Only",
		description = "Follow only filtered requirements during slicing."
	)
	boolean followFilteredRequirementsOnly() default false;

	@AttributeDefinition(
		name = "Include Optional Dependencies",
		description = "Include optional dependencies during slicing."
	)
	boolean includeOptionalDependencies() default false;
}
