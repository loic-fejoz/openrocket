package net.sf.openrocket.file.stl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
import net.sf.openrocket.rocketcomponent.Transition.Shape;
import net.sf.openrocket.startup.Application;

public class StlSaverTest {
	
	private static final String TMP_DIR = "./tmp/";
	
	private static Injector injector;
	
	public class StlOutputSteam {
		
		protected OutputStream w;
		protected OutputStream os;
		protected int triangle_count = 0;
		
		protected final float zero[] = { 0.f, 0.f, 0.f };
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
		
		public void writeFloat32(float val) throws IOException {
			int i = Float.floatToRawIntBits(val);
			w.write((byte) (i));
			w.write((byte) (i >> 8));
			w.write((byte) (i >> 16));
			w.write((byte) (i >> 24));
		}
		
		public void writeNumberOfTriangles(long i) throws IOException {
			writeUInt32(i);
		}
		
		public void writeVector(float x, float y, float z) throws IOException {
			writeFloat32(x);
			writeFloat32(y);
			writeFloat32(z);
		}
		
		public void writeVector(double x, double y, double z) throws IOException {
			writeVector((float) x, (float) y, (float) z);
		}
		
		public void writeTriangleAttribute() throws IOException {
			writeUInt16(0);
		}
		
		public void close() throws IOException {
			w.close();
		}
		
		public void writeTriangle(float[] n1, float[] p0, float[] p1, float[] p2) throws IOException {
			triangle_count++;
			writeVector(n1);
			writeVector(p0);
			writeVector(p1);
			writeVector(p2);
			writeTriangleAttribute();
		}
		
		public void writeTriangle(float[] p0, float[] p1, float[] p2) throws IOException {
			writeTriangle(zero, p0, p1, p2);
		}
		
		private void writeVector(float[] v) throws IOException {
			if (rotate) {
				writeVector(v[2], v[1], -v[0]);
			} else {
				writeVector(v[0], v[1], v[2]);
			}
		}
		
		public void writeSquare(float[] n, float[] p0, float[] p1, float[] p2, float[] p3) throws IOException {
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
			writeTriangle(zero, asFloat(p0), asFloat(p1), asFloat(p2));
		}
		
		private float[] asFloat(double[] v) {
			return new float[] { (float) v[0], (float) v[1], (float) v[2] };
		}
		
		public float[] rotated(double L, double angle_rad) {
			final float[] v = new float[] { 0f, (float) (L * Math.cos(angle_rad)), (float) (-L * Math.sin(angle_rad)) };
			return v;
		}
		
		public void writeSquare(float[] p1a, float[] p1b, float[] p2b, float[] p2a) throws IOException {
			writeSquare(zero, p1a, p1b, p2b, p2a);
		}
		
		public void writeOgive(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n)
				throws IOException {
			
			
			double y;
			
			final double angle_delta = Math.PI * 2 / n;
			
			writeProfile(shape_param, radius, length, 0, ogive_shape, n, angle_delta, false);
			writeProfile(shape_param, radius, length, thickness, ogive_shape, n, angle_delta, true);
			
			// Close end
			y = ogive_shape.getRadius(length, radius, length, shape_param);
			double y_int = y - thickness;
			float[] p1 = new float[] { (float) length, (float) y, 0f };
			float[] p1_int = new float[] { (float) length, (float) y_int, 0f };
			for (double angle_rad = angle_delta; angle_rad <= 2 * Math.PI; angle_rad += angle_delta) {
				float[] p2 = rotated(y, angle_rad);
				float[] p2_int = rotated(y_int, angle_rad);
				p2[0] = (float) length;
				p2_int[0] = (float) length;
				writeSquare(p1, p1_int, p2_int, p2);
				p1 = p2;
				p1_int = p2_int;
			}
		}
		
		private double writeProfile(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n, double angle_delta,
				final boolean inout)
				throws IOException {
			double x = 0.1f + thickness;
			final float[] p0 = { (float) thickness, 0f, 0f };
			// Generate the endpoint
			double y = ogive_shape.getRadius(x, radius, length, shape_param) - thickness;
			float[] p1 = { (float) x, (float) y, 0f };
			for (double angle_rad = angle_delta; angle_rad <= 2 * Math.PI; angle_rad += angle_delta) {
				float[] p2 = rotated(y, angle_rad);
				p2[0] = (float) x;
				if (inout) {
					writeTriangle(p0, p2, p1);
				} else {
					writeTriangle(p0, p1, p2);
				}
				p1 = p2;
			}
			// Generate layer by layer
			for (; x < length - 0.1; x += 0.1) {
				final double y1 = Math.abs(ogive_shape.getRadius(x, radius, length, shape_param) - thickness);
				final double y2 = Math.abs(ogive_shape.getRadius(x + 0.1, radius, length, shape_param) - thickness);
				float p1a[] = new float[] { (float) x, (float) y1, 0f };
				float p1b[] = { (float) x + 0.1f, (float) y2, 0f };
				for (double angle_rad = angle_delta; angle_rad <= 2 * Math.PI; angle_rad += angle_delta) {
					float[] p2a = rotated(y1, angle_rad);
					float[] p2b = rotated(y2, angle_rad);
					p2a[0] = (float) x;
					p2b[0] = (float) (x + 0.1);
					if (inout) {
						writeSquare(p1a, p2a, p2b, p1b);
					} else {
						writeSquare(p1a, p1b, p2b, p2a);
					}
					p1a = p2a;
					p1b = p2b;
				}
			}
			return angle_delta;
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
		
		final float x[] = { 1f, 0.f, 0f };
		final float mx[] = { -1f, 0.f, 0f };
		
		final float y[] = { 0f, 1f, 0.f };
		final float my[] = { 0f, -1f, 0f };
		
		final float z[] = { 0.f, 0.f, 1f };
		final float mz[] = { 0.f, 0.f, -1f };
		
		final float p0[] = { 0.f, 0.f, 0.f };
		final float px1[] = { 1.f, 0.f, 0f };
		final float py1[] = { 0.f, 1f, 0f };
		final float pxy1[] = { 1.f, 1f, 0f };
		
		final float pz1[] = { 0.f, 0.f, 1.f };
		final float px1z1[] = { 1.f, 0.f, 1f };
		final float py1z1[] = { 0.f, 1f, 1f };
		final float pxyz1[] = { 1.f, 1f, 1f };
		
		
		w.writeSquare(mz, p0, px1, pxy1, py1);
		w.writeSquare(z, pz1, px1z1, pxyz1, py1z1);
		w.writeSquare(x, px1, pxy1, pxyz1, px1z1);
		w.writeSquare(mx, p0, pz1, py1z1, py1);
		
		w.flush();
		w.close();
	}
	
	@Test
	public void testGenerateOgive() throws IOException {
		//OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v100();
		final FileOutputStream os = new FileOutputStream("/tmp/ogive.stl");
		final StlOutputSteam w = new StlOutputSteam(os);
		
		final double shape_param = 0.2;
		final double radius = 25;
		final double length = 6 * radius;
		final double thickness = 5;
		
		final Shape ogive_shape = net.sf.openrocket.rocketcomponent.Transition.Shape.POWER;
		w.writeHeader();
		final int n = 20;
		w.configureForUnknownTriangleCount();
		w.configureRotationFor3DPrint();
		w.writeOgive(shape_param, radius, length, thickness, ogive_shape, n);
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
