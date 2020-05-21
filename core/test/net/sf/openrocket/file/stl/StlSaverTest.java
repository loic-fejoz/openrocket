package net.sf.openrocket.file.stl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
	
	public class StlOutputSteam {
		
		protected OutputStream w;
		protected OutputStream os;
		protected int triangle_count = 0;
		
		protected final double zero[] = { 0.f, 0.f, 0.f };
		private boolean rotate = false;
		
		
		public StlOutputSteam(final OutputStream outputStream) throws IOException {
			w = outputStream;
			this.os = outputStream;
		}
		
		public StlOutputSteam(final String path) throws IOException {
			this(new FileOutputStream(path));
		}
		
		public void writeHeader() throws IOException {
			for (int i = 0; i < 80; i++) {
				writeUInt8((byte) 0x20);
			}
		}
		
		private void writeUInt8(byte i) throws IOException {
			w.write(i);
		}
		
		public void writeUInt16(int i) throws IOException {
			w.write(i & 0xFF);
			w.write((i >> 8) & 0xFF);
		}
		
		public void writeUInt32(long i) throws IOException {
			w.write((byte) (i));
			w.write((byte) (i >> 8));
			w.write((byte) (i >> 16));
			w.write((byte) (i >> 24));
		}
		
		public void writeFloat32(double val) throws IOException {
			int i = Float.floatToRawIntBits((float) val);
			w.write((byte) (i));
			w.write((byte) (i >> 8));
			w.write((byte) (i >> 16));
			w.write((byte) (i >> 24));
		}
		
		public void writeNumberOfTriangles(long i) throws IOException {
			writeUInt32(i);
		}
		
		public void writeVector(double x, double y, double z) throws IOException {
			writeFloat32(x);
			writeFloat32(y);
			writeFloat32(z);
		}
		
		public void writeTriangleAttribute() throws IOException {
			writeUInt16(0);
		}
		
		public void close() throws IOException {
			w.close();
		}
		
		public void writeTriangle(double[] n1, double[] p0, double[] p1, double[] p2) throws IOException {
			triangle_count++;
			writeVector(n1);
			writeVector(p0);
			writeVector(p1);
			writeVector(p2);
			writeTriangleAttribute();
		}
		
		private void writeVector(double[] v) throws IOException {
			if (rotate) {
				writeVector(v[2], v[1], -v[0]);
			} else {
				writeVector(v[0], v[1], v[2]);
			}
		}
		
		public void writeSquare(double[] n, double[] p0, double[] p1, double[] p2, double[] p3) throws IOException {
			writeTriangle(n, p0, p1, p3);
			writeTriangle(n, p3, p1, p2);
		}
		
		public void flush() throws IOException {
			if (w != os) {
				final ByteArrayOutputStream facets = (ByteArrayOutputStream) w;
				w = os;
				writeNumberOfTriangles(triangle_count);
				w.write(facets.toByteArray());
			}
			os.flush();
		}
		
		public void configureForUnknownTriangleCount() {
			w = new ByteArrayOutputStream();
		}
		
		public void writeTriangle(double[] p0, double[] p1, double[] p2) throws IOException {
			writeTriangle(zero, p0, p1, p2);
		}
		
		public double[] rotated(double L, double angle_rad) {
			final double[] v = new double[] { 0f, (double) (L * Math.cos(angle_rad)), (double) (-L * Math.sin(angle_rad)) };
			return v;
		}
		
		public void writeSquare(double[] p1a, double[] p1b, double[] p2b, double[] p2a) throws IOException {
			writeSquare(zero, p1a, p1b, p2b, p2a);
		}
		
		public void writeNoseCone(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n)
				throws IOException {
			
			final double delta_x = 5f;
			final double[][] outerEndPoints = writeProfile(shape_param, radius, length, 0, ogive_shape, n, delta_x, false);
			final double[][] innerEndPoints = writeProfile(shape_param, radius, length - thickness, thickness, ogive_shape, n, delta_x, true);
			assert (outerEndPoints.length == innerEndPoints.length) : "Expect same length: " + Integer.toString(outerEndPoints.length) + " vs " + Integer.toString(innerEndPoints.length);
			// Close end
			for (int i = 0; i < outerEndPoints.length; i++) {
				double[] p1 = outerEndPoints[i];
				double[] p1_int = innerEndPoints[i];
				double[] p2 = outerEndPoints[(i + 1) % outerEndPoints.length];
				double[] p2_int = innerEndPoints[(i + 1) % innerEndPoints.length];
				writeSquare(p1, p1_int, p2_int, p2);
			}
		}
		
		
		
		private double[][] writeProfile(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n,
				final double delta_x,
				final boolean inout)
				throws IOException {
			double x = delta_x;
			final double[] p0 = { (float) thickness, 0f, 0f };
			// Generate the tip endpoints
			double y = Math.max(0, ogive_shape.getRadius(Math.min(x, length), radius, length, shape_param) - thickness);
			double[] p1 = { (double) x, (double) y, 0f };
			for (int i = 0; i < n + 1; i++) {
				final double angle_rad = Math.PI * 2 * i / n;
				double[] p2 = rotated(y, angle_rad);
				p2[0] = (float) (x + thickness);
				if (inout) {
					writeTriangle(p0, p2, p1);
				} else {
					writeTriangle(p0, p1, p2);
				}
				p1 = p2;
			}
			/* Track the endPoints of the profile so as to be able to close the shape */
			final double[][] endPoints = new double[n + 1][3];
			// Generate layer by layer
			for (; x < length - delta_x; x += delta_x) {
				final double y1 = Math.max(0, ogive_shape.getRadius(x, radius, length, shape_param) - thickness);
				final double y2 = Math.max(0, ogive_shape.getRadius(x + delta_x, radius, length, shape_param) - thickness);
				double p1a[] = new double[] { x, y1, 0f };
				double p1b[] = { Math.min(x + delta_x, length), y2, 0f };
				for (int i = 0; i < n + 1; i++) {
					final double angle_rad = Math.PI * 2 * i / n;
					double[] p2a = rotated(y1, angle_rad);
					double[] p2b = rotated(y2, angle_rad);
					p2a[0] = x + thickness;
					p2b[0] = x + delta_x + thickness;
					if (inout) {
						writeSquare(p1a, p2a, p2b, p1b);
					} else {
						writeSquare(p1a, p1b, p2b, p2a);
					}
					endPoints[i] = p2b;
					p1a = p2a;
					p1b = p2b;
				}
			}
			return endPoints;
		}
		
		public void configureRotationFor3DPrint() {
			rotate = true;
		}
		
	}
	
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
				writeNoseCone(nose, samplename);
			} else {
				toVisit.addAll(aChild.getChildren());
			}
		}
	}
	
	private void writeNoseCone(NoseCone nose, final String samplename) throws IOException {
		final FileOutputStream os = new FileOutputStream("/tmp/nose-" + samplename + ".stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		final double radius = nose.getAftRadius();
		final double length = nose.getLength();
		final double thickness = nose.getAftShoulderThickness();
		
		w.writeHeader();
		final int n = 20;
		w.configureForUnknownTriangleCount();
		w.configureRotationFor3DPrint();
		w.writeNoseCone(
				nose.getShapeParameter(),
				radius,
				length,
				thickness,
				nose.getType(),
				n);
		w.flush();
		w.close();
	}
	
	private void writeNoseCone(final double shape_param, final Shape ogive_shape) throws FileNotFoundException, IOException {
		final FileOutputStream os = new FileOutputStream("/tmp/ogive-" + ogive_shape.name() + ".stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		final double radius = 25;
		final double length = 6 * radius;
		final double thickness = 5;
		
		w.writeHeader();
		final int n = 20;
		w.configureForUnknownTriangleCount();
		w.configureRotationFor3DPrint();
		w.writeNoseCone(shape_param, radius, length, thickness, ogive_shape, n);
		w.flush();
		w.close();
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
