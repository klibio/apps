package io.klib.app.p2.mirror.gogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.klib.app.p2.mirror.api.P2Mirror;

class P2MirrorGogoCommandTest {

	@Test
	void mirrorStatusWithoutServiceInstanceShowsGuidance() {
		P2MirrorGogoCommand command = new P2MirrorGogoCommand();

		assertEquals(P2MirrorGogoCommand.NO_SERVICE_INSTANCE_MESSAGE, command.mirrorStatus());
	}

	@Test
	void mirrorStatusWithServiceInstanceShowsCount() {
		P2MirrorGogoCommand command = new P2MirrorGogoCommand();
		P2Mirror mirror = repo -> {
		};
		command.addMirror(mirror);

		assertEquals("P2Mirror service instances available: 1", command.mirrorStatus());
	}

	@Test
	void mirrorRepoWithoutServiceInstanceThrowsGuidanceMessage() {
		P2MirrorGogoCommand command = new P2MirrorGogoCommand();

		IllegalStateException error = assertThrows(IllegalStateException.class,
			() -> command.mirrorRepo(URI.create("https://example.com/repository")));

		assertEquals(P2MirrorGogoCommand.NO_SERVICE_INSTANCE_MESSAGE, error.getMessage());
	}
}
