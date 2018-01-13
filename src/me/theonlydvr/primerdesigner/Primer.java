package me.theonlydvr.primerdesigner;

/** A class  representing a primer containing
 * both the sequence and the index in the phage 
 * genome where it is found
 */

public class Primer {
	@Override
	public String toString() {
		return "Primer [sequence=" + sequence + ", start=" + start + "]";
	}
	
	private String sequence;
	private int start;
	
	public Primer(String sequence, int start) {
		this.sequence = sequence;
		this.start = start;
	}
	
	public String getSequence() {
		return sequence;
	}
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}
	public int getStart() {
		return start;
	}
	public void setStart(int start) {
		this.start = start;
	}
}
