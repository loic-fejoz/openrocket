/**
 * 
 */
package net.sf.openrocket.file.icesl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.document.StorageOptions;
import net.sf.openrocket.file.RocketSaver;

/**
 * @author loic
 *
 */
public class IceSLSaver extends RocketSaver {

	public IceSLSaver() {
	}
	
	@Override
	public void save(final OutputStream dest, final OpenRocketDocument doc, final StorageOptions options) throws IOException {
		final OutputStreamWriter w = new OutputStreamWriter(dest);
		final IceSLComponentVisitor exporter = new IceSLComponentVisitor(w);
		exporter.convert(doc.getRocket());
		w.close();
	}
	
	@Override
	public long estimateFileSize(OpenRocketDocument doc, StorageOptions options) {
		return 2000000;
	}
	
}
