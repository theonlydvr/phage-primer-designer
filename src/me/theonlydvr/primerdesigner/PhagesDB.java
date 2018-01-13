package me.theonlydvr.primerdesigner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** A class containing all the functions for 
 * interacting with the phage metadata and BLAST 
 * search on phages.db
 */

public class PhagesDB {
	
	public static final String GENUS1 = "Mycobacterium phage ";
	public static final String GENUS2 = "Mycobacteriophage ";
	
	/**
	   * Fetches the FASTA file for a named phage virus
	   * 
	   * @param name
	   *          The name of the phage 
	   * @return a string representation for the phage's FASTA file
	*/
	public static String downloadPhageFASTA(String name) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpResponse response = httpclient.execute(new HttpGet(getPhage(name).get("fasta_file").getAsString()));
		String fileText = EntityUtils.toString(response.getEntity());
		httpclient.close();
		return fileText;
	}
	
	/**
	   * BLASTs a nucleotide sequence represented as a FASTA file or string
	   * 
	   * @param fasta
	   *          The sequence representation
	   * @return a string containing the BLAST results
	*/
	public static String locallyBLAST(String fasta) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpPost blast = new HttpPost("http://phagesdb.org/blast/results/blast.cgi");
		ArrayList<NameValuePair> blastParameters = new ArrayList<>();
		blastParameters.add(new BasicNameValuePair("PROGRAM", "blastn"));
		blastParameters.add(new BasicNameValuePair("DATALIB", "Acti_" + "12082017"));
		blastParameters.add(new BasicNameValuePair("SEQUENCE", fasta));
		blastParameters.add(new BasicNameValuePair("ALIGNMENTS", "200"));
		blast.setEntity(new UrlEncodedFormEntity(blastParameters, "UTF-8"));
		HttpResponse response = httpclient.execute(blast);
		String fileText = EntityUtils.toString(response.getEntity());
		httpclient.close();
		return fileText;
	}

	/**
	   * Fetches the data for a named phage virus
	   * 
	   * @param name
	   *          The name of the phage
	   * @return a JsonObject representing the phage's data
	*/
	public static JsonObject getPhage(String name) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpResponse response = httpclient.execute(new HttpGet("http://phagesdb.org/api/phages/" + name));
		JsonObject result = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		httpclient.close();
		return result;
	}
	
	/**
	   * Fetches the data for every phage in a particular subcluster
	   * 
	   * @param subcluster
	   *          The name of the subcluster
	   * @return a JsonObject containing the data for every phage
	*/
	public static JsonObject getSubCluster(String subcluster) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpResponse response = httpclient.execute(new HttpGet("http://phagesdb.org/api/subclusters/" + subcluster));
		JsonObject result = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		httpclient.close();
		return result;
	}
	
	/**
	   * Fetches the data for every phage in a particular cluster
	   * 
	   * @param cluster
	   *          The name of the cluster
	   * @return a JsonObject containing the data for every phage
	*/
	public static JsonObject getCluster(String cluster) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpResponse response = httpclient.execute(new HttpGet("http://phagesdb.org/api/clusters/" + cluster));
		JsonObject result = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		httpclient.close();
		return result;
	}
	
	/**
	   * Fetches the list of phage in a particular subcluster
	   * 
	   * @param subcluster
	   *          The name of the subcluster
	   * @return a JsonObject containing the list of phage
	*/
	public static JsonArray getSubClusterPhageList(String subcluster) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String next = "http://phagesdb.org/api/subclusters/" + subcluster + "/phagelist/";
		JsonArray phage = new JsonArray();
		do {
			HttpResponse response = httpclient.execute(new HttpGet(next));
			JsonObject page = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
			JsonArray results = page.getAsJsonArray("results");
			Iterator<JsonElement> i = results.iterator();
			while (i.hasNext())
				phage.add(i.next());
			next = page.get("next").isJsonNull() ? null : page.get("next").getAsString();
		} while (next != null);
		httpclient.close();
		return phage;
	}
	
	/**
	   * Fetches the list of phage in a particular subcluster
	   * 
	   * @param subcluster
	   *          The name of the subcluster
	   * @param searchProperties
	   *          The properties for which the list of phage will be filtered
	   * @return a JsonObject containing the list of phage
	*/
	public static JsonArray getSubClusterPhageList(String subcluster, PhagesDBSearchProperties searchProperties) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String next = "http://phagesdb.org/api/subclusters/" + subcluster + "/phagelist/";
		JsonArray phages = new JsonArray();
		do {
			HttpResponse response = httpclient.execute(new HttpGet(next));
			JsonObject page = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
			JsonArray results = page.getAsJsonArray("results");
			Iterator<JsonElement> i = results.iterator();
			while (i.hasNext()) {
				JsonObject phage = i.next().getAsJsonObject();
				if (searchProperties.satisfies(phage))
					phages.add(searchProperties.filter(phage));
			}
			next = page.get("next").isJsonNull() ? null : page.get("next").getAsString();
		} while (next != null);
		httpclient.close();
		return phages;
	}
	
	/**
	   * Fetches the list of phage in a particular cluster
	   * 
	   * @param cluster
	   *          The name of the cluster
	   * @return a JsonObject containing the list of phage
	*/
	public static JsonArray getClusterPhageList(String cluster) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String next = "http://phagesdb.org/api/clusters/" + cluster + "/phagelist/";
		JsonArray phage = new JsonArray();
		do {
			HttpResponse response = httpclient.execute(new HttpGet(next));
			JsonObject page = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
			JsonArray results = page.getAsJsonArray("results");
			Iterator<JsonElement> i = results.iterator();
			while (i.hasNext())
				phage.add(i.next());
			next = page.get("next").isJsonNull() ? null : page.get("next").getAsString();
		} while (next != null);
		httpclient.close();
		return phage;
	}
	
	/**
	   * Fetches the list of phage in a particular cluster
	   * 
	   * @param cluster
	   *          The name of the cluster
	   * @param searchProperties
	   *          The properties for which the list of phage will be filtered
	   * @return a JsonObject containing the list of phage
	*/
	public static JsonArray getClusterPhageList(String cluster, PhagesDBSearchProperties searchProperties) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String next = "http://phagesdb.org/api/clusters/" + cluster + "/phagelist/";
		JsonArray phages = new JsonArray();
		do {
			HttpResponse response = httpclient.execute(new HttpGet(next));
			JsonObject page = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
			JsonArray results = page.getAsJsonArray("results");
			Iterator<JsonElement> i = results.iterator();
			while (i.hasNext()) {
				JsonObject phage = i.next().getAsJsonObject();
				if (searchProperties.satisfies(phage))
					phages.add(searchProperties.filter(phage));
			}
			next = page.get("next").isJsonNull() ? null : page.get("next").getAsString();
		} while (next != null);
		httpclient.close();
		return phages;
	}
}