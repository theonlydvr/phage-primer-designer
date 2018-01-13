package me.theonlydvr.primerdesigner;

/** A class  representing a pair of primers containing
 * the sequences for the forward and backward primers
 * and the amplicon length
 */

public class ForRevPair {
	@Override
	public String toString() {
		return "ForRevPair [forward=" + forward + ", reverse=" + reverse + ", length=" + length + "]";
	}

	private String forward;
	private String reverse;
	private int length;
	
	public ForRevPair(String forward, String reverse, int length) {
		this.forward = forward;
		this.reverse = reverse;
		this.length = length;
	}

	public String getForward() {
		return forward;
	}

	public void setForward(String forward) {
		this.forward = forward;
	}

	public String getReverse() {
		return reverse;
	}

	public void setReverse(String reverse) {
		this.reverse = reverse;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}
}
