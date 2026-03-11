package io.klib.app.p2.mirror.api;

import java.net.URI;

public interface P2Mirror {

	void mirror(URI repo) throws Exception;
}
