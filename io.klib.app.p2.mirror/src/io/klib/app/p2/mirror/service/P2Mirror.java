package io.klib.app.p2.mirror.service;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.internal.repository.tools.SlicingOptions;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true)
public class P2Mirror {

	@Reference
	private IProvisioningAgentProvider agentProvider;

	private IProvisioningAgent agent;
	@SuppressWarnings("unused")
	private IArtifactRepositoryManager aRepoMgr;
	@SuppressWarnings("unused")
	private IMetadataRepositoryManager mRepoMgr;
	
	private static final Duration SERVICE_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration SERVICE_LOOKUP_RETRY_DELAY = Duration.ofMillis(250);

	@SuppressWarnings("unused")
	private static final String FEATURE_GROUP = ".feature.group";
	
	private static final String USER_DIR = System.getProperty("user.dir").replace("\\", "/");
	private static final String LOCAL_ROOT_URI = USER_DIR + "/repo";
	
	private String url = "https://bndtools.jfrog.io/artifactory/rel_7.2.1/";
	private String suffix = url;
	private String localUri;

	public void activate(BundleContext ctx) throws Exception {
		System.out.println("started");

		agent = agentProvider.createAgent(new URI("file:/" + USER_DIR + "/p2"));
		ctx.registerService(IProvisioningAgent.class, agent, null);

		mRepoMgr = getMetadataRepositoryManager();
		aRepoMgr = getArtifactRepositoryManager();

		MirrorApplication mirrorApplication = new MirrorApplication();
		RepositoryDescriptor srcRepoDesc = new RepositoryDescriptor();

		if (url.startsWith("file:")) {
			suffix = url.toString().replaceFirst("file:", "").replaceFirst(":", "_");
		} else {
			suffix = url.toString().replaceFirst(".*?:", "").replaceAll("//", "/");
		}
		if (url.toString().endsWith("!/")) {
			suffix = suffix.toString().replaceAll(".jar!/", "_jar").replaceFirst(".*/", "");
		}

		srcRepoDesc.setLocation(new URI(url));
		mirrorApplication.addSource(srcRepoDesc);

		localUri = LOCAL_ROOT_URI + suffix;
		File targetLocalStorage = new File(localUri);
		targetLocalStorage.mkdirs();
		String name = "x";
		// create metadata repository
		RepositoryDescriptor destMetadataRepoDesc = createTargetRepoDesc(targetLocalStorage, name,
				RepositoryDescriptor.KIND_METADATA);
		mirrorApplication.addDestination(destMetadataRepoDesc);

		// create artifact repository
		RepositoryDescriptor destArtifactRepoDesc = createTargetRepoDesc(targetLocalStorage, name,
				RepositoryDescriptor.KIND_ARTIFACT);
		destArtifactRepoDesc.setCompressed(true);
		destArtifactRepoDesc.setFormat(new URI("file:///Z:/ENGINE_LIB_DIR/cec/p2_repo_packedSiblings"));
		mirrorApplication.addDestination(destArtifactRepoDesc);
		mirrorApplication.setRaw(true);
		mirrorApplication.setVerbose(true);

		SlicingOptions sliceOpts = new SlicingOptions();
//		sliceOpts.latestVersionOnly(true);
//		sliceOpts.considerStrictDependencyOnly(false);
//		sliceOpts.followOnlyFilteredRequirements(false);
//		sliceOpts.includeOptionalDependencies(false);
//		sliceOpts.latestVersionOnly(false);
		mirrorApplication.setSlicingOptions(sliceOpts);

		/* 
		 * List<IInstallableUnit> ius = new ArrayList<IInstallableUnit>();
		 * InstallableUnit iu = new InstallableUnit();
		 * iu.setId("org.eclipse.nebula.widgets.paperclips.feature"+FEATURE_GROUP);
		 * iu.setVersion(Version.create("0.0.0")); ius.add(iu);
		 * 
		 * iu = new InstallableUnit();
		 * iu.setId("org.eclipse.nebula.paperclips.widgets");
		 * iu.setVersion(Version.create("0.0.0")); ius.add(iu);
		 * 
		 * mirrorApplication.setSourceIUs(ius);
		 */
		mirrorApplication.run(new NullProgressMonitor());
		
		downloadMetadata();

		System.out.println("finished");
	}

	private RepositoryDescriptor createTargetRepoDesc(final File targetLocation, final String name, final String kind) {
		RepositoryDescriptor destRepoDesc = new RepositoryDescriptor();
		destRepoDesc.setKind(kind);
		destRepoDesc.setCompressed(true);
		destRepoDesc.setLocation(targetLocation.toURI());
		destRepoDesc.setName(name);
		destRepoDesc.setAtomic("true"); // what is this for?^
		// destination.setFormat(sourceLocation); // can be used to define a target
		// format based on existing repository
		return destRepoDesc;
	}

	public IMetadataRepositoryManager getMetadataRepositoryManager() {
		IMetadataRepositoryManager repoMgr = awaitAgentService(IMetadataRepositoryManager.SERVICE_NAME,
				IMetadataRepositoryManager.class);

		if (repoMgr == null) {
			throw new IllegalStateException("Provisioning agent service unavailable: "
					+ IMetadataRepositoryManager.SERVICE_NAME + " after " + SERVICE_LOOKUP_TIMEOUT.toSeconds() + "s");
		}

		return repoMgr;
	}

	public IArtifactRepositoryManager getArtifactRepositoryManager() {
		IArtifactRepositoryManager repoMgr = awaitAgentService(IArtifactRepositoryManager.SERVICE_NAME,
				IArtifactRepositoryManager.class);

		if (repoMgr == null) {
			throw new IllegalStateException("Provisioning agent service unavailable: "
					+ IArtifactRepositoryManager.SERVICE_NAME + " after " + SERVICE_LOOKUP_TIMEOUT.toSeconds() + "s");
		}

		return repoMgr;
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
			return null;
		}

		if (!serviceType.isInstance(service)) {
			throw new IllegalStateException("Provisioning agent service '" + serviceName + "' is not of type "
					+ serviceType.getName() + ": " + service.getClass().getName());
		}

		return (T) service;
	}

	private void downloadMetadata() {
		String[] files = new String[] { "p2.index", "content.xml.xz", "content.jar", "artifacts.xml.xz",
				"artifacts.jar" };

		try {
			for (int i = 0; i < files.length; i++) {
				String fileUrl = url + files[i];
				URL url = new URI(fileUrl).toURL();
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("HEAD");

				int responseCode = connection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK) {
					// File exists, download it
					String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
					String filePath = localUri + fileName;

					Path destination = Path.of(filePath);
					Files.copy(url.openStream(), destination, StandardCopyOption.REPLACE_EXISTING);

					System.out.println("File downloaded successfully: " + filePath);
				} else {
					// File does not exist
					System.out.println("File not found: " + fileUrl);
				}
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public void addMeth() {
		System.out.println("hallo");
	}
}
