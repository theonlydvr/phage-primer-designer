package me.theonlydvr.primerdesigner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import org.apache.http.client.ClientProtocolException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Main class for executing primer finder program
 *  Uses the BLAST database at phages.db 
 *  Specified for mycobacteriophage
 */

public class Main {

	static final int MIN_PRIMER_LENGTH = 18; //Minimum length of primer
	static final int MAX_PRIMER_LENGTH = 22; //Maximum length of primer
	static final double MAX_FALSE_FRACTION = 5/6; //Max overlap fraction between an out of cluster phage and the primer sequence
	static final double MIN_GC_FRACTION = 0.4; //Minimum GC fraction of primer sequence
	static final double MAX_GC_FRACTION = 0.6; //Maximum GC fraction of primer sequence
	static final double MAX_GC_END = 3; //Maximum amount of GC in last 5 bases of 3' end
	static final double MAX_RUN = 4; //Maximum stretch of repeating bases
	static final double MAX_REPEATS = 4; //Maximum stretch of repeating pairs of bases
	static final int MIN_AMPLICON_LENGTH = 200; //Minimum length of sequence between primers
	static final int MAX_AMPLICON_LENGTH = 1000;  //Maximum length of sequence between primers
	
	static final String INITIAL_PHAGE = ""; //Phage for initial BLAST comparison
	static final String[] IN_BLACK_LIST = {}; //Phages to ignore when finding possible primers in a cluster
	static final String[] OUT_BLACK_LIST = {}; //Phages to ignore when searching for false positives
	static final int MAX_MISSED = 0; //The max number of phages in a cluster that a primer will miss, -1 means no limit
	
	static final String CLUSTER = "K6"; //Cluster for which to find primers
	
