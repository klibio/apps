package io.klib.app.p2.mirror.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import io.klib.app.p2.mirror.api.P2Mirror;

@Timeout(value = 15, unit = TimeUnit.MINUTES)
public class P2MirrorOsgiTest {

	private static final Logger LOG = Logger.getLogger(P2MirrorOsgiTest.class.getName());
	private static final String STARTUP_CHECKPOINT = "CHECKPOINT P2MirrorOsgiTest.setupClass entered";
	private static final Duration SERVICE_WAIT = Duration.ofSeconds(30);
	private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(60);
	private static final Duration DOWNLOAD_REQUEST_TIMEOUT = Duration.ofMinutes(5);
	private static final String FACTORY_PID = "io.klib.app.p2.mirror.service.P2Mirror";
	private static final String ARCHIVE_URL = "https://github.com/klibio/example.pde.rcp/releases/download/latest-main/repo.binary.zip";
	private static final Path TESTDATA_DIR = Paths.get(System.getProperty("user.dir"), "testdata");
	private static final Path ARCHIVE_PATH = TESTDATA_DIR.resolve("repo.binary.zip");

	private static URI preparedSourceRepoUri;

	private final List<Configuration> createdConfigurations = new ArrayList<>();

	@BeforeAll
	static void setupClass() throws Exception {
		LOG.info(STARTUP_CHECKPOINT);
		System.err.println(STARTUP_CHECKPOINT);
		System.err.flush();
		System.out.println(STARTUP_CHECKPOINT);
		System.out.flush();
		downloadArchiveIfMissing();
		preparedSourceRepoUri = createArchiveSourceUri();
	}

	@AfterEach
	void cleanupConfigurations() throws IOException {
		for (Configuration configuration : createdConfigurations) {
			if (configuration != null) {
				configuration.delete();
			}
		}
		createdConfigurations.clear();
	}

	@Test
	void mirrorsFileRepository() throws Exception {
		URI sourceRepo = findSourceRepoUri();
		assertJarFileSourceRepo(sourceRepo);
		Path mirrorRoot = Files.createTempDirectory("p2-mirror-file-");

		P2Mirror mirrorService = createMirrorService("file-instance", mirrorRoot);
		mirrorService.mirror(sourceRepo);

		assertTrue(containsFile(mirrorRoot, "p2.index"), "Expected mirrored metadata p2.index");
	}

	@Test
	void mirrorsWithMultipleFactoryInstancesConcurrently() throws Exception {
		URI sourceRepo = findSourceRepoUri();
		assertJarFileSourceRepo(sourceRepo);
		Path mirrorRootA = Files.createTempDirectory("p2-mirror-multi-a-");
		Path mirrorRootB = Files.createTempDirectory("p2-mirror-multi-b-");

		P2Mirror mirrorServiceA = createMirrorService("multi-a", mirrorRootA);
		P2Mirror mirrorServiceB = createMirrorService("multi-b", mirrorRootB);

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Void>> jobs = List.of(
				() -> {
					mirrorServiceA.mirror(sourceRepo);
					return null;
				},
				() -> {
					mirrorServiceB.mirror(sourceRepo);
					return null;
				}
			);
			List<Future<Void>> futures = executorService.invokeAll(jobs);
			for (Future<Void> future : futures) {
				future.get(2, TimeUnit.MINUTES);
			}
		} finally {
			executorService.shutdownNow();
		}

