package io.klib.app.p2.mirror.gogo;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.klib.app.p2.mirror.api.P2Mirror;

@Component(
	service = Object.class,
	property = {
		"osgi.command.scope=p2mirror",
		"osgi.command.function=mirrorRepo",
		"osgi.command.function=mirrorStatus"
	}
)
public class P2MirrorGogoCommand {

	static final String NO_SERVICE_INSTANCE_MESSAGE = "No P2Mirror service instance available. Configure a factory instance first.";

	private final List<P2Mirror> mirrors = new CopyOnWriteArrayList<>();

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void addMirror(P2Mirror mirror) {
		mirrors.add(mirror);
	}

	void removeMirror(P2Mirror mirror) {
		mirrors.remove(mirror);
	}

	public void mirrorRepo(URI sourceURI) throws Exception {
		if (sourceURI == null) {
			throw new IllegalArgumentException("sourceURI must not be null");
		}
		if (mirrors.isEmpty()) {
			throw new IllegalStateException(NO_SERVICE_INSTANCE_MESSAGE);
		}
		mirrors.get(0).mirror(sourceURI);
	}

	public String mirrorStatus() {
		if (mirrors.isEmpty()) {
			return NO_SERVICE_INSTANCE_MESSAGE;
		}
		return "P2Mirror service instances available: " + mirrors.size();
	}
}