	public static void main(String[] args) {
		try {
			//Get all sequenced phage in cluster
			JsonArray clusterData;
			if (CLUSTER.chars().allMatch(Character::isLetter))
				clusterData = PhagesDB.getCluster(CLUSTER).get("phage_set").getAsJsonArray();
			else clusterData = PhagesDB.getSubCluster(CLUSTER).get("phage_set").getAsJsonArray();
			Iterator<JsonElement> iter = clusterData.iterator();
			ArrayList<String> phageInCluster = new ArrayList<>();
			while (iter.hasNext()) {
				phageInCluster.add(iter.next().getAsString());
			}
			List<String> sequenced = new ArrayList<>();
			for (String phage : phageInCluster) {
				JsonObject data = PhagesDB.getPhage(phage);
				if (data.get("seq_finished").getAsBoolean()) 
					sequenced.add(phage);
			}
			
			//Blast FASTA file from phage in cluster
			sequenced.removeIf(name -> Arrays.asList(IN_BLACK_LIST).contains(name));
			String toBLAST = INITIAL_PHAGE.length() == 0 ? sequenced.get(0) : INITIAL_PHAGE;
			String alignments = PhagesDB.locallyBLAST(PhagesDB.downloadPhageFASTA(toBLAST));
			System.out.println(toBLAST);
			sequenced.remove(toBLAST);
			
			//Find all sequence matches >= 18 between the BLASTed phage and another in the cluster
			Scanner sequenceFinder = new Scanner(alignments);
			sequenceFinder.useDelimiter("\n");
			List<Primer> possibilities = new ArrayList<>();
			String line = sequenceFinder.next();
			while (possibilities.size() == 0 && sequenceFinder.hasNext()) {
				if (line.startsWith(">")) {
					String name = null;
					for (String phage : sequenced) {
						if (line.contains(phage)) {
							name = phage;
							break;
						}
					}
					if (name != null) {
						sequenced.remove(name);
						String sequence = "";
						int beginning = 0;
						do {
							if (line.contains("Score")) {
								if (sequence.length() >= MIN_PRIMER_LENGTH) 
									possibilities.add(new Primer(sequence, beginning));
								sequence = "";
							}
							else if (line.startsWith("Query")) {
								String matches = sequenceFinder.next();
								int start = line.indexOf(" ", line.indexOf(" ") + 1);
								beginning = Integer.parseInt(line.substring(line.indexOf(" ") + 1, start));
								for (int i = start + 1; i < matches.length(); i++) {
									if (matches.charAt(i) == '|')
										sequence += line.charAt(i);
									else {
										if (sequence.length() >= MIN_PRIMER_LENGTH)
											possibilities.add(new Primer(sequence, beginning));
										sequence = "";
									}
								}
							}
							if (sequenceFinder.hasNext()) line = sequenceFinder.next();
						} while(!line.startsWith(">") && sequenceFinder.hasNext());
						if (sequence.length() >= MIN_PRIMER_LENGTH) 
							possibilities.add(new Primer(sequence, beginning));
					}
					else line = sequenceFinder.next();
				}
				else line = sequenceFinder.next();
			}
			sequenceFinder.close();
			
			System.out.println(sequenced);
			System.out.println(possibilities.size());
			System.out.println(possibilities);
			System.out.println();
			
			Hashtable<Integer, ArrayList<Primer>> completePrimers = new Hashtable<>();
			
			//For each primer, if they exist, return every substring >= 18 present in every member of the cluster
			for (Primer p : possibilities) {
				StringBuilder primerBuilder = new StringBuilder(p.getSequence());
				String primerResults = PhagesDB.locallyBLAST(p.getSequence());
				Scanner primerAnalyzer = new Scanner(primerResults);
				primerAnalyzer.useDelimiter("\n");
				ArrayList<String> sequencedCopy = new ArrayList<>(sequenced);
				while(sequencedCopy.size() > 0 && primerAnalyzer.hasNext()) {
					if (line.startsWith(">")) {
						String name = null;
						for (String phage : sequencedCopy) {
							if (line.contains(phage)) {
								name = phage;
								break;
							}
						}
						if (name != null) {
							sequencedCopy.remove(name);
							int queryEnd = 0;
							boolean firstQuery = false;
							boolean started = false;
							do {
								if (line.contains("Score")) {
									if (started) break;
									else started = true;
								}
								else if (line.startsWith("Query")) {
									int queryStart = Integer.parseInt(line.substring(line.indexOf(" ") + 1, line.indexOf(" ", line.indexOf(" ") + 1)));
									if (!firstQuery) {
										firstQuery = true;
										if (queryStart > 1) {
											for (int i = 0; i < queryStart; i++)
												primerBuilder.setCharAt(i, '_');
										}
									}
									queryEnd = Integer.parseInt(line.substring(line.lastIndexOf(" ") + 1));
									String matches = primerAnalyzer.next();
									int start = line.indexOf(" ", line.indexOf(" ") + 1);
									while (line.charAt(start) == ' ')
										start++;
									for (int i = start; i < matches.length(); i++) {
										if (matches.charAt(i) != '|')
											primerBuilder.setCharAt((i - start) + queryStart - 1, '_');
									}
								}
								if (primerAnalyzer.hasNext()) line = primerAnalyzer.next();
							} while(!line.startsWith(">") && primerAnalyzer.hasNext());
							if (queryEnd < primerBuilder.length()) {
								for (int i = queryEnd; i < primerBuilder.length(); i++)
									primerBuilder.setCharAt(i, '_');
							}
						}
						else line = primerAnalyzer.next();
					}
					else line = primerAnalyzer.next();
				}
				primerAnalyzer.close();
				String sequence = "";
				if (sequencedCopy.size() <= MAX_MISSED || MAX_MISSED == -1) {
					for (int i = 0; i < primerBuilder.length(); i++) {
						if (primerBuilder.charAt(i) == '_') {
							if (sequence.length() > MIN_PRIMER_LENGTH) {
								System.out.println(sequence);
								if (completePrimers.containsKey(sequencedCopy.size()))
									completePrimers.get(sequencedCopy.size()).add(new Primer(sequence, p.getStart() + i - sequence.length()));
								else {
									ArrayList<Primer> newPrimers = new ArrayList<>();
									newPrimers.add(new Primer(sequence, p.getStart() + i - sequence.length()));
									completePrimers.put(sequencedCopy.size(), newPrimers);
								}
							}
							sequence = "";
						}
						else sequence += primerBuilder.charAt(i);
					}
					if (sequence.length() > MIN_PRIMER_LENGTH) {
						System.out.println(sequence);
						if (completePrimers.containsKey(sequencedCopy.size()))
							completePrimers.get(sequencedCopy.size()).add(new Primer(sequence, p.getStart() + primerBuilder.length() - sequence.length()));
						else {
							ArrayList<Primer> newPrimers = new ArrayList<>();
							newPrimers.add(new Primer(sequence, p.getStart() + primerBuilder.length() - sequence.length()));
							completePrimers.put(sequencedCopy.size(), newPrimers);
						}
					}
				}
			}
			System.out.println(completePrimers);
			
			//For each primer, verify there are no false positives
			for (Integer count : completePrimers.keySet()) {
				completePrimers.get(count).removeIf(primer -> {
					try {
						return !safePrimer(primer.getSequence());
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				});
			}
			System.out.println(completePrimers);
			
			Hashtable<Integer, ArrayList<Primer>> checkedPrimers = new Hashtable<>();
			
			//Eliminate primers with bad sequences
			for (Integer count : completePrimers.keySet()) {
				for (Primer primer : completePrimers.get(count)) {
					String sequence = primer.getSequence();
					boolean goodGC = goodGC(sequence);
					boolean goodEnd = goodEnd(sequence);
					boolean goodRuns = goodRuns(sequence);
					boolean goodRepeats = goodRepeats(sequence);
					if (goodGC && goodEnd && goodRuns && goodRepeats) {
						if (checkedPrimers.containsKey(count))
							checkedPrimers.get(count).add(primer);
						else {
							ArrayList<Primer> newPrimers = new ArrayList<>();
							newPrimers.add(primer);
							checkedPrimers.put(count, newPrimers);
						}
					}
					else {
						int start = primer.getStart();
						System.out.println("Failed: " + sequence);
						while ((!goodGC || !goodEnd) && sequence.length() > 18) {
							if ((sequence.endsWith("g") || sequence.endsWith("c")) && (!goodEnd || (goodEnd && goodEnd(sequence.substring(0, sequence.length() - 1))))) 
								sequence = sequence.substring(0, sequence.length() - 1);
							else if (sequence.startsWith("g") || sequence.startsWith("c")) {
								sequence = sequence.substring(1, sequence.length());
								start++;
							}
							else break;
							goodGC = goodGC(sequence);
							goodEnd = goodEnd(sequence);
						}
						goodRuns = goodRuns(sequence);
						goodRepeats = goodRepeats(sequence);
						if (goodGC && goodEnd && goodRuns && goodRepeats && safePrimer(sequence)) {
							System.out.println("Saved: " + sequence);
							if (checkedPrimers.containsKey(count))
								checkedPrimers.get(count).add(new Primer(sequence, start));
							else {
								ArrayList<Primer> newPrimers = new ArrayList<>();
								newPrimers.add(new Primer(sequence, start));
								checkedPrimers.put(count, newPrimers);
							}
						}
					}
				}
			}
			
			System.out.println(checkedPrimers);
			
			//Find primer pairings which would result in a good amplicon length
			ArrayList<ForRevPair> pairs = new ArrayList<>();
			Integer[] keys = checkedPrimers.keySet().toArray(new Integer[0]);
			Arrays.sort(keys);
			
			for (int i = 0; i < keys.length; i++) {
				for (int j = 0; j <= i; j++) {
					for (int k = 0; k < checkedPrimers.get(keys[i]).size(); k++) {
						for (int l = k + 1; l < checkedPrimers.get(keys[j]).size(); l++) {
							if (checkedPrimers.get(keys[i]).get(k).getStart() < checkedPrimers.get(keys[j]).get(l).getStart()) {
								int distance = checkedPrimers.get(keys[j]).get(l).getStart() - (checkedPrimers.get(keys[i]).get(k).getStart() + checkedPrimers.get(keys[i]).get(k).getSequence().length());
								if (distance >= MIN_AMPLICON_LENGTH && distance <= MAX_AMPLICON_LENGTH) {
									System.out.println(keys[j] + " " + keys[i]);
									pairs.add(new ForRevPair(checkedPrimers.get(keys[i]).get(k).getSequence(), checkedPrimers.get(keys[j]).get(l).getSequence(), distance));
								}
							}
							else {
								int distance = checkedPrimers.get(keys[i]).get(k).getStart() - (checkedPrimers.get(keys[j]).get(l).getStart() + checkedPrimers.get(keys[j]).get(l).getSequence().length());
								if (distance >= MIN_AMPLICON_LENGTH && distance <= MAX_AMPLICON_LENGTH) {
									System.out.println(keys[j] + " " + keys[j]);
									pairs.add(new ForRevPair(checkedPrimers.get(keys[i]).get(k).getSequence(), checkedPrimers.get(keys[j]).get(l).getSequence(), distance));
								}
							}
						}
					}
				}
			}
			if (pairs.size() > 5)
				System.out.println((new ArrayList<ForRevPair>(pairs)).subList(0, 5));
			else System.out.println(pairs);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	   * Verifies that the provided sequence has a GC content in the range
	   * 
	   * @param sequence
	   *          A nucleotide sequence represented as a string
	   * @return true if the GC content is in the range
	*/
	public static boolean goodGC(String sequence) {
		double gcCount = 0;
		for (int i = 0; i < sequence.length(); i++)
			if (sequence.charAt(i) == 'g' || sequence.charAt(i) == 'c') gcCount++;
		return (gcCount / sequence.length()) >= MIN_GC_FRACTION && (gcCount / sequence.length()) <= MAX_GC_FRACTION;
	}
	
	/**
	   * Verifies that the end of the provided sequence has a good amount of GC
	   * 
	   * @param sequence
	   *          A nucleotide sequence represented as a string
	   * @return true if the end GC content is less than the specified value
	*/
	public static boolean goodEnd(String sequence) {
		int endGCCount = 0;
		for (int i = sequence.length() - 5; i < sequence.length(); i++)
			if (sequence.charAt(i) == 'g' || sequence.charAt(i) == 'c') endGCCount++;
		return endGCCount <= MAX_GC_END;
	}
	
	/**
	   * Verifies that there are no sequences of repeating bases of too great a length
	   * 
	   * @param sequence
	   *          A nucleotide sequence represented as a string
	   * @return true if there are no long sequences of repeating bases
	*/
	public static boolean goodRuns(String sequence) {
		String run = sequence.charAt(0) + "";
		int longestRun = 0;
		for (int i = 1; i < sequence.length(); i++) {
			if (run.contains(sequence.charAt(i) + "")) run += sequence.charAt(i);
			else {
				if (longestRun < run.length()) longestRun = run.length();
				run = sequence.charAt(i) + "";
			}
		}
		return longestRun <= MAX_RUN;
	}
	
	/**
	   * Verifies that there are no sequences of repeating pairs of bases of too great a length
	   * 
	   * @param sequence
	   *          A nucleotide sequence represented as a string
	   * @return true if there are no long sequences of repeating pairs of bases
	*/
	public static boolean goodRepeats(String sequence) {
		String repeat = sequence.substring(0, 2);
		int longestRepeat = 0;
		for (int i = 2; i + 1 < sequence.length(); i+=2) {
			if (repeat.startsWith(sequence.substring(i, i + 2))) repeat += sequence.substring(i, i + 2);
			else {
				if (repeat.length() / 2 > longestRepeat) longestRepeat = repeat.length() / 2;
				repeat = sequence.substring(i, i + 2);
			}
		}
		repeat = sequence.substring(1, 3);
		for (int i = 1; i + 1 < sequence.length(); i+=2) {
			if (repeat.startsWith(sequence.substring(i, i + 2))) repeat += sequence.substring(i, i + 2);
			else {
				if (repeat.length() / 2 > longestRepeat) longestRepeat = repeat.length() / 2;
				repeat = sequence.substring(i, i + 2);
			}
		}
		return longestRepeat <= MAX_REPEATS;
	}
	
	/**
	   * Verifies that there are no potential false positives for the primer by comparison to out of cluster phage
	   * 
	   * @param primer
	   *          A nucleotide sequence represented as a string
	   * @return true if there are theoretically no potential false positives
	*/
	public static boolean safePrimer(String primer) throws ClientProtocolException, IOException {
		String primerMatch = PhagesDB.locallyBLAST(primer);
		Scanner primerFinder = new Scanner(primerMatch);
		primerFinder.useDelimiter("\n");
		do {
			String nextLine = primerFinder.next();
			if (nextLine.startsWith(">") && nextLine.contains("Mycobacterium")) {
				boolean blackListed = false;
				for (String phage : OUT_BLACK_LIST)
					blackListed = blackListed || nextLine.contains(phage);
				nextLine = primerFinder.next();
				if (nextLine.indexOf("Cluster") > 0 && !blackListed) {
					String cluster;
					if (nextLine.endsWith("Cluster")) 
						cluster = primerFinder.next().replace(" ", "");
					else 
						cluster = nextLine.substring(nextLine.indexOf("Cluster") + 8);
					if (!cluster.equals(CLUSTER) && ((CLUSTER.startsWith(cluster) && !cluster.matches(".*\\d+.*")) || (cluster.startsWith(CLUSTER) && !CLUSTER.matches(".*\\d+.*"))))
						cluster = CLUSTER;
					cluster= cluster.replaceAll("\\s+","");
					String matchString = primerFinder.next();
					while (!matchString.contains("Identities"))
						matchString = primerFinder.next();
					int matchLength = Integer.parseInt(matchString.substring(matchString.indexOf("=") + 2, matchString.indexOf("/")));
					if ((double) (matchLength / primer.length()) > MAX_FALSE_FRACTION && !CLUSTER.equals(cluster)) {
						primerFinder.close();
						return false;
					}
				}
			}
		} while(primerFinder.hasNext());
		primerFinder.close();
		return true;
	}
}
