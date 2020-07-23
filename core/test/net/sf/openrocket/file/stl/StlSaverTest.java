package net.sf.openrocket.file.stl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.util.Modules;

import net.sf.openrocket.ServicesForTesting;
import net.sf.openrocket.database.ComponentPresetDao;
import net.sf.openrocket.database.ComponentPresetDatabase;
import net.sf.openrocket.database.motor.MotorDatabase;
import net.sf.openrocket.database.motor.ThrustCurveMotorSetDatabase;
import net.sf.openrocket.file.motor.GeneralMotorLoader;
import net.sf.openrocket.l10n.DebugTranslator;
import net.sf.openrocket.l10n.Translator;
import net.sf.openrocket.motor.Motor;
import net.sf.openrocket.motor.ThrustCurveMotor;
import net.sf.openrocket.plugin.PluginModule;
import net.sf.openrocket.rocketcomponent.NoseCone;
import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.rocketcomponent.Transition.Shape;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.TestRockets;

public class StlSaverTest {
	
	private static Injector injector;
	
	@BeforeClass
	public static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		
		Module dbOverrides = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ComponentPresetDao.class).toProvider(new EmptyComponentDbProvider());
				bind(MotorDatabase.class).toProvider(new MotorDbProvider());
				bind(Translator.class).toInstance(new DebugTranslator(null));
			}
		};
		
		injector = Guice.createInjector(Modules.override(applicationModule).with(dbOverrides), pluginModule);
		Application.setInjector(injector);
		
		File tmpDir = new File("./tmp");
		if (!tmpDir.exists()) {
			boolean success = tmpDir.mkdirs();
			if (!success) {
				fail("Unable to create core/tmp dir needed for tests.");
			}
		}
	}
	
	@Test
	public void testGenerateStl() throws IOException {
		//OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v100();
		final FileOutputStream os = new FileOutputStream("/tmp/test.stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		w.writeHeader();
		w.writeNumberOfTriangles(8);
		
		final double x[] = { 1f, 0.f, 0f };
		final double mx[] = { -1f, 0.f, 0f };
		
		final double z[] = { 0.f, 0.f, 1f };
		final double mz[] = { 0.f, 0.f, -1f };
		
		final double p0[] = { 0.f, 0.f, 0.f };
		final double px1[] = { 1.f, 0.f, 0f };
		final double py1[] = { 0.f, 1f, 0f };
		final double pxy1[] = { 1.f, 1f, 0f };
		
		final double pz1[] = { 0.f, 0.f, 1.f };
		final double px1z1[] = { 1.f, 0.f, 1f };
		final double py1z1[] = { 0.f, 1f, 1f };
		final double pxyz1[] = { 1.f, 1f, 1f };
		
		
		w.writeSquare(mz, p0, px1, pxy1, py1);
		w.writeSquare(z, pz1, px1z1, pxyz1, py1z1);
		w.writeSquare(x, px1, pxy1, pxyz1, px1z1);
		w.writeSquare(mx, p0, pz1, py1z1, py1);
		
		w.flush();
		w.close();
	}
	
	@Test
	public void testGeneratePowerNoseCone() throws IOException {
		final double shape_param = 0.2;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.POWER;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	@Test
	public void testGenerateConicalNoseCone() throws IOException {
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.CONICAL;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	@Test
	public void testGenerateEllipsoidNoseCone() throws IOException {
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.ELLIPSOID;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	@Test
	public void testGenerateHaackConicalNoseCone() throws IOException {
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.HAACK;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	
	@Test
	public void testGenerateNoseCone() throws IOException {
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.OGIVE;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	@Test
	public void testGenerateParabolicNoseCone() throws IOException {
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.PARABOLIC;
		writeNoseCone(shape_param, ogive_shape);
	}
	
	@Test
	public void testGenerateHaackConicalNoseConeWithAftShoulderSmaller() throws IOException {
		final double mm = 1.0 / 1000.0;
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.HAACK;
		final double radius = 25 * mm;
		final double length = 6 * radius;
		final double thickness = 5 * mm;
		
		final NoseCone nose = new NoseCone();
		nose.setType(ogive_shape);
		nose.setAftRadius(radius);
		nose.setLength(length);
		nose.setThickness(thickness);
		nose.setShapeParameter(shape_param);
		nose.setAftShoulderLength(radius);
		nose.setAftShoulderRadius(radius - thickness / 2);
		nose.setAftShoulderThickness(1);
		nose.setName("haack-with-smaller-aft-shoulder");
		
		exportNoseCone(nose, nose.getName());
	}
	
	@Test
	public void testGenerateHaackConicalNoseConeWithAftShoulderBigger() throws IOException {
		final double mm = 1.0 / 1000.0;
		final double shape_param = 1.0;
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.HAACK;
		final double radius = 25 * mm;
		final double length = 6 * radius;
		final double thickness = 5 * mm;
		
		final NoseCone nose = new NoseCone();
		nose.setType(ogive_shape);
		nose.setAftRadius(radius);
		nose.setLength(length);
		nose.setThickness(thickness);
		nose.setShapeParameter(shape_param);
		nose.setAftShoulderLength(radius);
		nose.setAftShoulderRadius(radius - thickness * 2 / 3);
		nose.setAftShoulderThickness(thickness);
		nose.setName("haack-with-bigger-aft-shoulder");
		
		exportNoseCone(nose, nose.getName());
	}
	
	
	@Test
	public void testGenerateRocketV100NoseCone() throws IOException {
		writeNose(new TestRockets("ork2stl").makeTestRocket(), "testrocket");
	}
	
	@Test
	public void testGenerateRocketSmallFlyable() throws IOException {
		writeNose(TestRockets.makeSmallFlyable(), "small-flyable");
	}
	
	@Test
	public void testGenerateRocketBigBlue() throws IOException {
		writeNose(TestRockets.makeBigBlue(), "big-blue");
	}
	
	@Test
	public void testGenerateRocketIsoHaisu() throws IOException {
		writeNose(TestRockets.makeIsoHaisu(), "iso-haisu");
	}
	
	private void writeNose(final RocketComponent root, final String samplename) throws IOException {
		final Stack<RocketComponent> toVisit = new Stack<>();
		toVisit.push(root);
		while (!toVisit.isEmpty()) {
			final RocketComponent aChild = toVisit.pop();
			if (aChild instanceof NoseCone) {
				final NoseCone nose = (NoseCone) aChild;
				exportNoseCone(nose, samplename);
			} else {
				toVisit.addAll(aChild.getChildren());
			}
		}
	}
	
	private void exportNoseCone(NoseCone nose, final String samplename) throws IOException {
		final FileOutputStream os = new FileOutputStream("/tmp/nose-" + samplename + ".stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		final int n = 180;
		
		w.exportNoseCone(nose, n);
	}
	
	private void writeNoseCone(final double shape_param, final Shape ogive_shape) throws FileNotFoundException, IOException {
		final FileOutputStream os = new FileOutputStream("/tmp/ogive-" + ogive_shape.name() + ".stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		final double radius = 25;
		final double length = 6 * radius;
		final double thickness = 5;
		
		final NoseCone nose = new NoseCone();
		nose.setType(ogive_shape);
		nose.setAftRadius(radius);
		nose.setLength(length);
		nose.setThickness(thickness);
		nose.setShapeParameter(shape_param);
		
		w.writeHeader();
		final int n = 20;
		w.configureForUnknownTriangleCount();
		w.configureRotationFor3DPrint();
		w.writeNoseCone(nose, n);
		w.flush();
		w.close();
	}
	
	public static interface DistanceFunction {
		double call(double[] p);
	}
	
	public static interface NormalFunction {
		double[] call(double[] p);
	}
	
	private int getIndexOf(int span, int i_x, int i_y, int i_z) {
		return i_x + (span * i_y) + (span * span * i_z);
	}
	
	@Test
	public void testDualContouring() throws IOException {
		
		final DistanceFunction circle_function = new DistanceFunction() {
			
			@Override
			public double call(double[] p) {
				return 2.5 - Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
			}
			
		};
		
		final NormalFunction circle_normal = new NormalFunction() {
			
			@Override
			public double[] call(double[] p) {
				double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
				return new double[] { -p[0] / l, -p[1] / l, -p[2] / l };
			}
			
		};
		
		
		final FileOutputStream os = new FileOutputStream("/tmp/dual-contouring.stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		w.writeHeader();
		w.configureForUnknownTriangleCount();
		
		double xmin = -5;
		double xmax = 5;
		double ymin = -5;
		double ymax = 5;
		double zmin = -5;
		double zmax = 5;
		
		final Map<Integer, double[]> vert_indices = new HashMap<>(10000);
		final int span = 100;
		final double x_cell_size = (xmax - xmin) / span;
		final double y_cell_size = (ymax - ymin) / span;
		final double z_cell_size = (zmax - zmin) / span;
		for (int i_x = 0; i_x < span; i_x++) {
			double x = xmin + ((xmax - xmin) / span) * i_x;
			for (int i_y = 0; i_y < span; i_y++) {
				double y = ymin + ((ymax - ymin) / span) * i_y;
				for (int i_z = 0; i_z < span; i_z++) {
					double z = zmin + ((zmax - zmin) / span) * i_z;
					// Not adaptative version
					double vert[] = new double[] { x + x_cell_size / 2.0, y + y_cell_size / 2.0, z + z_cell_size / 2 };
					
					if (true) {
						// Adaptive version
						double vertxyz[] = new double[] { x + x_cell_size, y + y_cell_size, z + z_cell_size };
						double vertxy[] = new double[] { x + x_cell_size, y + y_cell_size, z };
						double vertx[] = new double[] { x + x_cell_size, y, z };
						double vertxz[] = new double[] { x + x_cell_size, y, z + z_cell_size };
						double vertyz[] = new double[] { x, y + y_cell_size, z + z_cell_size };
						double verty[] = new double[] { x, y + y_cell_size, z };
						double vertz[] = new double[] { x, y, z + z_cell_size };
						vert = new double[] { x, y, z };
						
						final boolean d = circle_function.call(vert) > 0.0;
						final boolean dxyz = circle_function.call(vertxyz) > 0.0;
						final boolean dxy = circle_function.call(vertxy) > 0.0;
						final boolean dx = circle_function.call(vertx) > 0.0;
						final boolean dxz = circle_function.call(vertxz) > 0.0;
						final boolean dyz = circle_function.call(vertyz) > 0.0;
						final boolean dy = circle_function.call(verty) > 0.0;
						final boolean dz = circle_function.call(vertz) > 0.0;
						
						/*boolean changeX = false;
						if ((d != dx) || (dz != dxz) || (dyz != dxyz) || (dy != dxy)) {
							changeX = true;
						}
						boolean changeY = false;
						if ((d != dy) || (dx != dxy) || (dz != dyz) || (dxz != dxyz)) {
							changeY = true;
						}
						boolean changeZ = false;
						if ((d != dz) || (dx != dxz) || (dy != dyz) || (dxy != dxyz)) {
							changeZ = true;
						}*/
						final double precision = 0.0001;
						if (d != dxyz) {
							vert = findByDichotomy(precision, circle_function, vert, vertxyz);
						} else if (d != dxz) {
							vert = findByDichotomy(precision, circle_function, vert, vertxz);
						} else if (d != dxy) {
							vert = findByDichotomy(precision, circle_function, vert, vertxy);
						} else if (d != dyz) {
							vert = findByDichotomy(precision, circle_function, vert, vertyz);
						} else if (d != dxz) {
							vert = findByDichotomy(precision, circle_function, vert, vertxz);
						} else if (d != dx) {
							vert = findByDichotomy(precision, circle_function, vert, vertx);
						} else if (d != dy) {
							vert = findByDichotomy(precision, circle_function, vert, verty);
						} else if (d != dz) {
							vert = findByDichotomy(precision, circle_function, vert, vertz);
						}
						
					}
					
					//
					vert_indices.put(getIndexOf(span, i_x, i_y, i_z), vert);
				}
			}
		}
		double[] p = new double[] { 0.0, 0.0, 0.0 };
		double[] p_adjacent = new double[] { 0.0, 0.0, 0.0 };
		for (int i_x = 0; i_x < span; i_x++) {
			double x = xmin + ((xmax - xmin) / span) * i_x;
			p[0] = x;
			for (int i_y = 0; i_y < span; i_y++) {
				double y = ymin + ((ymax - ymin) / span) * i_y;
				p[1] = y;
				for (int i_z = 0; i_z < span; i_z++) {
					double z = zmin + ((zmax - zmin) / span) * i_z;
					p[2] = z;
					
					p_adjacent[0] = p[0];
					p_adjacent[1] = p[1];
					p_adjacent[2] = p[2];
					
					final boolean solid1 = circle_function.call(p) > 0.0;
					
					if (x > xmin && y > ymin) {
						p_adjacent[2] = p[2] + z_cell_size;
						final boolean solid2 = circle_function.call(p_adjacent) > 0.0;
						if (solid1 != solid2) {
							
							double[] p1a = vert_indices.get(getIndexOf(span, i_x - 1, i_y - 1, i_z));
							double[] p1b = vert_indices.get(getIndexOf(span, i_x - 0, i_y - 1, i_z));
							double[] p2b = vert_indices.get(getIndexOf(span, i_x - 0, i_y - 0, i_z));
							double[] p2a = vert_indices.get(getIndexOf(span, i_x - 1, i_y - 0, i_z));
							w.writeSquare(p1a, p1b, p2b, p2a);
							//todo swap if solid2
						}
						// reset
						p_adjacent[2] = p[2];
					}
					
					if (x > xmin && z > zmin) {
						p_adjacent[1] = p[1] + y_cell_size;
						final boolean solid2 = circle_function.call(p_adjacent) > 0.0;
						if (solid1 != solid2) {
							
							double[] p1a = vert_indices.get(getIndexOf(span, i_x - 1, i_y, i_z - 1));
							double[] p1b = vert_indices.get(getIndexOf(span, i_x - 0, i_y, i_z - 1));
							double[] p2b = vert_indices.get(getIndexOf(span, i_x - 0, i_y, i_z));
							double[] p2a = vert_indices.get(getIndexOf(span, i_x - 1, i_y, i_z));
							w.writeSquare(p1a, p1b, p2b, p2a);
							//todo swap if solid2
						}
						// reset
						p_adjacent[1] = p[1];
					}
					
					if (y > ymin && z > zmin) {
						p_adjacent[0] = p[0] + x_cell_size;
						final boolean solid2 = circle_function.call(p_adjacent) > 0.0;
						if (solid1 != solid2) {
							
							double[] p1a = vert_indices.get(getIndexOf(span, i_x, i_y - 1, i_z - 1));
							double[] p1b = vert_indices.get(getIndexOf(span, i_x, i_y - 0, i_z - 1));
							double[] p2b = vert_indices.get(getIndexOf(span, i_x, i_y - 0, i_z));
							double[] p2a = vert_indices.get(getIndexOf(span, i_x, i_y - 1, i_z));
							w.writeSquare(p1a, p1b, p2b, p2a);
							//todo swap if solid2
						}
						// reset
						p_adjacent[2] = p[2];
					}
				}
			}
		}
		w.flush();
		w.close();
	}
	
	private double[] findByDichotomy(
			final double precision,
			final DistanceFunction circle_function,
			double[] p1,
			double[] p2) {
		boolean p1In = circle_function.call(p1) > 0.0;
		boolean p2In = circle_function.call(p2) > 0.0;
		assert (p1In != p2In);
		while (dist2(p1, p2) > precision * precision) {
			double newP[] = middle(p1, p2);
			boolean newPIn = circle_function.call(newP) > 0.0;
			if (newPIn == p1In) {
				p1 = newP;
			} else {
				p2 = newP;
			}
		}
		return middle(p1, p2);
	}
	
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.0 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion100() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v100();
	//		assertEquals(100, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.1 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion101_withFinTabs() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v101_withFinTabs();
	//		assertEquals(101, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion101_withTubeCouplerChild() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v101_withTubeCouplerChild();
	//		assertEquals(101, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.2 // 
	//	////////////////////////////////
	//	
	//	// no version 1.2 file type exists
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.3 // 
	//	////////////////////////////////
	//	
	//	// no version 1.3 file type exists
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.4 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion104_withSimulationData() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v104_withSimulationData();
	//		assertEquals(104, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion104_withMotor() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v104_withMotor();
	//		assertEquals(104, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.5 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion105_withComponentPresets() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v105_withComponentPreset();
	//		assertEquals(105, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion105_withCustomExpressions() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v105_withCustomExpression();
	//		assertEquals(105, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion105_withLowerStageRecoveryDevice() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v105_withLowerStageRecoveryDevice();
	//		assertEquals(105, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.6 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion106_withAppearance() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v106_withAppearance();
	//		assertEquals(106, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion106_withMotorMountIgnitionConfig() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v106_withMotorMountIgnitionConfig();
	//		assertEquals(106, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion106_withRecoveryDeviceDeploymentConfig() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v106_withRecoveryDeviceDeploymentConfig();
	//		assertEquals(106, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	@Test
	//	public void testFileVersion106_withStageDeploymentConfig() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v106_withStageSeparationConfig();
	//		assertEquals(106, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	////////////////////////////////
	//	// Tests for File Version 1.7 // 
	//	////////////////////////////////
	//	
	//	@Test
	//	public void testFileVersion107_withSimulationExtension() {
	//		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v107_withSimulationExtension(SIMULATION_EXTENSION_SCRIPT);
	//		assertEquals(107, getCalculatedFileVersion(rocketDoc));
	//	}
	//	
	//	
	//	/*
	//	 * Utility Functions
	//	 */
	//	
	//	private int getCalculatedFileVersion(OpenRocketDocument rocketDoc) {
	//		int fileVersion = this.saver.testAccessor_calculateNecessaryFileVersion(rocketDoc, null);
	//		return fileVersion;
	//	}
	//	
	//	private OpenRocketDocument loadRocket(String fileName) {
	//		GeneralRocketLoader loader = new GeneralRocketLoader(new File(fileName));
	//		OpenRocketDocument rocketDoc = null;
	//		try {
	//			rocketDoc = loader.load();
	//		} catch (RocketLoadException e) {
	//			e.printStackTrace();
	//			fail("RocketLoadException while loading file " + fileName + " : " + e.getMessage());
	//		}
	//		return rocketDoc;
	//	}
	//	
	//	private File saveRocket(OpenRocketDocument rocketDoc, StorageOptions options) {
	//		String fileName = String.format(TMP_DIR + "%s_%s.ork", this.getClass().getName(), rocketDoc.getRocket().getName());
	//		File file = new File(fileName);
	//		
	//		OutputStream out = null;
	//		try {
	//			out = new FileOutputStream(file);
	//			this.saver.save(out, rocketDoc, options);
	//		} catch (FileNotFoundException e) {
	//			fail("FileNotFound saving file " + fileName + ": " + e.getMessage());
	//		} catch (IOException e) {
	//			fail("IOException saving file " + fileName + ": " + e.getMessage());
	//		}
	//		
	//		try {
	//			if (out != null) {
	//				out.close();
	//			}
	//		} catch (IOException e) {
	//			fail("Unable to close output stream for file " + fileName + ": " + e.getMessage());
	//		}
	//		
	//		return file;
	//	}
	//	
	
	private double dist2(double[] p1, double[] p2) {
		double acc = 0.0;
		for (int i = 0; i < 3; ++i) {
			double d = (p1[i] - p2[i]);
			acc += d * d;
		}
		return acc;
	}
	
	private double[] middle(double[] p1, double[] p2) {
		return new double[] {
				(p1[0] + p2[0]) / 2.0,
				(p1[1] + p2[1]) / 2.0,
				(p1[2] + p2[2]) / 2.0,
		};
	}
	
	private static ThrustCurveMotor readMotor() {
		GeneralMotorLoader loader = new GeneralMotorLoader();
		InputStream is = StlSaverTest.class.getResourceAsStream("/net/sf/openrocket/Estes_A8.rse");
		assertNotNull("Problem in unit test, cannot find Estes_A8.rse", is);
		try {
			for (Motor m : loader.load(is, "Estes_A8.rse")) {
				return (ThrustCurveMotor) m;
			}
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException: " + e);
		}
		throw new RuntimeException("Could not load motor");
	}
	
	private static class EmptyComponentDbProvider implements Provider<ComponentPresetDao> {
		
		final ComponentPresetDao db = new ComponentPresetDatabase();
		
		@Override
		public ComponentPresetDao get() {
			return db;
		}
	}
	
	private static class MotorDbProvider implements Provider<ThrustCurveMotorSetDatabase> {
		
		final ThrustCurveMotorSetDatabase db = new ThrustCurveMotorSetDatabase();
		
		public MotorDbProvider() {
			db.addMotor(readMotor());
			
			assertEquals(1, db.getMotorSets().size());
		}
		
		@Override
		public ThrustCurveMotorSetDatabase get() {
			return db;
		}
	}
	
	
}
