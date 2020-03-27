/**
 * 
 */
package net.sf.openrocket.file.icesl;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.junit.Assert;
import org.junit.BeforeClass;

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
import net.sf.openrocket.l10n.DebugTranslator;
import net.sf.openrocket.l10n.Translator;
import net.sf.openrocket.motor.Manufacturer;
import net.sf.openrocket.motor.Motor;
import net.sf.openrocket.motor.ThrustCurveMotor;
import net.sf.openrocket.plugin.PluginModule;
import net.sf.openrocket.rocketcomponent.Rocket;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.Coordinate;
import net.sf.openrocket.util.TestRockets;
import net.sf.openrocket.util.BaseTestCase.BaseTestCase;

/**
 * @author Loïc Fejoz <loic@fejoz.net>
 *
 */
public class IceSLSaverTestCase extends BaseTestCase {
	
	private static final File TMP_DIR = new File("./tmp/");
	
	public static final String SIMULATION_EXTENSION_SCRIPT = "// Test <  &\n// >\n// <![CDATA[";
	
	private static Injector injector;
	
	private static class EmptyComponentDbProvider implements Provider<ComponentPresetDao> {
		
		final ComponentPresetDao db = new ComponentPresetDatabase();
		
		@Override
		public ComponentPresetDao get() {
			return db;
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
		
		if (!(TMP_DIR.exists() && TMP_DIR.isDirectory())) {
			boolean success = TMP_DIR.mkdirs();
			if (!success) {
				fail("Unable to create core/tmp dir needed for tests.");
			}
		}
	}
	
	private static class MotorDbProvider implements Provider<ThrustCurveMotorSetDatabase> {
		
		final ThrustCurveMotorSetDatabase db = new ThrustCurveMotorSetDatabase();
		
		public MotorDbProvider() {
			db.addMotor( new ThrustCurveMotor.Builder()
					.setManufacturer(Manufacturer.getManufacturer("A"))
					.setDesignation("F12X")
					.setDescription("Desc")
					.setMotorType(Motor.Type.UNKNOWN)
					.setStandardDelays(new double[] {})
					.setDiameter(0.024)
					.setLength(0.07)
					.setTimePoints(new double[] { 0, 1, 2 })
					.setThrustPoints(new double[] { 0, 1, 0 })
					.setCGPoints(new Coordinate[] { Coordinate.NUL, Coordinate.NUL, Coordinate.NUL })
					.setDigest("digestA")
					.build());

		}
		
		@Override
		public ThrustCurveMotorSetDatabase get() {
			return db;
		}
	}
	
	@org.junit.Test
	public void testConvertRocket_v100() throws Exception {
		final Rocket rocket = TestRockets.makeTestRocket_v100().getRocket();
		Assert.assertNotNull(rocket);
		final IceSLComponentVisitor iceslVisitor = new IceSLComponentVisitor();
		final Writer output = iceslVisitor.convert(rocket); 
		saveTo(output, "/tmp/v100.lua");
		Assert.assertEquals("", output.toString());
	}

	private void saveTo(final Writer output, final String filename) throws IOException {
		final FileWriter fw = new FileWriter(new File(filename));
		fw.write(output.toString());
		fw.close();
	}
	
	@org.junit.Test
	public void testConvertRocket_v100_withFinTabs() throws Exception {
		final Rocket rocket = TestRockets.makeTestRocket_v101_withFinTabs().getRocket();
		Assert.assertNotNull(rocket);
		final IceSLComponentVisitor iceslVisitor = new IceSLComponentVisitor();
		final Writer output = iceslVisitor.convert(rocket);
		saveTo(output, "/tmp/v100-with-fin-tabs.lua");
		Assert.assertEquals("", output.toString());
	}
	
	@org.junit.Test
	public void testConvertRocket_v100_withTubeCouplerChild() throws Exception {
		final Rocket rocket = TestRockets.makeTestRocket_v101_withTubeCouplerChild().getRocket();
		Assert.assertNotNull(rocket);
		final IceSLComponentVisitor iceslVisitor = new IceSLComponentVisitor();
		final Writer output = iceslVisitor.convert(rocket);
		saveTo(output, "/tmp/v100-with-tube-coupler-child.lua");
		Assert.assertEquals("", output.toString());
	}
	
	@org.junit.Test
	public void testConvertRocket_v100_withBoosters() throws Exception {
		final Rocket rocket = TestRockets.makeTestRocket_v108_withBoosters().getRocket();
		Assert.assertNotNull(rocket);
		final IceSLComponentVisitor iceslVisitor = new IceSLComponentVisitor();
		final Writer output = iceslVisitor.convert(rocket);
		saveTo(output, "/tmp/v100-with-boosters.lua");
		Assert.assertEquals("", output.toString());
	}
	
//	@org.junit.Test
//	public void testConvertSimpleModel() throws Exception {
//		GeneralRocketLoader loader = new GeneralRocketLoader(new File("A simple model rocket.ork"));
//		final InputStream stream = this.getClass().getResourceAsStream("A simple model rocket.ork");
//		Assert.assertNotNull("Could not open test file A simple model rocket.ork", stream);
//		final Rocket rocket;
//		try {
//			OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
//			loader.load(new BufferedInputStream(stream));
//			rocket = doc.getRocket();
//			Assert.assertNotNull(rocket);
//		} catch (IllegalStateException ise) {
//			Assert.fail(ise.getMessage());
//			return;
//		}
//		//Assert.assertTrue(loader.getWarnings().size() == 2);
//		System.out.println(loader.getWarnings());
//	}
}
