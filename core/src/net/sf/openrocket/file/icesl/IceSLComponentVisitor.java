/**
 * 
 */
package net.sf.openrocket.file.icesl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.sf.openrocket.rocketcomponent.BodyComponent;
import net.sf.openrocket.rocketcomponent.BodyTube;
import net.sf.openrocket.rocketcomponent.CenteringRing;
import net.sf.openrocket.rocketcomponent.ExternalComponent;
import net.sf.openrocket.rocketcomponent.FinSet;
import net.sf.openrocket.rocketcomponent.InnerTube;
import net.sf.openrocket.rocketcomponent.InternalComponent;
import net.sf.openrocket.rocketcomponent.LaunchLug;
import net.sf.openrocket.rocketcomponent.NoseCone;
import net.sf.openrocket.rocketcomponent.Parachute;
import net.sf.openrocket.rocketcomponent.RadiusRingComponent;
import net.sf.openrocket.rocketcomponent.RingComponent;
import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.rocketcomponent.RocketComponentVisitor;
import net.sf.openrocket.rocketcomponent.ShockCord;
import net.sf.openrocket.rocketcomponent.StructuralComponent;
import net.sf.openrocket.rocketcomponent.SymmetricComponent;
import net.sf.openrocket.rocketcomponent.ThicknessRingComponent;
import net.sf.openrocket.rocketcomponent.Transition;
import net.sf.openrocket.rocketcomponent.TrapezoidFinSet;
import net.sf.openrocket.rocketcomponent.TubeCoupler;
import net.sf.openrocket.rocketcomponent.TubeFinSet;
import net.sf.openrocket.util.Coordinate;

/**
 * @author Loïc Fejoz <loic@fejoz.net>
 *
 */
public class IceSLComponentVisitor implements RocketComponentVisitor<Writer> {
	
	private static final int METER_TO_MILLIMETER = 1000;
	protected final Writer w;
	protected int level = 0;

	public IceSLComponentVisitor() {
		this(new StringWriter());
	}
	
	public IceSLComponentVisitor(final Writer output) {
		w = output;
	}
	