		assertTrue(containsFile(mirrorRootA, "p2.index"), "Expected mirrored metadata p2.index in instance A");
		assertTrue(containsFile(mirrorRootB, "p2.index"), "Expected mirrored metadata p2.index in instance B");
	}

	private P2Mirror createMirrorService(String destinationName, Path mirrorRoot) throws Exception {
		BundleContext bundleContext = bundleContext();
		ConfigurationAdmin configurationAdmin = getService(bundleContext, ConfigurationAdmin.class);

		Configuration configuration = configurationAdmin.createFactoryConfiguration(FACTORY_PID, "?");
		Dictionary<String, Object> properties = new Hashtable<>();
		properties.put("repoRootUri", mirrorRoot.toUri().toString());
		properties.put("destinationName", destinationName);
		properties.put("agentUri", mirrorRoot.resolve("agent").toUri().toString());
		properties.put("downloadMetadata", Boolean.TRUE);
		properties.put("verbose", Boolean.FALSE);
		configuration.update(properties);
		createdConfigurations.add(configuration);

		return waitForMirrorService(bundleContext, destinationName, SERVICE_WAIT);
	}

	private BundleContext bundleContext() {
		Bundle bundle = FrameworkUtil.getBundle(getClass());
		assertNotNull(bundle, "Test must run in OSGi framework");
		return bundle.getBundleContext();
	}

	private <T> T getService(BundleContext context, Class<T> type) {
		ServiceReference<T> reference = context.getServiceReference(type);
		assertNotNull(reference, "Missing required service " + type.getName());
		T service = context.getService(reference);
		assertNotNull(service, "Service unavailable for " + type.getName());
		return service;
	}

	private P2Mirror waitForMirrorService(BundleContext context, String destinationName, Duration timeout)
		throws InterruptedException, InvalidSyntaxException {
		String filter = "(destinationName=" + destinationName + ")";
		long timeoutAt = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			ServiceReference<P2Mirror> reference = context.getServiceReferences(P2Mirror.class, filter)
				.stream()
				.findFirst()
				.orElse(null);
			if (reference != null) {
				P2Mirror service = context.getService(reference);
				if (service != null) {
					return service;
				}
			}
			Thread.sleep(200);
		}
		throw new IllegalStateException("Timed out waiting for P2Mirror service with destinationName=" + destinationName);
	}

	private boolean containsFile(Path root, String fileName) throws IOException {
		try (Stream<Path> stream = Files.walk(root)) {
			return stream.anyMatch(path -> path.getFileName().toString().equals(fileName));
		}
	}

	private void assertJarFileSourceRepo(URI sourceRepo) {
		assertTrue("jar".equalsIgnoreCase(sourceRepo.getScheme()), "Expected jar:file source URI but got " + sourceRepo);
		assertTrue(sourceRepo.toString().startsWith("jar:file:"), "Expected jar:file source URI but got " + sourceRepo);
		assertTrue(sourceRepo.toString().endsWith("!/"), "Expected archive root URI ending with !/ but got " + sourceRepo);
	}

	private URI findSourceRepoUri() {
		if (preparedSourceRepoUri != null) {
			return preparedSourceRepoUri;
		}
		throw new IllegalStateException("Cannot determine prepared jar:file source URI for " + ARCHIVE_PATH);
	}

	private static void downloadArchiveIfMissing() throws IOException, InterruptedException {
		if (Files.exists(ARCHIVE_PATH)) {
			return;
		}

		Files.createDirectories(TESTDATA_DIR);

		String githubBearer = System.getenv("GITHUB_BEARER");
		if (githubBearer == null || githubBearer.isBlank()) {
			githubBearer = System.getProperty("GITHUB_BEARER");
		}
		if (githubBearer == null || githubBearer.isBlank()) {
			fail("Missing GITHUB_BEARER. Provide it as an environment variable or JVM system property (-DGITHUB_BEARER=...) and retry:\n\n"
				+ "ALTERNATIVELY download via curl int testdata folder\n"
				+ "export GITHUB_BEARER=<YOUR_GITHUB__BEARER_TOKEN>\n"
				+ "curl -L \\\n"
				+ "  -H \"Accept: application/vnd.github+json\" \\\n"
				+ "  -H \"Authorization: Bearer $GITHUB_BEARER\" \\\n"
				+ "  " + ARCHIVE_URL);
		}

		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(DOWNLOAD_CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(ARCHIVE_URL))
			.header("Accept", "application/vnd.github+json")
			.header("Authorization", "Bearer " + githubBearer)
			.timeout(DOWNLOAD_REQUEST_TIMEOUT)
			.GET()
			.build();

		Path temporaryArchive = ARCHIVE_PATH.resolveSibling(ARCHIVE_PATH.getFileName() + ".part");
		HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporaryArchive));
		if (response.statusCode() >= 400) {
			Files.deleteIfExists(temporaryArchive);
			fail("Unable to download test archive from " + ARCHIVE_URL + " (HTTP " + response.statusCode() + ").");
		}

		Files.move(temporaryArchive, ARCHIVE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static URI createArchiveSourceUri() {
		return URI.create("jar:" + ARCHIVE_PATH.toAbsolutePath().toUri() + "!/");
	}
}
