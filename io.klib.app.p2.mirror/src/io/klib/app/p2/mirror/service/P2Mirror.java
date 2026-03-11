package io.klib.app.p2.mirror.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.internal.repository.tools.SlicingOptions;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = io.klib.app.p2.mirror.api.P2Mirror.class)
@Designate(ocd = P2MirrorConfig.class, factory = true)
public class P2Mirror implements io.klib.app.p2.mirror.api.P2Mirror {
	private static final String USER_DIR = System.getProperty("user.dir").replace("\\", "/");
	private static final String LOCAL_ROOT_URI = USER_DIR + "/repo";
	private static final String LOCAL_AGENT_URI = "file:/" + USER_DIR + "/p2";

	@Reference
	private IProvisioningAgentProvider agentProvider;

	private volatile IProvisioningAgent agent;
	private volatile ServiceRegistration<IProvisioningAgent> registration;
	private volatile P2MirrorConfig config;

	private static final Duration SERVICE_LOOKUP_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SERVICE_LOOKUP_RETRY_DELAY = Duration.ofMillis(250);

	private static final String[] METADATA_FILES = new String[] {
		"p2.index", "content.xml.xz", "content.jar", "artifacts.xml.xz", "artifacts.jar"
	};

	@Activate
	void activate(BundleContext context, P2MirrorConfig config) throws Exception {
		this.config = Objects.requireNonNull(config, "config");
		this.agent = agentProvider.createAgent(resolveAgentLocation(config));
		this.registration = context.registerService(IProvisioningAgent.class, agent, null);

		awaitAgentServiceSafely("org.eclipse.equinox.p2.repository.metadataRepositoryManager", Object.class);
		awaitAgentServiceSafely("org.eclipse.equinox.p2.repository.artifactRepositoryManager", Object.class);
	}

	@Deactivate
	void deactivate() {
		ServiceRegistration<IProvisioningAgent> localRegistration = registration;
		registration = null;
		if (localRegistration != null) {
			localRegistration.unregister();
		}

		IProvisioningAgent localAgent = agent;
		agent = null;
		if (localAgent != null) {
			localAgent.stop();
		}
	}

	@Override
	public void mirror(URI repo) throws Exception {
		if (repo == null) {
			throw new IllegalArgumentException("repo must not be null");
		}

		P2MirrorConfig localConfig = Objects.requireNonNull(config, "config");
		MirrorApplication mirrorApplication = new MirrorApplication();

		RepositoryDescriptor sourceDescriptor = new RepositoryDescriptor();
		sourceDescriptor.setLocation(repo);
		mirrorApplication.addSource(sourceDescriptor);

		String suffix = suffixFromSource(repo.toString());
		Path targetRoot = resolveRepoRootPath(localConfig);
		Path targetDirectory = targetRoot.resolve(suffix).normalize();
		Files.createDirectories(targetDirectory);

		RepositoryDescriptor metadataDestination = createTargetRepoDesc(targetDirectory, localConfig.destinationName(),
			RepositoryDescriptor.KIND_METADATA, localConfig.metadataCompressed(), localConfig.atomic());
		mirrorApplication.addDestination(metadataDestination);

		RepositoryDescriptor artifactDestination = createTargetRepoDesc(targetDirectory, localConfig.destinationName(),
			RepositoryDescriptor.KIND_ARTIFACT, localConfig.artifactCompressed(), localConfig.atomic());
		mirrorApplication.addDestination(artifactDestination);

		mirrorApplication.setRaw(localConfig.raw());
		mirrorApplication.setVerbose(localConfig.verbose());

		SlicingOptions slicingOptions = new SlicingOptions();
		slicingOptions.latestVersionOnly(localConfig.latestVersionOnly());
		slicingOptions.considerStrictDependencyOnly(localConfig.strictDependenciesOnly());
		slicingOptions.followOnlyFilteredRequirements(localConfig.followFilteredRequirementsOnly());
		slicingOptions.includeOptionalDependencies(localConfig.includeOptionalDependencies());
		mirrorApplication.setSlicingOptions(slicingOptions);

		mirrorApplication.run(new NullProgressMonitor());

		if (localConfig.downloadMetadata()) {
			downloadMetadata(repo, targetDirectory);
		}
	}

	private RepositoryDescriptor createTargetRepoDesc(Path targetLocation, String name, String kind, boolean compressed,
		boolean atomic) {
		RepositoryDescriptor destination = new RepositoryDescriptor();
		destination.setKind(kind);
		destination.setCompressed(compressed);
		destination.setLocation(targetLocation.toUri());
		destination.setName(name);
		destination.setAtomic(Boolean.toString(atomic));
		return destination;
	}