	public Writer convert(final RocketComponent component) {
		try {
			final InputStream is = this.getClass().getResourceAsStream("icesl-header.lua");
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			is.transferTo(os);
			w.write(os.toString());
			w.write("\n");
			component.accept(this);
			w.write("\nemit(");
			w.write(asLuaIdentifier(component));
			w.write("())\n");
			return w;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void visit(final RocketComponent component) {
		try {
			for(RocketComponent child: component.getChildren()) {
				child.accept(this);
			}
			w.write("function ");
			w.write(asLuaIdentifier(component));
			w.write("()\n");
			w.write("  -- expected mass of the section: ");
			w.write(Double.toString(component.getSectionMass()));
			w.write("\n");
			w.write("  -- actual java ");
			w.write(component.getClass().toString());
			w.write("\n");
			
			w.write("  return translate(");
			writeVector(component.getPosition());
			w.write(") * ");
			if (component.getChildren().isEmpty()) {
				final boolean t = doActualConversion(component);
				//assert(t);
				if (!t) {
					System.out.println("TODO: " + component.getClass().getName());
				}
				w.write("\n");
			} else {
				w.write("union{\n");
				final boolean t = doActualConversion(component);
				if (t) {
					w.write(",\n");
				}
				for(RocketComponent child: component.getChildren()) {
					w.write("    ");
					w.write(asLuaIdentifier(child));
					w.write("(),\n");
				}
				w.write("  }\n");
			}
			w.write("end\n\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean doActualConversion(RocketComponent component) {
		final String className = component.getClass().getSimpleName();
		try {
			final Method method = this.getClass().getMethod("visit" + className, component.getClass());
			method.invoke(this, component);
			return true;
		} catch (NoSuchMethodException e) {
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void visitBodyTube(BodyTube obj) throws IOException {
		w.write("BodyTube{\n");
		writeLength("outer_radius", obj.getOuterRadius());
		writeLength("inner_radius", obj.getInnerRadius());
		writeSymmetricComponentAttributes(obj);
		w.write("  }");
	}
	
	public void visitLaunchLug(LaunchLug obj) throws IOException {
		w.write("LaunchLug{\n");
		writeLength("outer_radius", obj.getOuterRadius());
		writeLength("inner_radius", obj.getInnerRadius());
		writeLength("thickness", obj.getThickness());
		writeAttribute("instance_count", obj.getInstanceCount());
		writeLength("instance_separation", obj.getInstanceSeparation());
		writeExternalComponentAttributes(obj);
		w.write("  }");
	}
	
	public void visitParachute(Parachute parachute) throws IOException {
//		parachute.get
		w.write("box(0)");
	}
	
	public void visitShockCord(ShockCord cord) throws IOException {
		w.write("box(0)");
	}
	
	public void visitInnerTube(InnerTube tube) throws IOException {
		w.write("InnerTube{\n");
		writeThicknessRingComponentAttributes(tube);
		w.write("  }");
	}
	
	public void visitTubeCoupler(TubeCoupler obj) throws IOException {
		w.write("TubeCoupler{\n");
		writeThicknessRingComponentAttributes(obj);
		w.write("  }");
	}
	
	public void visitTrapezoidFinSet(TrapezoidFinSet finset) throws IOException {
		w.write("TrapezoidFinSet{\n");
		writeFinSetAttributes(finset);
		writeLength("tip_chord", finset.getTipChord());
		writeLength("sweep", finset.getSweep());
		writeLength("height", finset.getHeight());
		writeLength("root_chord", finset.getRootChord());
		writeAttribute("position", finset.getPosition());
		writeLength("thickness", finset.getThickness());
		writeAttribute("fin_points", finset.getFinPoints());
		w.write("  }");
	}
	
	public void visitTubeFinSet(TubeFinSet finset) throws IOException {
		w.write("TubeFinSet{\n");
		writeExternalComponentAttributes(finset);
		w.write("  }");
	}
	
	public void visitTransition(Transition trans) throws IOException {
		final String parts[] = trans.getType().getName().split("\\.");
		final String typename = parts[parts.length-1].replace("]", "");
		w.write(typename);
		w.write("Transition{\n");
		writeTransitionAttributes(trans);
		w.write("  }");
	}

	public void visitNoseCone(NoseCone noseCone) throws IOException {
		final String parts[] = noseCone.getType().getName().split("\\.");
		final String typename = parts[parts.length-1].replace("]", "");
		w.write(typename);
		w.write("NoseCone{\n");
		writeTransitionAttributes(noseCone);
		w.write("  }");
	}
	
	public void visitCenteringRing(CenteringRing tube) throws IOException {
		w.write("CenteringRing{\n");
		writeRadiusRingComponentAttributes(tube);
		writeLength("length", tube.getLength());
		writeAttribute("position", tube.getPosition());
		w.write("  }");
	}
	
	private void writeRocketComponentAttributes(RocketComponent obj) throws IOException {
		writeLength("length", obj.getLength());
		writeAttribute("position", obj.getPosition());
		writeLength("axial_offset", obj.getAxialOffset());
	}
	
	private void writeExternalComponentAttributes(ExternalComponent obj) throws IOException {
		writeRocketComponentAttributes(obj);
	}
	
	private void writeBodyComponentAttributes(BodyComponent obj) throws IOException {
		writeExternalComponentAttributes(obj);
	}
	
	private void writeSymmetricComponentAttributes(SymmetricComponent obj) throws IOException {
		writeBodyComponentAttributes(obj);
	}
	
	private void writeTransitionAttributes(Transition trans) throws IOException {
		writeLength("thickness", trans.getThickness());
		writeAttribute("shape_param", trans.getShapeParameter());
		writeLength("fore_radius", trans.getForeRadius());
		writeLength("aft_radius", trans.getAftRadius());
		w.write("    aft_shoulder={\n");
		w.write("    ");
		writeLength("length", trans.getAftShoulderLength());
		w.write("    ");
		writeLength("radius", trans.getAftShoulderRadius());
		w.write("    ");
		writeLength("thickness", trans.getAftShoulderThickness());
		w.write("    ");
		writeAttribute("is_capped", trans.isAftShoulderCapped());
		w.write("    },\n");
		writeSymmetricComponentAttributes(trans);
	}
	
	private void writeFinSetAttributes(FinSet obj) throws IOException {
		writeAttribute("fin_count", obj.getFinCount());
		writeAttribute("cant_angle", obj.getCantAngle());
		writeLength("fillet_radius", obj.getFilletRadius());
		writeAttribute("cross_section", obj.getCrossSection().name());
		writeAttribute("tab_points", obj.getTabPoints());
		writeAttribute("root_points", obj.getRootPoints());
		writeLength("body_radius", obj.getBodyRadius());
		writeExternalComponentAttributes(obj);
	}
	
	private void writeInternalComponentAttributes(InternalComponent obj) throws IOException {
		writeRocketComponentAttributes(obj);
	}
	
	private void writeStructuralComponentAttributes(StructuralComponent obj) throws IOException {
		writeInternalComponentAttributes(obj);
	}	
	
	private void writeRingComponentAttributes(RingComponent obj) throws IOException {
		writeLength("outer_radius", obj.getOuterRadius());
		writeLength("inner_radius", obj.getInnerRadius());
		writeLength("thickness", obj.getThickness());
		writeStructuralComponentAttributes(obj);
	}
	
	private void writeRadiusRingComponentAttributes(RadiusRingComponent obj) throws IOException {
		writeRingComponentAttributes(obj);
	}
	
	private void writeThicknessRingComponentAttributes(ThicknessRingComponent obj) throws IOException {
		writeRingComponentAttributes(obj);
	}

	protected void writeAttribute(
			final String attrname,
			final double value) throws IOException {
		w.write("    ");
		w.write(attrname);
		w.write(" = ");
		w.write(Double.toString(value));
		w.write(",\n");
	}
	
	protected void writeLength(
			final String attrname,
			final double value) throws IOException {
		writeAttribute(attrname, METER_TO_MILLIMETER * value); // Convert m to mm	
	}
	
	protected void writeLength(final double value) throws IOException {
		w.write(Double.toString(value * METER_TO_MILLIMETER));
	}
	
	protected void writeAttribute(
			final String attrname,
			final boolean value) throws IOException {
		w.write("    ");
		w.write(attrname);
		w.write(" = ");
		w.write(Boolean.toString(value).toLowerCase());
		w.write(",\n");
	}
	
	protected void writeAttribute(
			final String attrname,
			final String value) throws IOException {
		w.write("    ");
		w.write(attrname);
		w.write(" = '");
		w.write(value);
		w.write("',\n");
	}
	
	protected void writeAttribute(
			final String attrname,
			final Coordinate value) throws IOException {
		w.write("    ");
		w.write(attrname);
		w.write(" = ");
		writeVector(value);
		w.write(",\n");
	}
	
	protected void writeVector(final Coordinate v) throws IOException {
		w.write("v(");
		writeLength(v.x);
		w.write(", ");
		writeLength(v.y);
		w.write(", ");
		writeLength(v.z);
		w.write(")");
	}
	
	protected void writeAttribute(
			final String attrname,
			final Coordinate[] values) throws IOException {
		w.write("    ");
		w.write(attrname);
		w.write(" = {");
		for(Coordinate v: values) {
			writeVector(v);
			w.write(",");
		}
		w.write("},\n");
	}

	public String asLuaIdentifier(final RocketComponent value) {
		return asLuaIdentifier(value.toString());
	}
	
	public String asLuaIdentifier(final String value) {
		return value.trim().replaceAll("\\W", "_");
	}
	
	@Override
	public Writer getResult() {
		return w;
	}
}
