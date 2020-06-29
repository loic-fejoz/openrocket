package net.sf.openrocket.file.stl;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.sf.openrocket.rocketcomponent.NoseCone;
import net.sf.openrocket.rocketcomponent.Transition.Shape;

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
	
	public void writeSquare(double[] p1a, double[] p1b, double[] p2b, double[] p2a) throws IOException {
		writeSquare(zero, p1a, p1b, p2b, p2a);
	}
	
	/**
	 * Export a Nose Cone alone in its file.
	 * @param nose
	 * @param n
	 * @throws IOException
	 */
	public void exportNoseCone(final NoseCone nose, final int n) throws IOException {
		writeHeader();
		configureForUnknownTriangleCount();
		configureRotationFor3DPrint();
		writeNoseCone(
				nose,
				n);
		flush();
		close();
	}
	
	/**
	 * Write triangles for Nose Cone.
	 * @param nose
	 * @param n
	 * @throws IOException
	 */
	public void writeNoseCone(final NoseCone nose, final int n)
			throws IOException {
		
		final double shape_param = nose.getShapeParameter();
		final Shape ogive_shape = nose.getType();
		final double one_meter_in_millimeter = 1000.0;
		final double radius = nose.getAftRadius() * one_meter_in_millimeter;
		final double length = nose.getLength() * one_meter_in_millimeter;
		final double thickness = nose.getThickness() * one_meter_in_millimeter;
		
		final double delta_x = length / 100.0;
		double[][] outerEndPoints = writeProfile(shape_param, radius, length, 0, ogive_shape, n, delta_x, false);
		double[][] innerEndPoints = writeProfile(shape_param, radius, length - thickness, thickness, ogive_shape, n, delta_x, true);
		assert (outerEndPoints.length == innerEndPoints.length) : "Expect same length: " + Integer.toString(outerEndPoints.length) + " vs " + Integer.toString(innerEndPoints.length);
		// Close end
		if (nose.getAftShoulderLength() > 0.0) {
			
			final double externalRadius = nose.getAftShoulderRadius() * one_meter_in_millimeter;
			final double internalRadius = externalRadius - nose.getAftShoulderThickness() * one_meter_in_millimeter;
			// Shoulder's aft	
			final double x = innerEndPoints[0][0];
			double[] p1_shoulder_ext = { x, (double) externalRadius, 0f };
			double[] p1_shoulder_int = { x, (double) internalRadius, 0f };
			final RotationIterator itExt = new RotationIterator(externalRadius, n).setX(x);
			final RotationIterator itInt = new RotationIterator(internalRadius, n).setX(x);
			for (int i = 0; i < n + 1; i++) {
				double[] p2_shoulder_ext = itExt.next();
				double[] p2_shoulder_int = itInt.next();
				double[] p1 = outerEndPoints[i];
				double[] p1_int = innerEndPoints[i];
				double[] p2 = outerEndPoints[(i + 1) % outerEndPoints.length];
				double[] p2_int = innerEndPoints[(i + 1) % innerEndPoints.length];
				writeSquare(p1, p1_shoulder_ext, p2_shoulder_ext, p2);
				writeSquare(p1_shoulder_int, p1_int, p2_int, p2_shoulder_int);
				p1_shoulder_ext = p2_shoulder_ext;
				p1_shoulder_int = p2_shoulder_int;
			}
			// Shoulder's side
			outerEndPoints = generateProfilePart(-1.0, externalRadius, length + nose.getAftShoulderLength() * one_meter_in_millimeter, 0, null, n, delta_x, false, x);
			innerEndPoints = generateProfilePart(-1.0, internalRadius, length + nose.getAftShoulderLength() * one_meter_in_millimeter, 0, null, n, delta_x, true, x);
		}
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
		generateTipPart(shape_param, radius, length, thickness, ogive_shape, n, inout, x);
		return generateProfilePart(shape_param, radius, length, thickness, ogive_shape, n, delta_x, inout, x);
	}
	
	private void generateTipPart(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n, final boolean inout, double x)
			throws IOException {
		final double[] p0 = { (float) thickness, 0f, 0f };
		// Generate the tip endpoints
		double y = Math.max(0, ogive_shape.getRadius(Math.min(x, length), radius, length, shape_param) - thickness);
		double[] p1 = { (double) x, (double) y, 0f };
		final RotationIterator it = new RotationIterator(y, n).setX(x + thickness);
		for (double[] p2 : it) {
			if (inout) {
				writeTriangle(p0, p2, p1);
			} else {
				writeTriangle(p0, p1, p2);
			}
			p1 = p2;
		}
	}
	
	private double[][] generateProfilePart(final double shape_param, final double radius, final double length, final double thickness, final Shape ogive_shape, final int n, final double delta_x,
			final boolean inout, double x) throws IOException {
		/* Track the endPoints of the profile so as to be able to close the shape accurately */
		final double[][] endPoints = new double[n + 1][3];
		// Generate layer by layer
		for (; x < length - delta_x; x += delta_x) {
			final double y1 = Math.max(0, (ogive_shape == null) ? radius : ogive_shape.getRadius(x, radius, length, shape_param) - thickness);
			final double y2 = Math.max(0, (ogive_shape == null) ? radius : ogive_shape.getRadius(x + delta_x, radius, length, shape_param) - thickness);
			double p1a[] = new double[] { x, y1, 0f };
			double x1b = Math.min(x + delta_x, length);
			double p1b[] = { x1b, y2, 0f };
			final RotationIterator itP2A = new RotationIterator(y1, n).setX(x + thickness);
			final RotationIterator itP2B = new RotationIterator(y2, n).setX(x + delta_x + thickness);
			for (int i = 0; i < n + 1; i++) {
				double[] p2a = itP2A.next();
				double[] p2b = itP2B.next();
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