	@SuppressWarnings("unchecked")
	private <T> T awaitAgentService(String serviceName, Class<T> serviceType) {
		long timeoutAt = System.currentTimeMillis() + SERVICE_LOOKUP_TIMEOUT.toMillis();
		Object service = agent.getService(serviceName);

		while (service == null && System.currentTimeMillis() < timeoutAt) {
			try {
				Thread.sleep(SERVICE_LOOKUP_RETRY_DELAY.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for provisioning agent service: " + serviceName,
						e);
			}
			service = agent.getService(serviceName);
		}

		if (service == null) {
			throw new IllegalStateException("Provisioning agent service unavailable: " + serviceName + " after "
				+ SERVICE_LOOKUP_TIMEOUT.toSeconds() + "s");
		}

		if (!serviceType.isAssignableFrom(service.getClass())) {
			throw new IllegalStateException("Provisioning agent service '" + serviceName + "' is not of type "
					+ serviceType.getName() + ": " + service.getClass().getName());
		}

		return (T) service;
	}

	private <T> void awaitAgentServiceSafely(String serviceName, Class<T> serviceType) {
		try {
			awaitAgentService(serviceName, serviceType);
		} catch (IllegalStateException exception) {
			System.err.println("P2Mirror activation continuing without ready agent service " + serviceName + ": "
				+ exception.getMessage());
		}
	}

	private void downloadMetadata(URI sourceRepo, Path targetDirectory) throws IOException, URISyntaxException {
		for (String fileName : METADATA_FILES) {
			URI fileUri = resolveRepositoryFile(sourceRepo, fileName);
			Path destination = targetDirectory.resolve(fileName);
			if ("file".equalsIgnoreCase(fileUri.getScheme())) {
				Path source = Path.of(fileUri);
				if (Files.exists(source)) {
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				}
				continue;
			}

			if ("jar".equalsIgnoreCase(fileUri.getScheme())) {
				copyJarEntryIfPresent(fileUri, destination);
				continue;
			}

			if (isHttpUri(fileUri) && existsHttp(fileUri)) {
				URL url = fileUri.toURL();
				Files.copy(url.openStream(), destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private URI resolveRepositoryFile(URI sourceRepo, String fileName) {
		if ("jar".equalsIgnoreCase(sourceRepo.getScheme())) {
			return URI.create(sourceRepo.toString() + fileName);
		}
		return sourceRepo.resolve(fileName);
	}

	private void copyJarEntryIfPresent(URI fileUri, Path destination) throws IOException {
		try (var inputStream = fileUri.toURL().openStream()) {
			Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (FileNotFoundException exception) {
			// Optional metadata file not present in the source archive.
		}
	}

	private boolean isHttpUri(URI fileUri) {
		String scheme = fileUri.getScheme();
		return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	}

	private boolean existsHttp(URI fileUri) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) fileUri.toURL().openConnection();
		try {
			connection.setRequestMethod("HEAD");
			int responseCode = connection.getResponseCode();
			return responseCode == HttpURLConnection.HTTP_OK;
		} finally {
			connection.disconnect();
		}
	}

	private String suffixFromSource(String url) {
		String suffix;
		if (url.startsWith("file:")) {
			suffix = url.replaceFirst("file:", "").replaceFirst(":", "_");
		} else {
			suffix = url.replaceFirst(".*?:", "").replaceAll("//", "/");
		}
		while (suffix.startsWith("/")) {
			suffix = suffix.substring(1);
		}
		if (url.endsWith("!/")) {
			suffix = suffix.replaceAll(".jar!/", "_jar").replaceFirst(".*/", "");
		}
		return suffix;
	}

	private String resolveRepoRootUri(P2MirrorConfig config) {
		if (P2MirrorConfig.LOCAL_ROOT_URI.equals(config.repoRootUri())) {
			return LOCAL_ROOT_URI;
		}
		return config.repoRootUri();
	}

	private Path resolveRepoRootPath(P2MirrorConfig config) {
		return resolvePath(resolveRepoRootUri(config));
	}

	private String resolveAgentUri(P2MirrorConfig config) {
		if (P2MirrorConfig.AGENT_URI.equals(config.agentUri())) {
			return LOCAL_AGENT_URI;
		}
		return config.agentUri();
	}

	private URI resolveAgentLocation(P2MirrorConfig config) throws URISyntaxException {
		String agentUri = resolveAgentUri(config);
		if (looksLikeWindowsPath(agentUri)) {
			return Path.of(agentUri).toUri();
		}

		URI uri = new URI(agentUri);
		if (uri.getScheme() != null) {
			return uri;
		}

		return Path.of(agentUri).toUri();
	}

	private Path resolvePath(String value) {
		if (looksLikeWindowsPath(value)) {
			return Path.of(value);
		}

		try {
			URI uri = URI.create(value);
			if (uri.getScheme() != null) {
				return Path.of(uri);
			}
		} catch (IllegalArgumentException exception) {
			// Fall back to treating the value as a filesystem path.
		}

		return Path.of(value);
	}

	private boolean looksLikeWindowsPath(String value) {
		return value != null && value.matches("^[a-zA-Z]:[\\\\/].*");
	}
}
