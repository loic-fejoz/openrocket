/**
 * 
 */
package net.sf.openrocket.file.icesl;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.rocketcomponent.RocketComponentVisitor;

/**
 * @author Loïc Fejoz <loic@fejoz.net>
 *
 */
public class TreeDisplayerComponentVisitor implements RocketComponentVisitor<Writer> {
	
	protected final Writer w;
	protected int level = 0;

	public TreeDisplayerComponentVisitor() {
		this(new StringWriter());
	}
	
	public TreeDisplayerComponentVisitor(final Writer output) {
		w = output;
	}

	@Override
	public void visit(final RocketComponent component) {
		try {
			for(int i=0; i < level; i++) {
					w.write(' ');
			}
			w.write(component.toString());
			w.write('\n');
			level++;
			for(RocketComponent child: component.getChildren()) {
				child.accept(this);
			}
			level--;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getLuaIdentifier(final String value) {
		return value.replaceAll("\\w", "_");
	}
	
	@Override
	public Writer getResult() {
		return w;
	}
}
