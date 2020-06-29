/**
 * 
 */
package net.sf.openrocket.file.stl;

import java.util.Iterator;

/** Generate circular points
 * @author Loïc Fejoz
 * 
 */
public class RotationIterator implements Iterator<double[]>, Iterable<double[]> {
	
	/**
	 * Create a rotated point at given from point (0, L, 0)
	 * @param L
	 * @param angle_rad
	 * @param x 
	 * @return
	 */
	public static double[] rotated(double L, double angle_rad, double x) {
		final double[] v = new double[] { x, (double) (L * Math.cos(angle_rad)), (double) (-L * Math.sin(angle_rad)) };
		return v;
	}
	
	private double radius;
	private int n;
	private int i;
	private double x;
	
	/**
	 * 
	 */
	public RotationIterator(final double radius, final int numberOfPoints) {
		this.radius = radius;
		n = numberOfPoints;
		i = 0;
		x = 0;
	}
	
	public RotationIterator setX(final double value) {
		x = value;
		return this;
	}
	
	@Override
	public Iterator<double[]> iterator() {
		return this;
	}
	
	@Override
	public boolean hasNext() {
		return i < n + 1;
	}
	
	@Override
	public double[] next() {
		final double angle_rad = Math.PI * 2 * i / n;
		i++;
		return rotated(radius, angle_rad, x);
	}
	
}
